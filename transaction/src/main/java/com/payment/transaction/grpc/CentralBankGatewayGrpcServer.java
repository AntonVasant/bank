package com.payment.transaction.grpc;

import com.centralbank.gateway.v1.Acknowledgment;
import com.centralbank.gateway.v1.CentralBankGatewayGrpc;
import com.centralbank.gateway.v1.CreditRequest;
import com.payment.transaction.service.CreditOrchestrator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

/**
 * gRPC service implementation for the Central Bank Gateway.
 * <p>
 * Receives {@code RequestCredit} RPCs from the central bank, delegates the
 * entire processing pipeline to {@link CreditOrchestrator} (which runs all
 * DB work on the {@code paymentExecutor} thread pool), and returns an
 * {@link Acknowledgment} when the future completes.
 * <p>
 * The gRPC I/O thread is <b>never blocked</b> — control is returned immediately
 * after submitting work to the executor.
 */
@Service
public class CentralBankGatewayGrpcServer
        extends CentralBankGatewayGrpc.CentralBankGatewayImplBase {

    private final CreditOrchestrator creditOrchestrator;

    public CentralBankGatewayGrpcServer(CreditOrchestrator creditOrchestrator) {
        this.creditOrchestrator = creditOrchestrator;
    }

    @Override
    public void requestCredit(CreditRequest request,
                               StreamObserver<Acknowledgment> responseObserver) {
        creditOrchestrator.handleCreditRequest(request)
                .whenComplete((acknowledgment, ex) -> {
                    if (ex != null) {
                        responseObserver.onError(Status.INTERNAL
                                .withDescription("Unexpected error processing credit request: "
                                        + ex.getMessage())
                                .withCause(ex)
                                .asRuntimeException());
                    } else {
                        responseObserver.onNext(acknowledgment);
                        responseObserver.onCompleted();
                    }
                });
    }
}
