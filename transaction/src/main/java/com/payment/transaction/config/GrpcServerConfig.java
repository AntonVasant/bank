package com.payment.transaction.config;

import com.payment.transaction.grpc.CentralBankGatewayGrpcServer;
import com.payment.transaction.grpc.SettlementReportGrpcServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of the embedded gRPC server that hosts
 * {@link CentralBankGatewayGrpcServer} and {@link SettlementReportGrpcServer}.
 * <p>
 * The server starts on {@link PostConstruct} and performs a graceful
 * 30-second drain on {@link PreDestroy}.  Port is configured via
 * {@code grpc.server.port} in the active profile yaml.
 */
@Component
public class GrpcServerConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final CentralBankGatewayGrpcServer centralBankGatewayGrpcServer;
    private final SettlementReportGrpcServer settlementReportGrpcServer;
    private final int port;

    private Server server;

    public GrpcServerConfig(CentralBankGatewayGrpcServer centralBankGatewayGrpcServer,
                             SettlementReportGrpcServer settlementReportGrpcServer,
                             @Value("${grpc.server.port}") int port) {
        this.centralBankGatewayGrpcServer = centralBankGatewayGrpcServer;
        this.settlementReportGrpcServer = settlementReportGrpcServer;
        this.port = port;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(centralBankGatewayGrpcServer)
                .addService(settlementReportGrpcServer)
                .build()
                .start();
        log.info("Central Bank Gateway gRPC server started on port {}", port);
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null && !server.isShutdown()) {
            log.info("Shutting down gRPC server on port {}", port);
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}
