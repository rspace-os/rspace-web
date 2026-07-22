// Keep this behind the dynamic import in ./otel so disabled telemetry loads no SDK code.

import { context, createContextKey, metrics, type Span } from "@opentelemetry/api";
import { ZoneContextManager } from "@opentelemetry/context-zone";
import { AggregationTemporalityPreference, OTLPMetricExporter } from "@opentelemetry/exporter-metrics-otlp-http";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { registerInstrumentations } from "@opentelemetry/instrumentation";
import { DocumentLoadInstrumentation } from "@opentelemetry/instrumentation-document-load";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { UserInteractionInstrumentation } from "@opentelemetry/instrumentation-user-interaction";
import { XMLHttpRequestInstrumentation } from "@opentelemetry/instrumentation-xml-http-request";
import { resourceFromAttributes } from "@opentelemetry/resources";
import { MeterProvider, PeriodicExportingMetricReader } from "@opentelemetry/sdk-metrics";
import {
  BatchSpanProcessor,
  ParentBasedSampler,
  TraceIdRatioBasedSampler,
  WebTracerProvider,
} from "@opentelemetry/sdk-trace-web";
import { ATTR_SERVICE_NAME } from "@opentelemetry/semantic-conventions";
import { type Metric, onCLS, onINP, onLCP } from "web-vitals";
import type { OtelWebConfig, UiTraceContext } from "./otel";

const OTLP_EXPORT_URLS = /\/otlp\/v1\//;

const METRIC_EXPORT_INTERVAL_MILLIS = 30_000;

const UI_TRACE_CONTEXT = createContextKey("rspace.ui.trace-context");

let uiPage: string | undefined;

export function setUiPage(page: string): void {
  uiPage = page;
}

export function runWithUiTraceContext<T>(traceContext: UiTraceContext, callback: () => T): T {
  return context.with(context.active().setValue(UI_TRACE_CONTEXT, traceContext), callback);
}

function annotateRequestSpan(span: Span): void {
  const traceContext = context.active().getValue(UI_TRACE_CONTEXT) as UiTraceContext | undefined;
  const page = traceContext?.page ?? uiPage;
  if (page) span.setAttribute("rspace.ui.page", page);
  if (traceContext?.reactIsland) {
    span.setAttribute("rspace.react.island", traceContext.reactIsland);
  }
}

export function startOtel(config: OtelWebConfig): void {
  const resource = resourceFromAttributes({
    [ATTR_SERVICE_NAME]: "rspace-frontend",
  });

  const tracerProvider = new WebTracerProvider({
    resource,
    sampler: new ParentBasedSampler({
      root: new TraceIdRatioBasedSampler(config.traceSamplingRatio),
    }),
    spanProcessors: [new BatchSpanProcessor(new OTLPTraceExporter({ url: config.tracesUrl }))],
  });
  tracerProvider.register({ contextManager: new ZoneContextManager() });

  const meterProvider = new MeterProvider({
    resource,
    readers: [
      new PeriodicExportingMetricReader({
        exporter: new OTLPMetricExporter({
          url: config.metricsUrl,
          // Elastic does not support cumulative histograms.
          temporalityPreference: AggregationTemporalityPreference.DELTA,
        }),
        exportIntervalMillis: METRIC_EXPORT_INTERVAL_MILLIS,
      }),
    ],
  });
  metrics.setGlobalMeterProvider(meterProvider);

  registerInstrumentations({
    tracerProvider,
    meterProvider,
    instrumentations: [
      new DocumentLoadInstrumentation(),
      new FetchInstrumentation({
        ignoreUrls: [OTLP_EXPORT_URLS],
        requestHook: annotateRequestSpan,
      }),
      new XMLHttpRequestInstrumentation({
        ignoreUrls: [OTLP_EXPORT_URLS],
        applyCustomAttributesOnSpan: annotateRequestSpan,
      }),
      new UserInteractionInstrumentation(),
    ],
  });

  reportWebVitals(meterProvider);

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      void tracerProvider.forceFlush().catch((error) => console.warn("Failed to flush OpenTelemetry traces", error));
      void meterProvider.forceFlush().catch((error) => console.warn("Failed to flush OpenTelemetry metrics", error));
    }
  });
}

function reportWebVitals(meterProvider: MeterProvider): void {
  const meter = meterProvider.getMeter("rspace.web-vitals");
  const histograms = {
    LCP: meter.createHistogram("rspace.web_vitals.lcp", {
      description: "Largest Contentful Paint",
      unit: "ms",
    }),
    INP: meter.createHistogram("rspace.web_vitals.inp", {
      description: "Interaction to Next Paint",
      unit: "ms",
    }),
    CLS: meter.createHistogram("rspace.web_vitals.cls", {
      description: "Cumulative Layout Shift",
      unit: "1",
    }),
  };
  const record = (metric: Metric) => {
    histograms[metric.name as keyof typeof histograms]?.record(metric.value, {
      "web_vital.rating": metric.rating,
      "page.path": window.location.pathname,
    });
  };
  onLCP(record);
  onINP(record);
  onCLS(record);
}
