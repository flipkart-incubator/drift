package com.flipkart.drift.persistence.dao;

import lombok.Getter;

@Getter
public enum ConnectionType {
    HOT("ims_hot"),COLD("ims_cold"),ARCHIVAL("ims_archive"),AUDIT("ims_audit");

    ConnectionType(String namespace) {
        this.namespace = namespace;
    }

    private final String namespace;
}

