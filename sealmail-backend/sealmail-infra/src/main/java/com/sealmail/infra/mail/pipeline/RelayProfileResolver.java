package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.DeliveryRoute;
import com.sealmail.domain.mailsecurity.DeliveryRouteResolver;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.domain.policy.DeliveryTransportProfile;
import com.sealmail.infra.config.properties.PostfixProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RelayProfileResolver {

    private final PostfixProperties postfixProperties;
    private final DeliveryRouteResolver deliveryRouteResolver;
    private String gmEdgeOutboundHost;
    private int gmEdgeOutboundPort;

    public RelayProfileResolver(PostfixProperties postfixProperties,
                                DeliveryRouteResolver deliveryRouteResolver,
                                @Value("${sealmail.gm-edge.outbound-host:${SEALMAIL_GM_EDGE_OUTBOUND_HOST:127.0.0.1}}")
                                String gmEdgeOutboundHost,
                                @Value("${sealmail.gm-edge.outbound-port:${SEALMAIL_GM_EDGE_OUTBOUND_PORT:2526}}")
                                int gmEdgeOutboundPort) {
        this.postfixProperties = postfixProperties;
        this.deliveryRouteResolver = deliveryRouteResolver;
        this.gmEdgeOutboundHost = gmEdgeOutboundHost;
        this.gmEdgeOutboundPort = gmEdgeOutboundPort;
    }

    public RelayProfile resolve(MailDirection direction, MailEnvelope envelope) {
        if (direction == MailDirection.OUTBOUND) {
            Optional<RelayProfile> deliveryRoute = outboundDeliveryRoute(envelope);
            if (deliveryRoute.isPresent()) {
                return deliveryRoute.get();
            }
        }
        if (!postfixProperties.isEnabled()) {
            return null;
        }
        int port = direction == MailDirection.INBOUND
                ? postfixProperties.getAfterFilterPort()
                : postfixProperties.getOutboundPort();
        return new RelayProfile(
                postfixProperties.getHost(),
                port,
                "",
                "",
                postfixProperties.getTimeout(),
                postfixProperties.getEnvelopeFrom());
    }

    void setGmEdgeOutbound(String host, int port) {
        this.gmEdgeOutboundHost = host;
        this.gmEdgeOutboundPort = port;
    }

    private Optional<RelayProfile> outboundDeliveryRoute(MailEnvelope envelope) {
        if (envelope == null || envelope.getRecipients().isEmpty()) {
            return Optional.empty();
        }

        return deliveryRouteResolver.resolve(envelope.getRecipients())
                .map(this::relayProfileForDeliveryRoute);
    }

    private RelayProfile relayProfileForDeliveryRoute(DeliveryRoute route) {
        if (route.transportProfile().usesGmTls()) {
            return new RelayProfile(
                    gmEdgeRelayHost(),
                    gmEdgeRelayPort(),
                    "",
                    "",
                    postfixProperties.getTimeout(),
                    postfixProperties.getEnvelopeFrom(),
                    DeliveryTransportProfile.SMTP_CLEAR);
        }
        return new RelayProfile(
                route.host(),
                route.port(),
                "",
                "",
                postfixProperties.getTimeout(),
                postfixProperties.getEnvelopeFrom(),
                route.transportProfile());
    }

    private String gmEdgeRelayHost() {
        return gmEdgeOutboundHost != null && !gmEdgeOutboundHost.isBlank()
                ? gmEdgeOutboundHost.trim()
                : "127.0.0.1";
    }

    private int gmEdgeRelayPort() {
        return gmEdgeOutboundPort > 0 ? gmEdgeOutboundPort : 2526;
    }
}
