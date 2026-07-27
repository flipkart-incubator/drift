package com.flipkart.drift.persistence.dao;

import com.flipkart.drift.persistence.bootstrap.ConnectionNamespace;

public enum ConnectionType {
    HOT("ims_hot"), COLD("ims_cold"), ARCHIVAL("ims_archive"), AUDIT("ims_audit");

    ConnectionType(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }

    private final String defaultNamespace;

    /**
     * Returns the effective namespace for this connection type.
     * If an application has provided overrides via ConnectionNamespace.setConfig(...),
     * those overrides are consulted. Otherwise the built-in default is returned.
     */
    public String getNamespace() {
        return ConnectionNamespace.resolve(this, defaultNamespace);
    }

    // package-visible accessor for the original built-in default
    String getDefaultNamespace() { return defaultNamespace; }
}

