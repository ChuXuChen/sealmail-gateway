package com.sealmail.edge.routing;

import com.sealmail.edge.config.EdgeConfig;

public record MailRoute(String host, int port, EdgeConfig.RouteSecurity security) {
    public MailRoute {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (security == null) {
            throw new IllegalArgumentException("security must not be null");
        }
    }
}
