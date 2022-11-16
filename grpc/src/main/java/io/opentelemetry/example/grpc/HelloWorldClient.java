/*
 * Copyright 2015 The gRPC Authors
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HelloWorldClient {
  private static final Logger logger = Logger.getLogger(HelloWorldClient.class.getName());
  private final String serverHostname;
  private final Integer serverPort;
  private ManagedChannel channel;
  private GreeterGrpc.GreeterBlockingStub blockingStub;

  // it is important to initialize the OpenTelemetry SDK as early as possible in your application's
  // lifecycle.
  private static final OpenTelemetry openTelemetry =
      ExampleConfiguration.initOpenTelemetry("OtelExampleGrpcClient");

  /** Construct client connecting to HelloWorld server at {@code host:port}. */
  public HelloWorldClient(String host, int port) {
    this.serverHostname = host;
    this.serverPort = port;
    // Initialize the OTel tracer
  }

  public void start() {
    GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(openTelemetry);

    this.channel =
        ManagedChannelBuilder.forAddress(serverHostname, serverPort)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
            // needing certificates.
            .usePlaintext()
            // Intercept the request to tag the span context
            .intercept(grpcTelemetry.newClientInterceptor())
            .build();
    this.blockingStub = GreeterGrpc.newBlockingStub(channel);
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Say hello to server. */
  public void greet(String name) {
    logger.info("Will try to greet " + name + " ...");

    HelloRequest request = HelloRequest.newBuilder().setName(name).build();
    try {
      HelloReply response = blockingStub.sayHello(request);
      logger.info("Greeting: " + response.getMessage());
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
    }
  }

  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting.
   */
  public static void main(String[] args) throws Exception {
    // Access a service running on the local machine on port 50051
    HelloWorldClient client = new HelloWorldClient("localhost", 50051);

    client.start();
    try {
      String user = "World";
      // Use the arg as the name to greet if provided
      if (args.length > 0) {
        user = args[0];
      }
      for (int i = 0; i < 10; i++) {
        client.greet(user + " " + i);
      }
    } finally {
      client.shutdown();
    }
    // await a bit for traces to be flushed
    Thread.sleep(1000);
  }
}
