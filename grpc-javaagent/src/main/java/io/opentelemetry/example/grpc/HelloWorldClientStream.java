/*
 * Copyright 2015 The gRPC Authors
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HelloWorldClientStream {
  private static final Logger logger = Logger.getLogger(HelloWorldClientStream.class.getName());
  private final ManagedChannel channel;
  private final String serverHostname;
  private final Integer serverPort;
  private final GreeterGrpc.GreeterStub asyncStub;

  /** Construct client connecting to HelloWorld server at {@code host:port}. */
  public HelloWorldClientStream(String host, int port) {
    this.serverHostname = host;
    this.serverPort = port;
    this.channel =
        ManagedChannelBuilder.forAddress(host, port)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
            // needing certificates.
            .usePlaintext()
            .build();
    asyncStub = GreeterGrpc.newStub(channel);
    // Initialize the OTel tracer
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /** Say hello to server. */
  public void greet(List<String> names) {
    logger.info("Will try to greet " + Arrays.toString(names.toArray()) + " ...");

    StreamObserver<HelloRequest> requestObserver;

    // Set the context with the current span
    try {
      HelloReplyStreamObserver replyObserver = new HelloReplyStreamObserver();
      requestObserver = asyncStub.sayHelloStream(replyObserver);
      for (String name : names) {
        try {
          requestObserver.onNext(HelloRequest.newBuilder().setName(name).build());
          // Sleep for a bit before sending the next one.
          Thread.sleep(500);
        } catch (InterruptedException e) {
          logger.log(Level.WARNING, "RPC failed: {0}", e.getMessage());
          requestObserver.onError(e);
        }
      }
      requestObserver.onCompleted();
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
    } finally {
    }
  }

  private static class HelloReplyStreamObserver implements StreamObserver<HelloReply> {

    public HelloReplyStreamObserver() {
      logger.info("Greeting: ");
    }

    @Override
    public void onNext(HelloReply value) {
      Span span = Span.current();
      span.addEvent("Data received: " + value.getMessage());
      logger.info(value.getMessage());
    }

    @Override
    public void onError(Throwable t) {
      Span span = Span.current();
      logger.log(Level.WARNING, "RPC failed: {0}", t.getMessage());
      span.setStatus(StatusCode.ERROR, "gRPC status: " + t.getMessage());
    }

    @Override
    public void onCompleted() {
      // Since onCompleted is async and the span.end() is called in the main thread,
      // it is recommended to set the span Status in the main thread.
    }
  }

  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting.
   */
  public static void main(String[] args) throws Exception {
    // Access a service running on the local machine on port 50053
    HelloWorldClientStream client = new HelloWorldClientStream("localhost", 50053);
    try {
      List<String> users = Arrays.asList("world", "this", "is", "a", "list", "of", "names");
      // Use the arg as the name to greet if provided
      if (args.length > 0) {
        users = Arrays.asList(args);
      }
      client.greet(users);
    } finally {
      client.shutdown();
    }
  }
}
