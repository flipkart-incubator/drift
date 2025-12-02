package com.flipkart.drift.persistence.helper;

import com.flipkart.drift.persistence.dao.ConnectionType;

public enum QuerySource {
    HOT_ONLY(ConnectionType.HOT), ARCHIVE_ONLY(ConnectionType.ARCHIVAL);
    private final ConnectionType connectionType;
    QuerySource(ConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }
}

