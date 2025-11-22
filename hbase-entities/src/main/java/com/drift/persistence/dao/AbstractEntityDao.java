package com.drift.persistence.dao;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.hbaseobjectmapper.AbstractHBDAO;
import com.flipkart.hbaseobjectmapper.HBColumn;
import com.flipkart.hbaseobjectmapper.HBObjectMapper;
import com.flipkart.hbaseobjectmapper.HBRecord;
import com.flipkart.hbaseobjectmapper.codec.BestSuitCodec;
import com.drift.persistence.annotations.PrimaryKey;
import com.drift.persistence.annotations.Version;
import com.drift.persistence.exception.ApiException;
import com.google.common.collect.Lists;
import com.sematext.hbase.wd.AbstractRowKeyDistributor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractEntityDao<ROWKEY extends Serializable & Comparable<ROWKEY>, ENTITY extends HBRecord<ROWKEY>, KEY extends Serializable & Comparable<KEY>>
        extends AbstractHBDAO<ROWKEY, ENTITY> {

    protected final Optional<Pair<String, String>> versionFamilyAndColumn;
    protected final Optional<Pair<String, String>> primaryKeyFamilyAndColumn;
    private final IConnectionProvider connectionProvider;
    private static final long PERF_TTL = TimeUnit.HOURS.toMillis(2);

    protected AbstractEntityDao(IConnectionProvider connectionProvider, Connection defaultConnection, ObjectMapper objectMapper) throws IOException {
        super(defaultConnection, new BestSuitCodec(objectMapper));
        this.connectionProvider = connectionProvider;
        versionFamilyAndColumn = getFamilyAndColumn(Version.class);
        primaryKeyFamilyAndColumn = getFamilyAndColumn(PrimaryKey.class);
    }

    private Optional<Pair<String, String>> getFamilyAndColumn(Class<? extends Annotation> annotation) throws IOException {
        for (Field field : super.hbRecordClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(annotation)) {
                HBColumn column = field.getAnnotation(HBColumn.class);
                return Optional.of(Pair.newPair(column.family(), column.column()));
            }
        }
        return Optional.empty();
    }

    /*
    * do not change this
    * */

    //scan in all the buckets , after skipping the offset number of records in current bucket
    @Timed(name = "fetchBulk", absolute = true)
    @ExceptionMetered
    public List<ENTITY> fetchBulk(ROWKEY startRowKey,
                                  ROWKEY endRowKey,
                                  String columnFamily,
                                  FilterList filter,
                                  int pageSize,
                                  Optional<Pair<String, String>> offset,
                                  AbstractRowKeyDistributor keyDistributor,
                                  ConnectionType connectionType) throws IOException {
        Scan scanTmp = new Scan()
                .withStartRow(hbObjectMapper.toIbw(startRowKey).get())
                .withStopRow(hbObjectMapper.toIbw(endRowKey).get())
                .addFamily(hbObjectMapper.toIbw(columnFamily).get())
                .setScanMetricsEnabled(true)
                .setLimit(pageSize)
                .setBatch(pageSize);
        Scan[] scans = keyDistributor.getDistributedScans(scanTmp);

        List<MultiRowRangeFilter.RowRange> rowRanges = Lists.newArrayList();
        int i = 0;
        if (offset.isPresent()) {
            byte[] scanStartRowKey = Bytes.toBytes(offset.get().getFirst());
            byte[] compareRowKey = Bytes.toBytes(offset.get().getSecond());
            for (; i < scans.length; i++) {
                if (Bytes.compareTo(scans[i].getStartRow(), compareRowKey) <= 0 && Bytes.compareTo(scans[i].getStopRow(), compareRowKey) > 0) {
                    rowRanges.add(new MultiRowRangeFilter.RowRange(scanStartRowKey, false, scans[i].getStopRow(), false));
                    i++;
                    break;
                }
            }
        }
        for (; i < scans.length; i++) {
            rowRanges.add(new MultiRowRangeFilter.RowRange(scans[i].getStartRow(), true, scans[i].getStopRow(), false));
        }

        filter.addFilter(Lists.newArrayList(new MultiRowRangeFilter(rowRanges), new PageFilter(pageSize)));

        Scan scan = new Scan()
                .addFamily(hbObjectMapper.toIbw(columnFamily).get())
                .setScanMetricsEnabled(true)
                .setFilter(filter);

        log.info("scan --> " + scanTmp.toJSON());
        try (Table table = getHBTable(connectionType); ResultScanner scanner = table.getScanner(scan)) {
            return Arrays.stream(scanner.next(pageSize))
                    .map(x -> hbObjectMapper.readValue(x, hbRecordClass))
                    .collect(Collectors.toList());
        }
    }

    //Use this for only static entities
    @Timed
    @ExceptionMetered
    public List<ENTITY> findAll(String columnFamily, Optional<String> rowKeyPrefix, ConnectionType connectionType) throws IOException {
        Scan scan = (new Scan())
                .addFamily(this.hbObjectMapper.toIbw(columnFamily).get())
                .setScanMetricsEnabled(true);
        if (rowKeyPrefix.isPresent() && !rowKeyPrefix.get().isEmpty()) {
            scan.setFilter(new PrefixFilter(Bytes.toBytes(rowKeyPrefix.get())));
        }
        List<ENTITY> entities = new ArrayList<>();
        log.info("scan --> " + scan.toJSON());
        try (Table table = getHBTable(connectionType); ResultScanner scanner = table.getScanner(scan)) {
            for (Result result : scanner) {
                try {
                    entities.add(hbObjectMapper.readValue(result, hbRecordClass));
                } catch(Exception e) {
                    log.error("Unable to deserialize entity " + e);
                }
            }
        }
        return entities;
    }

    @Timed
    @ExceptionMetered
    public boolean create(ENTITY entity, ConnectionType connectionType) throws IOException {
        if (Objects.isNull(entity)) {
            throw new ApiException("Entity cannot be null");
        }
        if (!primaryKeyFamilyAndColumn.isPresent()) {
            return persist(entity, connectionType) != null ? Boolean.TRUE : Boolean.FALSE;
        }
        Put put = getHBasePutRequest(entity);
        try (Table table = getHBTable(connectionType)) {
            return table.checkAndMutate(hbObjectMapper.toIbw(entity.composeRowKey()).get(), hbObjectMapper.toIbw(primaryKeyFamilyAndColumn.get().getFirst()).get())
                    .qualifier(hbObjectMapper.toIbw(primaryKeyFamilyAndColumn.get().getSecond()).get())
                    .ifNotExists()
                    .thenPut(put);
        }
    }

    /**
     * Upsert operation - creates entity if it doesn't exist, updates if it does.
     * This method always succeeds.
     * @param entity The entity to upsert
     * @param connectionType The connection type to use
     * @throws IOException if there's an error during the operation
     */
    @Timed
    @ExceptionMetered
    public boolean upsert(ENTITY entity, ConnectionType connectionType) throws IOException {
        if (Objects.isNull(entity)) {
            throw new ApiException("Entity cannot be null");
        }
        if (!primaryKeyFamilyAndColumn.isPresent()) {
            return persist(entity, connectionType) != null ? Boolean.TRUE : Boolean.FALSE;
        }
        
        Put put = getHBasePutRequest(entity);
        try (Table table = getHBTable(connectionType)) {
            table.put(put);
            return true;
        }
    }

    @Timed
    @ExceptionMetered
    public boolean update(ENTITY entity, KEY primaryKey, ConnectionType connectionType) throws IOException {
        if (Objects.isNull(entity)) {
            throw new ApiException("Entity cannot be null");
        }
        if (!primaryKeyFamilyAndColumn.isPresent()) {
            return persist(entity, connectionType) != null ? Boolean.TRUE : Boolean.FALSE;
        }
        Put put = getHBasePutRequest(entity);
        try (Table table = getHBTable(connectionType)) {
            return table.checkAndMutate(hbObjectMapper.toIbw(entity.composeRowKey()).get(), hbObjectMapper.toIbw(primaryKeyFamilyAndColumn.get().getFirst()).get())
                    .qualifier(hbObjectMapper.toIbw(primaryKeyFamilyAndColumn.get().getSecond()).get())
                    .ifEquals(hbObjectMapper.toIbw(primaryKey).get())
                    .thenPut(put);
        }
    }

    @Timed
    @ExceptionMetered
    public boolean updateWithVersion(ENTITY entity, Integer currentVersion, ConnectionType connectionType) throws IOException {
        if (Objects.isNull(entity)) {
            throw new ApiException("Entity cannot be null");
        }
        if (!versionFamilyAndColumn.isPresent()) {
            throw new ApiException("@version annotation is not present in entity");
        }
        Put put = getHBasePutRequest(entity);
        log.info("Current version version " + currentVersion);
        try (Table table = getHBTable(connectionType)) {
            boolean b = false;
            if (currentVersion != null) {
                // if version starts from non null value
                b = table.checkAndMutate(hbObjectMapper.toIbw(entity.composeRowKey()).get(), hbObjectMapper.toIbw(versionFamilyAndColumn.get().getFirst()).get())
                        .qualifier(hbObjectMapper.toIbw(versionFamilyAndColumn.get().getSecond()).get())
                        .ifEquals(hbObjectMapper.toIbw(currentVersion).get())
                        .thenPut(put);
            } else {
                ///if version has started from null
                b = table.checkAndMutate(hbObjectMapper.toIbw(entity.composeRowKey()).get(), hbObjectMapper.toIbw(versionFamilyAndColumn.get().getFirst()).get())
                        .qualifier(hbObjectMapper.toIbw(versionFamilyAndColumn.get().getSecond()).get())
                        .ifNotExists()
                        .thenPut(put);
            }
            return b;
        }
    }

    public List<ENTITY> bulkGet(List<ROWKEY> rowkeys, Optional<FilterList> filterList, ConnectionType connectionType, Optional<String> colfam) throws IOException {
        List<Get> gets = rowkeys.parallelStream()
                .map(key -> {
                    Get get = getGet(key);
                    colfam.ifPresent(cf -> get.addFamily(Bytes.toBytes(cf)));
                    filterList.ifPresent(filters -> get.setFilter(filters));
                    return get;
                }).collect(Collectors.toList());
        try (Table table = getHBTable(connectionType)) {
            Result[] results = table.get(gets);
            return Arrays
                    .stream(results)
                    .parallel()
                    .filter(res -> !res.isEmpty())
                    .map(res -> hbObjectMapper.readValue(res, hbRecordClass))
                    .collect(Collectors.toList());
        }
    }

    public ENTITY get(ROWKEY rowKey, ConnectionType connectionType) throws IOException {
        try (Table table = getHBTable(connectionType)) {
            Result result = table.get(super.getGet(rowKey));
            return hbObjectMapper.readValue(result, hbRecordClass);
        }
    }

    public ENTITY getOnGet(Get get, ConnectionType connectionType) throws IOException {
        try (Table table = getHBTable(connectionType)) {
            Result result = table.get(get);
            return hbObjectMapper.readValue(result, hbRecordClass);
        }
    }

    public long increment(ROWKEY rowKey, String fieldName, String colFam, long amount, ConnectionType connectionType) throws IOException {
        try (Table table = getHBTable(connectionType)) {
            return table.incrementColumnValue(hbObjectMapper.toIbw(rowKey).get(), hbObjectMapper.toIbw(colFam).get()
                    , hbObjectMapper.toIbw(fieldName).get(), amount);
        }
    }

    public ROWKEY persist(HBRecord<ROWKEY> record, ConnectionType connectionType) throws IOException {
        Put put = getHBasePutRequest(record);
        try (Table table = getHBTable(connectionType)) {
            table.put(put);
            return record.composeRowKey();
        }
    }

    private Put getHBasePutRequest(HBRecord<ROWKEY> record) {
        Put put = hbObjectMapper.writeValueAsPut(record);
        return put;
    }

    public List<ROWKEY> persist(List<ENTITY> records, ConnectionType connectionType) throws IOException {
        List<Put> puts = new ArrayList<>(records.size());
        List<ROWKEY> rowKeys = new ArrayList<>(records.size());
        for (HBRecord<ROWKEY> object : records) {
            puts.add(hbObjectMapper.writeValueAsPut(object));
            rowKeys.add(object.composeRowKey());
        }
        try (Table table = getHBTable(connectionType)) {
            table.put(puts);
        }
        return rowKeys;
    }


    public void delete(ROWKEY rowKey, ConnectionType connectionType) throws IOException {
        Delete delete = new Delete(hbObjectMapper.toIbw(rowKey).get());
        try (Table table = getHBTable(connectionType)) {
            table.delete(delete);
        }
    }

    public void delete(HBRecord<ROWKEY> record, ConnectionType connectionType) throws IOException {
        this.delete(record.composeRowKey(), connectionType);
    }

    public void delete(ArrayList<ROWKEY> rowKeys, ConnectionType connectionType) throws IOException {
        List<Delete> deletes = new ArrayList<>(rowKeys.size());
        for (ROWKEY keys : rowKeys) {
            deletes.add(new Delete(hbObjectMapper.toIbw(keys).get()));
        }
        try (Table table = getHBTable(connectionType)) {
            table.delete(deletes);
        }
    }

    public void delete(List<ENTITY> records, ConnectionType connectionType) throws IOException {
        List<Delete> deletes = new ArrayList<>(records.size());
        for (HBRecord<ROWKEY> record : records) {
            deletes.add(new Delete(hbObjectMapper.toIbw(record.composeRowKey()).get()));
        }
        try (Table table = getHBTable(connectionType)) {
            table.delete(deletes);
        }
    }

    public Table getHBTable(ConnectionType connectionType) throws IOException {
        return connectionProvider.getConnection(connectionType).getTable(TableName.valueOf(connectionType.getNamespace() + ":" + getTableName()));
    }

    @Override
    @Deprecated
    public ENTITY get(ROWKEY rowKey, int numVersionsToFetch) throws IOException {
        return super.get(rowKey, numVersionsToFetch);
    }

    @Override
    @Deprecated
    public ENTITY get(ROWKEY rowKey) throws IOException {
        return super.get(rowKey);
    }

    @Override
    @Deprecated
    public ENTITY getOnGet(Get get) throws IOException {
        return super.getOnGet(get);
    }

    @Override
    @Deprecated
    public List<ENTITY> getOnGets(List<Get> gets) throws IOException {
        return super.getOnGets(gets);
    }

    @Override
    @Deprecated
    public ENTITY[] get(ROWKEY[] rowKeys, int numVersionsToFetch) throws IOException {
        return super.get(rowKeys, numVersionsToFetch);
    }

    @Override
    @Deprecated
    public ENTITY[] get(ROWKEY[] rowKeys) throws IOException {
        return super.get(rowKeys);
    }

    @Override
    @Deprecated
    public List<ENTITY> get(List<ROWKEY> rowKeys, int numVersionsToFetch) throws IOException {
        return super.get(rowKeys, numVersionsToFetch);
    }

    @Override
    @Deprecated
    public List<ENTITY> get(List<ROWKEY> rowKeys) throws IOException {
        return super.get(rowKeys);
    }

    @Override
    @Deprecated
    public List<ENTITY> get(ROWKEY startRowKey, ROWKEY endRowKey, int numVersionsToFetch) throws IOException {
        return super.get(startRowKey, endRowKey, numVersionsToFetch);
    }

    @Override
    @Deprecated
    public long increment(ROWKEY rowKey, String fieldName, long amount) throws IOException {
        return super.increment(rowKey, fieldName, amount);
    }

    @Override
    @Deprecated
    public long increment(ROWKEY rowKey, String fieldName, long amount, Durability durability) throws IOException {
        return super.increment(rowKey, fieldName, amount, durability);
    }

    @Override
    @Deprecated
    public Increment getIncrement(ROWKEY rowKey) {
        return super.getIncrement(rowKey);
    }

    @Override
    @Deprecated
    public ENTITY increment(Increment increment) throws IOException {
        return super.increment(increment);
    }

    @Override
    @Deprecated
    public ENTITY append(ROWKEY rowKey, String fieldName, Object valueToAppend) throws IOException {
        return super.append(rowKey, fieldName, valueToAppend);
    }

    @Override
    @Deprecated
    public ENTITY append(ROWKEY rowKey, Map<String, Object> valuesToAppend) throws IOException {
        return super.append(rowKey, valuesToAppend);
    }

    @Override
    @Deprecated
    public Append getAppend(ROWKEY rowKey) {
        return super.getAppend(rowKey);
    }

    @Override
    @Deprecated
    public ENTITY append(Append append) throws IOException {
        return super.append(append);
    }

    @Override
    @Deprecated
    public List<ENTITY> get(ROWKEY startRowKey, ROWKEY endRowKey) throws IOException {
        return super.get(startRowKey, endRowKey);
    }

    @Override
    @Deprecated
    public ROWKEY persist(HBRecord<ROWKEY> record) throws IOException {
        return super.persist(record);
    }

    @Override
    @Deprecated
    public List<ROWKEY> persist(List<ENTITY> records) throws IOException {
        return super.persist(records);
    }

    @Override
    @Deprecated
    public void delete(ROWKEY rowKey) throws IOException {
        super.delete(rowKey);
    }

    @Override
    @Deprecated
    public void delete(HBRecord<ROWKEY> record) throws IOException {
        super.delete(record);
    }

    @Override
    @Deprecated
    public void delete(ROWKEY[] rowKeys) throws IOException {
        super.delete(rowKeys);
    }

    @Override
    @Deprecated
    public void delete(List<ENTITY> records) throws IOException {
        super.delete(records);
    }

    @Override
    public String getTableName() {
        return super.getTableName();
    }

    @Override
    @Deprecated
    public Map<String, Integer> getColumnFamiliesAndVersions() {
        return super.getColumnFamiliesAndVersions();
    }

    @Override
    @Deprecated
    public Set<String> getFields() {
        return super.getFields();
    }

    @Override
    @Deprecated
    public Table getHBaseTable() throws IOException {
        return super.getHBaseTable();
    }

    public HBObjectMapper getHbObjectMapper() {
        return this.hbObjectMapper;
    }

}

