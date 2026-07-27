package com.flipkart.drift.persistence.bootstrap;

import com.flipkart.drift.persistence.dao.ConnectionType;

/**
 * Holder for optional application-provided namespace overrides.
 * Applications that parse configuration (application.yml / Dropwizard config)
 * may call ConnectionNamespace.setConfig(...) at startup to provide per-connection
 * namespace overrides. If no config is provided, built-in defaults are used.
 */
public final class ConnectionNamespace {
    private static volatile ConnectionNamespaceConfig config;

    private ConnectionNamespace(){}

    public static void setConfig(ConnectionNamespaceConfig cfg){
        config = cfg;
    }

    public static void clearConfig(){
        config = null;
    }

    public static String resolve(ConnectionType connectionType, String defaultNamespace){
        ConnectionNamespaceConfig local = config;
        if (local == null) {
            return defaultNamespace;
        }
        return local.resolve(connectionType, defaultNamespace);
    }
}