import { createContext, createElement, type ReactNode, useCallback, useContext } from "react";

export type OtelWebConfig = {
  tracesUrl: string;
  metricsUrl: string;
  traceSamplingRatio: number;
};

export type UiTraceContext = {
  page: string;
  reactIsland?: string;
};

type RunWithUiTraceContext = <T>(traceContext: UiTraceContext, callback: () => T) => T;

let initialisation: Promise<void> | null = null;
let runWithUiTraceContextImpl: RunWithUiTraceContext | undefined;
let setUiPage: ((page: string) => void) | undefined;

const ReactUiTraceContext = createContext<UiTraceContext | undefined>(undefined);

export function UiTraceContextProvider({
  traceContext,
  children,
}: {
  traceContext: UiTraceContext;
  children?: ReactNode;
}): ReactNode {
  return createElement(ReactUiTraceContext.Provider, { value: traceContext }, children);
}

export function useTracedRequest(): <T>(callback: () => T) => T {
  const traceContext = useContext(ReactUiTraceContext);
  return useCallback(
    <T>(callback: () => T): T =>
      traceContext && runWithUiTraceContextImpl ? runWithUiTraceContextImpl(traceContext, callback) : callback(),
    [traceContext],
  );
}

function readConfig(): OtelWebConfig | null {
  const meta = document.querySelector<HTMLMetaElement>('meta[name="rs-otel-web"]');
  if (!meta) return null;
  if (meta.content !== "true") return null;
  const configuredRatio = meta.dataset.traceSamplingRatio?.trim();
  const parsedRatio = Number(configuredRatio);
  const validRatio = configuredRatio !== undefined && configuredRatio !== "" && parsedRatio >= 0 && parsedRatio <= 1;
  if (!validRatio) console.warn("Invalid OpenTelemetry trace sampling ratio; tracing is disabled");
  const traceSamplingRatio = validRatio ? parsedRatio : 0;
  const origin = window.location.origin;
  return {
    tracesUrl: `${origin}/otlp/v1/traces`,
    metricsUrl: `${origin}/otlp/v1/metrics`,
    traceSamplingRatio,
  };
}

export function initOtel(): Promise<void> {
  if (initialisation) return initialisation;
  const config = readConfig();
  if (!config) {
    initialisation = Promise.resolve();
    return initialisation;
  }
  initialisation = import("./otelImpl")
    .then(async (otel) => {
      await otel.startOtel(config);
      if ("runWithUiTraceContext" in otel) runWithUiTraceContextImpl = otel.runWithUiTraceContext;
      if ("setUiPage" in otel) setUiPage = otel.setUiPage;
    })
    .catch((e) => {
      console.warn("OpenTelemetry initialisation failed", e);
    });
  return initialisation;
}

export function onPageLoadWithOtel(startPage: () => void): void;
export function onPageLoadWithOtel(traceContext: UiTraceContext, startPage: () => void): void;
export function onPageLoadWithOtel(
  traceContextOrStartPage: UiTraceContext | (() => void),
  startPage?: () => void,
): void {
  const traceContext = typeof traceContextOrStartPage === "function" ? undefined : traceContextOrStartPage;
  const pageStarter = typeof traceContextOrStartPage === "function" ? traceContextOrStartPage : startPage;
  if (!pageStarter) throw new TypeError("startPage is required");
  const telemetryReady = initOtel();
  window.addEventListener("load", () => {
    void telemetryReady.then(() => {
      if (traceContext) setUiPage?.(traceContext.page);
      if (traceContext && runWithUiTraceContextImpl) {
        runWithUiTraceContextImpl(traceContext, pageStarter);
      } else {
        pageStarter();
      }
    });
  });
}
