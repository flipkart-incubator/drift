package com.drift.worker.bootstrap;

import com.codahale.metrics.InstrumentedExecutorService;
import com.drift.persistence.bootstrap.CacheMaxEntriesConfig;
import com.drift.persistence.bootstrap.StaticCacheRefreshConfig;
import com.drift.persistence.dao.ConnectionType;
import com.drift.persistence.dao.IConnectionProvider;
import com.drift.worker.Utility.ABServiceInitializer;
import com.drift.worker.Utility.ConfigClientUtil;
import com.drift.worker.config.*;
import com.drift.commons.exception.RedisStoreException;
import com.drift.worker.stringResolver.MustacheStringResolver;
import com.drift.worker.stringResolver.StringResolver;
import com.drift.commons.utils.MetricsRegistry;
import com.flipkart.kloud.config.ConfigClient;
import com.flipkart.kloud.config.ConfigClientBuilder;
import com.flipkart.kloud.config.DynamicBucket;
import com.google.inject.*;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.security.UserGroupInformation;
import redis.clients.jedis.JedisPoolAbstract;
import redis.clients.jedis.JedisSentinelPool;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WorkerModule extends AbstractModule {
    private final DriftWorkerConfiguration driftWorkerConfiguration;
    private final JedisSentinelPool jedisSentinelPool;

    public WorkerModule(DriftWorkerConfiguration driftWorkerConfiguration) {
        this.driftWorkerConfiguration = driftWorkerConfiguration;
        this.jedisSentinelPool = provideJedisPool();
    }

    private JedisSentinelPool provideJedisPool() {
        try {
            final RedisConfiguration redisConfiguration = driftWorkerConfiguration.getRedisConfiguration();
            String hosts = redisConfiguration.getSentinels();
            StringTokenizer strTkn = new StringTokenizer(hosts, ",");
            List<String> hostList = new ArrayList<>();
            while (strTkn.hasMoreTokens()) hostList.add(strTkn.nextToken());
            Set<String> sentinels = new HashSet<>(hostList);
            GenericObjectPoolConfig<?> genericObjectPoolConfig = getGenericObjectPoolConfig(redisConfiguration);
            return new JedisSentinelPool(redisConfiguration.getMaster(), sentinels, genericObjectPoolConfig, redisConfiguration.getPassword());
        } catch (Exception e) {
            log.error("Failed to Connected to RedisDao Server " + e.getMessage(), e);
            throw new RedisStoreException(Response.Status.INTERNAL_SERVER_ERROR, "Unable to init redis config", e.getMessage());
        }
    }

    private static GenericObjectPoolConfig<?> getGenericObjectPoolConfig(RedisConfiguration redisConfiguration) {
        GenericObjectPoolConfig<?> genericObjectPoolConfig = new GenericObjectPoolConfig<>();
        genericObjectPoolConfig.setTimeBetweenEvictionRunsMillis(-1);
        genericObjectPoolConfig.setMaxTotal(redisConfiguration.getMaxTotal());
        genericObjectPoolConfig.setTestOnBorrow(redisConfiguration.isTestOnBorrow());
        genericObjectPoolConfig.setMaxWaitMillis(redisConfiguration.getMaxWaitMillis());
        genericObjectPoolConfig.setBlockWhenExhausted(redisConfiguration.isBlockWhenExhausted());
        genericObjectPoolConfig.setMaxIdle(redisConfiguration.getMaxIdle());
        genericObjectPoolConfig.setMinIdle(redisConfiguration.getMinIdle());
        return genericObjectPoolConfig;
    }

    public static class ConnectionProvider implements IConnectionProvider {
        private final Injector injector;

        @Inject
        public ConnectionProvider(Injector injector) {
            this.injector = injector;
        }

        @Override
        public Connection getConnection(ConnectionType connectionType) {
            return injector.getInstance(Key.get(Connection.class, Names.named(connectionType.name())));
        }

        @Override
        public boolean isDegraded() {
            return false;
        }
    }

    public static class ConnectionProviderWorker implements Provider<Connection> {
        private final ConfigClient configClient;
        private final String hbaseConfigBucket;
        private final DriftWorkerConfiguration driftWorkerConfiguration;

        @Inject
        public ConnectionProviderWorker(ConfigClient configClient, DriftWorkerConfiguration driftWorkerConfiguration) {
            this.configClient = configClient;
            this.hbaseConfigBucket = driftWorkerConfiguration.getHbaseConfigBucket();
            this.driftWorkerConfiguration = driftWorkerConfiguration;
        }

        @Override
        public Connection get() {
            String zkQuorum;
            try {
                DynamicBucket bucket = configClient.getDynamicBucket(hbaseConfigBucket);
                zkQuorum = bucket.getString("zookeeperQuoramHotCalvin");
            } catch (Exception e) {
                log.error("Failed to fetch zookeeperQuoramHotCalvin from config bucket: {}", hbaseConfigBucket, e);
                throw new RuntimeException("Failed to fetch zookeeperQuoramHotCalvin from config bucket", e);
            }

            if (StringUtils.isEmpty(zkQuorum)) {
                log.error("ZOOKEEPER_QUORAM_WORKER not found in config service property");
                throw new RuntimeException("ZOOKEEPER_QUORAM_WORKER not found in config service property");
            }
            Configuration configuration = HBaseConfiguration.create();
            configuration.set(HConstants.ZOOKEEPER_QUORUM, zkQuorum);
            applyHadoopIdentity(driftWorkerConfiguration);
            populateBasicFields(configuration);

            try {
                return ConnectionFactory.createConnection(configuration);
            } catch (Exception e) {
                log.error("Error while creating connection to HBase", e);
                throw new RuntimeException("Failed to create HBase connection", e);
            }
        }
    }

    private static void populateBasicFields(Configuration configuration) {
        configuration.set("hbase.zookeeper.property.clientPort", "2181");
        configuration.set(HConstants.ZK_SESSION_TIMEOUT, "60000");
        configuration.set("zookeeper.recovery.retry", "3");
        configuration.set(HConstants.HBASE_CLIENT_RETRIES_NUMBER, "3");
        configuration.set(HConstants.HBASE_RPC_TIMEOUT_KEY, "6000");
        configuration.set(HConstants.HBASE_CLIENT_IPC_POOL_SIZE, "20");
        configuration.set(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, "6000");
        configuration.set(HConstants.HBASE_RPC_SHORTOPERATION_TIMEOUT_KEY, "4000");
        configuration.set(HConstants.HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD, "120000");
        configuration.set(HConstants.HBASE_CLIENT_MAX_PERSERVER_TASKS, "40");
        configuration.set(HConstants.HBASE_CLIENT_MAX_PERREGION_TASKS, "4");
        configuration.set(HConstants.HBASE_CLIENT_IPC_POOL_TYPE, "RoundRobinPool");
        configuration.set("hbase.hconnection.threads.max", "128");
    }

    private static void applyHadoopIdentity(DriftWorkerConfiguration driftWorkerConfiguration) {
        try {
            String userName = driftWorkerConfiguration.getHadoopUserName();
            if (StringUtils.isNotBlank(userName)) {
                System.setProperty("HADOOP_USER_NAME", userName);
            }
            String loginUser = driftWorkerConfiguration.getHadoopLoginUser();
            if (StringUtils.isNotBlank(loginUser)) {
                UserGroupInformation.setLoginUser(UserGroupInformation.createRemoteUser(loginUser));
            }
        } catch (Exception e) {
            log.warn("Unable to apply Hadoop identity from worker configuration: {}", e.getMessage());
        }
    }

    @Override
    protected void configure() {
        bind(JedisPoolAbstract.class).toInstance(this.jedisSentinelPool);
        bind(DriftWorkerConfiguration.class).toInstance(driftWorkerConfiguration);
        bind(StringResolver.class).to(MustacheStringResolver.class);
        bind(Connection.class).annotatedWith(Names.named(ConnectionType.HOT.name())).toProvider(ConnectionProviderWorker.class).asEagerSingleton();
        bind(IConnectionProvider.class).to(ConnectionProvider.class).asEagerSingleton();
        bind(ConfigClientUtil.class).asEagerSingleton();
        if (driftWorkerConfiguration.getAbConfiguration().getIsABEnabled()) {
            bind(ABServiceInitializer.class).asEagerSingleton();
        }
    }

    @Provides
    @Singleton
    private InstrumentedExecutorService provideCacheRefreshThreadPool() {
        ExecutorServiceConfig executorServiceConfig = driftWorkerConfiguration.getCacheRefreshExecutorServiceConfig();

        ExecutorService executorService = new ThreadPoolExecutor(executorServiceConfig.getMinThreads(),// core pool size
                executorServiceConfig.getMaxThreads(),// maximum pool size
                5L,// keep-alive time
                TimeUnit.SECONDS,// keep-alive time unit
                new LinkedBlockingQueue<>(executorServiceConfig.getQueueSize()),// work queue
                new ThreadPoolExecutor.CallerRunsPolicy()// rejected execution handler
        );

        // Wrapping with InstrumentedExecutorService to add metrics
        return new InstrumentedExecutorService(executorService, MetricsRegistry.INSTANCE.getRegistry(), "ScanThreadPool");
    }

    @Provides
    @Singleton
    private StaticCacheRefreshConfig provideCacheRefreshConfig() {
        return driftWorkerConfiguration.getStaticCacheRefreshConfig();
    }

    @Provides
    @Singleton
    private CacheMaxEntriesConfig provideCacheMaxEntriesConfig() {
        return driftWorkerConfiguration.getCacheMaxEntriesConfig();
    }

    @Provides
    @Singleton
    public ConfigClient configClientProvider() {
        return new ConfigClientBuilder().build();
    }

    @Provides
    @Named("enumStoreBucket")
    public String enumStoreBucket() {
        return driftWorkerConfiguration.getEnumStoreBucket();
    }

    @Provides
    @Singleton
    public ABConfiguration abConfiguration() {
        return driftWorkerConfiguration.getAbConfiguration();
    }

}

