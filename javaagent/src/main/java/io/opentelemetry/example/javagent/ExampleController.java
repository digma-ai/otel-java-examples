package io.opentelemetry.example.javagent;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Random;

@Controller
public class ExampleController {

    private static final Logger LOGGER = LogManager.getLogger(ExampleController.class);
    private final AttributeKey<String> ATTR_METHOD = AttributeKey.stringKey("method");

    private final Random random = new Random();
    private final Tracer tracer;
    private final LongHistogram doWorkHistogram;

    @Autowired
    ExampleController(OpenTelemetry openTelemetry) {
        tracer = openTelemetry.getTracer(Application.class.getName());
        Meter meter = openTelemetry.getMeter(Application.class.getName());
        doWorkHistogram = meter.histogramBuilder("example-do-work").ofLongs().build();
    }

    @GetMapping("/example")
    @ResponseBody()
    public String exampleMethod() throws InterruptedException {
        int sleepTime = random.nextInt(200);
        doWork(sleepTime);
        doWorkHistogram.record(sleepTime, Attributes.of(ATTR_METHOD, "someMethod"));
        return "example response";
    }

    private void doWork(int sleepTime) throws InterruptedException {
        Span span = tracer.spanBuilder("exampleDoWork").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            Thread.sleep(sleepTime);
            LOGGER.info("An example log message!");
        } finally {
            span.end();
        }
    }

}
