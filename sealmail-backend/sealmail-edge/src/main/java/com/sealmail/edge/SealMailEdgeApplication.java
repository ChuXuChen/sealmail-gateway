package com.sealmail.edge;

import com.sealmail.edge.admin.EdgeAdminServer;
import com.sealmail.edge.config.EdgeConfig;
import com.sealmail.edge.metrics.EdgeMetrics;
import com.sealmail.edge.routing.DomainRouteResolver;
import com.sealmail.edge.routing.MailRoute;
import com.sealmail.edge.smtp.RelayTarget;
import com.sealmail.edge.smtp.RelayTargetResolver;
import com.sealmail.edge.smtp.SmtpDataBuffer;
import com.sealmail.edge.smtp.SmtpEdgeServer;
import com.sealmail.edge.smtp.SmtpRelayService;
import com.sealmail.edge.tls.EdgeTlsContextFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public final class SealMailEdgeApplication {
    private static final Logger log = Logger.getLogger(SealMailEdgeApplication.class.getName());

    private SealMailEdgeApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("sealmail-edge.properties");
        EdgeConfig config = EdgeConfig.load(configPath);
        EdgeTlsContextFactory tlsContextFactory = new EdgeTlsContextFactory(config.tls());
        SmtpDataBuffer dataBuffer = new SmtpDataBuffer(config.maxMessageSizeBytes());
        EdgeMetrics metrics = new EdgeMetrics();
        SmtpRelayService relayService = new SmtpRelayService(
                config.connectTimeout(),
                config.readTimeout(),
                config.maxLineLengthBytes(),
                tlsContextFactory,
                "sealmail-kona-edge"
        );

        List<SmtpEdgeServer> servers = new ArrayList<>();
        if (config.inbound().enabled()) {
            RelayTargetResolver postfixResolver = recipients -> Optional.of(new RelayTarget(
                    config.postfix().host(),
                    config.postfix().port(),
                    RelayTarget.Security.CLEAR
            ));
            servers.add(new SmtpEdgeServer(
                    "gm-starttls-inbound",
                    config.inbound().bindAddress(),
                    config.inbound().startTlsPort(),
                    config.inbound().backlog(),
                    SmtpEdgeServer.Mode.STARTTLS,
                    config.inbound().maxConnections(),
                    config.maxRecipients(),
                    config.readTimeout(),
                    config.maxLineLengthBytes(),
                    dataBuffer,
                    tlsContextFactory,
                    postfixResolver,
                    relayService,
                    metrics,
                    true
            ));
            servers.add(new SmtpEdgeServer(
                    "gm-implicit-tls-inbound",
                    config.inbound().bindAddress(),
                    config.inbound().implicitTlsPort(),
                    config.inbound().backlog(),
                    SmtpEdgeServer.Mode.IMPLICIT_TLS,
                    config.inbound().maxConnections(),
                    config.maxRecipients(),
                    config.readTimeout(),
                    config.maxLineLengthBytes(),
                    dataBuffer,
                    tlsContextFactory,
                    postfixResolver,
                    relayService,
                    metrics,
                    true
            ));
        }

        if (config.outbound().enabled()) {
            DomainRouteResolver domainRouteResolver = new DomainRouteResolver(config.routes());
            RelayTargetResolver routeResolver = recipients -> domainRouteResolver.resolve(recipients)
                    .map(SealMailEdgeApplication::toGmRelayTarget);
            servers.add(new SmtpEdgeServer(
                    "gm-outbound-smart-host",
                    config.outbound().bindAddress(),
                    config.outbound().port(),
                    config.outbound().backlog(),
                    SmtpEdgeServer.Mode.CLEAR_INTERNAL,
                    config.outbound().maxConnections(),
                    config.maxRecipients(),
                    config.readTimeout(),
                    config.maxLineLengthBytes(),
                    dataBuffer,
                    tlsContextFactory,
                    routeResolver,
                    relayService,
                    metrics,
                    false
            ));
        }

        for (SmtpEdgeServer server : servers) {
            server.start();
        }
        EdgeAdminServer adminServer = null;
        if (config.admin().enabled()) {
            adminServer = new EdgeAdminServer(config.admin(), config, tlsContextFactory, servers, metrics);
            adminServer.start();
            log.info(() -> "gm-edge-admin listening on "
                    + config.admin().bindAddress() + ":" + config.admin().port());
        }
        EdgeAdminServer finalAdminServer = adminServer;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeAll(servers, finalAdminServer)));
        log.info("SealMail Kona Edge started with " + servers.size() + " SMTP listener(s)");
        Thread.currentThread().join();
    }

    private static RelayTarget toGmRelayTarget(MailRoute route) {
        RelayTarget.Security security = switch (route.security()) {
            case STARTTLS -> RelayTarget.Security.STARTTLS;
            case IMPLICIT_TLS -> RelayTarget.Security.IMPLICIT_TLS;
        };
        return new RelayTarget(route.host(), route.port(), security);
    }

    private static void closeAll(List<SmtpEdgeServer> servers, EdgeAdminServer adminServer) {
        if (adminServer != null) {
            adminServer.close();
        }
        for (SmtpEdgeServer server : servers) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
    }
}
