package com.flipkart.drift.persistence.bootstrap;

import com.flipkart.drift.persistence.dao.ConnectionType;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * Optional override for the HBase/YAK namespace used per {@link ConnectionType}.
 * Any field left unset (or the whole config block omitted) falls back to
 * the provided default namespace, preserving existing behaviour.
 */
@Data
public class ConnectionNamespaceConfig {
    private String hot;
    private String cold;
    private String archival;
    private String audit;

    public String resolve(ConnectionType connectionType, String defaultNamespace) {
        String override = switch (connectionType) {
            case HOT -> hot;
            case COLD -> cold;
            case ARCHIVAL -> archival;
            case AUDIT -> audit;
        };
        return StringUtils.isNotBlank(override) ? override : defaultNamespace;
    }

    public boolean isEmpty() {
        return (hot == null || hot.isBlank())
                && (cold == null || cold.isBlank())
                && (archival == null || archival.isBlank())
                && (audit == null || audit.isBlank());
    }
}
