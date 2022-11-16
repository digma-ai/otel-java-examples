/*
 * Copyright 2015 The gRPC Authors
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.grpc;

import com.digma.otel.instrumentation.grpc.v1_6.DigmaTracingServerInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import java.io.IOException;
import java.util.logging.Logger;

/** Server that manages startup/shutdown of a {@code Greeter} server. */
public final class HelloWorldServer {
  private static final Logger logger = Logger.getLogger(HelloWorldServer.class.getName());

  private static final int PORT = 50051;

  // it is important to initialize the OpenTelemetry SDK as early as possible in your application's
  // lifecycle.
  private static final OpenTelemetry openTelemetry =
      ExampleConfiguration.initOpenTelemetry("OtelExampleGrpcServer");

  private Server server;

  private void start() throws IOException {
    /* The port on which the server should run */

    GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);

    server =
        ServerBuilder.forPort(PORT)
            .addService(new GreeterImpl())
            // Intercept gRPC calls
            .intercept(DigmaTracingServerInterceptor.create()) // acts second
            .intercept(grpcTelemetry.newServerInterceptor()) // acts first
            .build()
            .start();
    logger.info("Server started, listening on " + PORT);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                  System.err.println("*** shutting down gRPC server since JVM is shutting down");
                  HelloWorldServer.this.stop();
                  System.err.println("*** server shut down");
                }));
  }

  private void stop() {
    if (server != null) {
      server.shutdown();
    }
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

    // We serve a normal gRPC call
    @Override
    public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
      // Serve the request
      HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    // We serve a stream gRPC call
    @Override
    public StreamObserver<HelloRequest> sayHelloStream(
        final StreamObserver<HelloReply> responseObserver) {
      return new StreamObserver<HelloRequest>() {
        @Override
        public void onNext(HelloRequest value) {
          responseObserver.onNext(
              HelloReply.newBuilder().setMessage("Hello " + value.getName()).build());
        }

        @Override
        public void onError(Throwable t) {
          logger.info("[Error] " + t.getMessage());
          responseObserver.onError(t);
        }

        @Override
        public void onCompleted() {
          responseObserver.onCompleted();
        }
      };
    }
  }

  /** Main launches the server from the command line. */
  public static void main(String[] args) throws IOException, InterruptedException {
    final HelloWorldServer server = new HelloWorldServer();
    server.start();
    server.blockUntilShutdown();
  }
}
