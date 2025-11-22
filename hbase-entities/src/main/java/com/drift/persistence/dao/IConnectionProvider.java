package com.drift.persistence.dao;

import org.apache.hadoop.hbase.client.Connection;

public interface IConnectionProvider {
    Connection getConnection(ConnectionType connectionType);
    boolean isDegraded();
}

