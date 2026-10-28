import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";
import { afterAll, beforeAll, expect, test, vi } from "vitest";

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
vi.mock("@opentelemetry/sdk-trace-web", async (original) => {
  const sdk = await original<typeof import("@opentelemetry/sdk-trace-web")>();
  return { ...sdk, BatchSpanProcessor: sdk.SimpleSpanProcessor };
});
// These cases exercise fetch; jsdom does not implement browser navigation/resource timings.
vi.mock("@opentelemetry/instrumentation-document-load", () => ({
  DocumentLoadInstrumentation: class {
    getConfig() {
      return { enabled: false };
    }
    setTracerProvider() {}
    setMeterProvider() {}
    enable() {}
    disable() {}
  },
}));
vi.mock("@opentelemetry/instrumentation-xml-http-request", () => ({
  XMLHttpRequestInstrumentation: class {
    getConfig() {
      return { enabled: false };
    }
    setTracerProvider() {}
    setMeterProvider() {}
    enable() {}
    disable() {}
  },
}));
vi.mock("web-vitals", () => ({ onLCP() {}, onINP() {}, onCLS() {} }));

const { startOtel, setUiPage, runWithUiTraceContext } = await import("../otelImpl");
let mode = "success";
beforeAll(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (mode === "reject") throw new TypeError("Network failure");
      return {
        status: 200,
        url,
        clone() {
          if (mode === "clone-failure") throw new TypeError("body already used");
          if (mode === "reader-failure")
            return {
              body: {
                getReader() {
                  throw new TypeError("locked stream");
                },
              },
            };
          return { body: null };
        },
      };
    }),
  );
  startOtel({
    tracesUrl: "http://localhost/otlp/v1/traces",
    metricsUrl: "http://localhost/otlp/v1/metrics",
    traceSamplingRatio: 1,
  });
  setUiPage("apps");
});
afterAll(() => vi.unstubAllGlobals());

for (const scenario of ["success", "reject", "clone-failure", "reader-failure"]) {
  test(`fetch ${scenario}: exported URL excludes secrets and keeps UI context`, async () => {
    mode = scenario;
    exported.length = 0;
    const request = runWithUiTraceContext({ page: "apps", reactIsland: "app-bar" }, () =>
      fetch("http://localhost/data?token=acceptance-secret#acceptance-secret"),
    );
    if (scenario === "reject") await expect(request).rejects.toThrow("Network failure");
    else await request;
    await vi.waitFor(() => expect(exported).toHaveLength(1));
    expect(exported[0].attributes["rspace.ui.page"]).toBe("apps");
    expect(exported[0].attributes["rspace.react.island"]).toBe("app-bar");
    expect(JSON.stringify(exported[0].attributes)).not.toContain("acceptance-secret");
    expect(exported[0].attributes["url.full"]).toBe("http://localhost/data");
  });
}
test("export requests do not recursively produce spans", async () => {
  mode = "success";
  exported.length = 0;
  await fetch("http://localhost/otlp/v1/traces");
  await new Promise((resolve) => setTimeout(resolve, 400));
  expect(exported).toHaveLength(0);
});
