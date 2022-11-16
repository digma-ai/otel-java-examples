/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.example.grpc;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * All SDK management takes place here, away from the instrumentation code, which should only access
 * the OpenTelemetry APIs.
 */
public final class ExampleConfiguration {

  /**
   * Initialize OpenTelemetry.
   *
   * @return a ready-to-use {@link OpenTelemetry} instance.
   */
  static OpenTelemetry initOpenTelemetry(String serviceName) {
    // Include required service.name resource attribute on all spans and metrics
    Resource resource =
        Resource.getDefault().merge(Resource.builder().put(SERVICE_NAME, serviceName).build());

    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder()
            // install the W3C Trace Context propagator
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(
                                SpanExporter.composite(
                                    OtlpGrpcSpanExporter.builder()
                                        .setTimeout(2, TimeUnit.SECONDS)
                                        .build(),
                                    LoggingSpanExporter.create()))
                            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                            .build())
                    .build())
            .setMeterProvider(
                SdkMeterProvider.builder()
                    .setResource(resource)
                    .registerMetricReader(
                        PeriodicMetricReader.builder(OtlpGrpcMetricExporter.getDefault())
                            .setInterval(Duration.ofMillis(1000))
                            .build())
                    .build())
            .buildAndRegisterGlobal();

    Runtime.getRuntime()
        .addShutdownHook(new Thread(openTelemetrySdk.getSdkTracerProvider()::shutdown));
    Runtime.getRuntime()
        .addShutdownHook(new Thread(openTelemetrySdk.getSdkMeterProvider()::shutdown));

    return openTelemetrySdk;
  }
}
