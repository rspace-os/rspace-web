import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { startOtel } = vi.hoisted(() => ({ startOtel: vi.fn() }));

vi.mock("@/common/otelImpl", () => ({ startOtel }));

describe("browser telemetry bootstrap", () => {
  beforeEach(() => {
    vi.resetModules();
    startOtel.mockReset();
  });

  afterEach(() => {
    document.head.querySelector('meta[name="rs-otel-web"]')?.remove();
    vi.restoreAllMocks();
  });

  it("does not load telemetry when server configuration disables it", async () => {
    const { initOtel } = await import("@/common/otel");

    await initOtel();

    expect(startOtel).not.toHaveBeenCalled();
  });

  it("starts telemetry once with server-provided configuration", async () => {
    const meta = document.createElement("meta");
    meta.name = "rs-otel-web";
    meta.content = "true";
    meta.dataset.traceSamplingRatio = "0.25";
    document.head.append(meta);
    const { initOtel } = await import("@/common/otel");

    await Promise.all([initOtel(), initOtel()]);

    expect(startOtel).toHaveBeenCalledOnce();
    expect(startOtel).toHaveBeenCalledWith({
      tracesUrl: `${window.location.origin}/otlp/v1/traces`,
      metricsUrl: `${window.location.origin}/otlp/v1/metrics`,
      traceSamplingRatio: 0.25,
    });
  });

  it("disables trace sampling and warns when the configured ratio is invalid", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const meta = document.createElement("meta");
    meta.name = "rs-otel-web";
    meta.content = "true";
    meta.dataset.traceSamplingRatio = "2";
    document.head.append(meta);
    const { initOtel } = await import("@/common/otel");

    await initOtel();

    expect(startOtel).toHaveBeenCalledWith(expect.objectContaining({ traceSamplingRatio: 0 }));
    expect(warn).toHaveBeenCalledWith("Invalid OpenTelemetry trace sampling ratio; tracing is disabled");
  });

  it("runs page startup only after telemetry is ready", async () => {
    let releaseTelemetry: (() => void) | undefined;
    startOtel.mockReturnValue(
      new Promise<void>((resolve) => {
        releaseTelemetry = resolve;
      }),
    );
    const meta = document.createElement("meta");
    meta.name = "rs-otel-web";
    meta.content = "true";
    meta.dataset.traceSamplingRatio = "1";
    document.head.append(meta);
    const { onPageLoadWithOtel } = await import("@/common/otel");
    const startPage = vi.fn();

    onPageLoadWithOtel(startPage);
    window.dispatchEvent(new Event("load"));
    await Promise.resolve();
    expect(startPage).not.toHaveBeenCalled();

    releaseTelemetry?.();
    await vi.waitFor(() => expect(startPage).toHaveBeenCalledOnce());
  });
});
