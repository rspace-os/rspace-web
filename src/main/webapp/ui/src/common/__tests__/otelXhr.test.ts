import type { ReadableSpan } from "@opentelemetry/sdk-trace";
import { HttpResponse, http } from "msw/http";
import { beforeAll, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";

const { exported } = vi.hoisted(() => ({ exported: [] as ReadableSpan[] }));
vi.mock("@opentelemetry/exporter-trace-otlp-http", () => ({
  OTLPTraceExporter: class {
    export(spans: ReadableSpan[], done: (result: { code: number }) => void) {
      exported.push(...spans);
      done({ code: 0 });
    }
    shutdown() {
      return Promise.resolve();
    }
  },
}));
vi.mock("@opentelemetry/sdk-trace", async (original) => {
  const sdk = await original<typeof import("@opentelemetry/sdk-trace")>();
  return { ...sdk, BatchSpanProcessor: sdk.SimpleSpanProcessor };
});
// Axios uses XHR in the browser; isolate it from the other instrumentations.
const disabledInstrumentation = class {
  getConfig() {
    return { enabled: false };
  }
  setTracerProvider() {}
  setMeterProvider() {}
  enable() {}
  disable() {}
};
vi.mock("@opentelemetry/instrumentation-document-load", () => ({
  DocumentLoadInstrumentation: disabledInstrumentation,
}));
vi.mock("@opentelemetry/instrumentation-fetch", () => ({ FetchInstrumentation: disabledInstrumentation }));
vi.mock("web-vitals", () => ({ onLCP() {}, onINP() {}, onCLS() {} }));

const { startOtel, runWithUiTraceContext } = await import("../otelImpl");

beforeAll(() => {
  startOtel({
    tracesUrl: "http://localhost/otlp/v1/traces",
    metricsUrl: "http://localhost/otlp/v1/metrics",
    traceSamplingRatio: 1,
  });
});

function sendXhr(url: string): Promise<number> {
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open("GET", url);
    xhr.addEventListener("loadend", () => resolve(xhr.status));
    xhr.send();
  });
}

test.each([
  ["success", 200],
  ["server error", 500],
])("XHR %s: exported URL excludes secrets and keeps UI context", async (_case, status) => {
  server.use(http.get(`${window.location.origin}/data`, () => new HttpResponse(null, { status })));
  exported.length = 0;

  const responseStatus = await runWithUiTraceContext({ page: "apps", reactIsland: "app-bar" }, () =>
    sendXhr("/data?token=acceptance-secret#acceptance-secret"),
  );

  expect(responseStatus).toBe(status);
  await vi.waitFor(() => expect(exported).toHaveLength(1));
  expect(exported[0].attributes["rspace.ui.page"]).toBe("apps");
  expect(exported[0].attributes["rspace.react.island"]).toBe("app-bar");
  expect(JSON.stringify(exported[0].attributes)).not.toContain("acceptance-secret");
  expect(exported[0].attributes["url.full"]).toBe(`${window.location.origin}/data`);
});
