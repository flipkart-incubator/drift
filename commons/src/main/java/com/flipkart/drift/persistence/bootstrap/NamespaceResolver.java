package com.flipkart.drift.persistence.bootstrap;

import com.flipkart.drift.persistence.dao.ConnectionType;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Per-team override for the HBase namespace bound to each {@link ConnectionType}.
 * Populated from the consuming application's Dropwizard configuration (e.g.
 * {@code namespaceOverrides.hot: mar_dift_hot}); any field left unset falls back to
 * the provided default namespace, so teams that configure nothing keep the
 * built-in ims_* namespaces unchanged.
 */
@Getter
@Setter
public class NamespaceResolver {
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
}