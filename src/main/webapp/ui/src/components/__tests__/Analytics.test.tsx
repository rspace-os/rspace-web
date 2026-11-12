import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeAutoObservable } from "mobx";
import { HttpResponse, http } from "msw";
import posthog from "posthog-js";
import { StrictMode, useContext } from "react";
import { beforeEach, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";

// Keeps one React instance across vi.resetModules(), which each test uses to
// reset the module-level setup promise in Analytics.tsx.
vi.mock("react", async (importOriginal) => importOriginal());
vi.mock("posthog-js", () => ({ default: { init: vi.fn(), capture: vi.fn() } }));

beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  Reflect.deleteProperty(window, "analytics");
});

async function loadAnalytics() {
  const { default: Analytics } = await import("../Analytics");
  const { default: AnalyticsContext } = await import("@/stores/contexts/Analytics");
  const { observer } = await import("mobx-react-lite");
  const context = makeAutoObservable({
    isAvailable: null as boolean | null,
    trackEvent: vi.fn(),
  });
  const Status = observer(() => {
    const analytics = useContext(AnalyticsContext);
    return (
      <button type="button" onClick={() => analytics.trackEvent("React event", { source: "test" })}>
        {String(analytics.isAvailable)}
      </button>
    );
  });
  return { Analytics, AnalyticsContext, context, Status };
}

it("shares properties and PostHog initialization across Analytics trees", async () => {
  let requests = 0;
  server.use(
    http.get("/session/ajax/analyticsProperties", () => {
      requests++;
      return HttpResponse.json({
        analyticsEnabled: true,
        analyticsServerType: "posthog",
        analyticsServerHost: "https://analytics.example.com",
        analyticsServerKey: "test-key",
      });
    }),
  );
  const user = userEvent.setup();
  const { Analytics, Status, AnalyticsContext, context } = await loadAnalytics();
  const otherContext = makeAutoObservable({ ...context, isAvailable: null });
  render(
    <StrictMode>
      <AnalyticsContext.Provider value={context}>
        <Analytics>
          <Status />
        </Analytics>
      </AnalyticsContext.Provider>
    </StrictMode>,
  );
  render(
    <AnalyticsContext.Provider value={otherContext}>
      <Analytics>
        <Status />
      </Analytics>
    </AnalyticsContext.Provider>,
  );
  await waitFor(() => expect(screen.getAllByRole("button", { name: "true" })).toHaveLength(2));
  const buttons = screen.getAllByRole("button", { name: "true" });
  expect(requests).toBe(1);
  expect(posthog.init).toHaveBeenCalledExactlyOnceWith("test-key", { api_host: "https://analytics.example.com" });
  await user.click(buttons[0]);
  expect(posthog.capture).toHaveBeenCalledWith("React event", { source: "test" });
});

it("replays legacy events queued before initialization and captures subsequent events", async () => {
  server.use(
    http.get("/session/ajax/analyticsProperties", () =>
      HttpResponse.json({
        analyticsEnabled: true,
        analyticsServerType: "posthog",
        analyticsServerKey: "test-key",
      }),
    ),
  );
  const queue: Array<[string, Record<string, unknown> | undefined]> = [];
  window.analytics = {
    _queue: queue,
    track: (event, properties) => {
      queue.push([event, properties]);
    },
  };
  window.analytics.track("Before init", { queued: true });
  const { Analytics, Status } = await loadAnalytics();
  render(
    <Analytics>
      <Status />
    </Analytics>,
  );
  await screen.findByRole("button", { name: "true" });
  expect(posthog.capture).toHaveBeenNthCalledWith(1, "Before init", { queued: true });
  window.analytics.track("After init", { queued: false });
  expect(posthog.capture).toHaveBeenNthCalledWith(2, "After init", { queued: false });
  expect(posthog.capture).toHaveBeenCalledTimes(2);
});

it("keeps analytics unavailable when disabled", async () => {
  server.use(http.get("/session/ajax/analyticsProperties", () => HttpResponse.json({ analyticsEnabled: false })));
  const { Analytics, Status } = await loadAnalytics();
  render(
    <Analytics>
      <Status />
    </Analytics>,
  );
  expect(await screen.findByRole("button", { name: "false" })).toBeInTheDocument();
  expect(posthog.init).not.toHaveBeenCalled();
});

it("handles a failed properties request and lets a later mount retry", async () => {
  let requests = 0;
  server.use(
    http.get("/session/ajax/analyticsProperties", () => {
      requests++;
      return requests === 1
        ? new HttpResponse(null, { status: 503 })
        : HttpResponse.json({ analyticsEnabled: true, analyticsServerType: "posthog", analyticsServerKey: "test-key" });
    }),
  );
  const { Analytics, Status, AnalyticsContext, context } = await loadAnalytics();
  render(
    <AnalyticsContext.Provider value={context}>
      <Analytics>
        <Status />
      </Analytics>
    </AnalyticsContext.Provider>,
  );
  await screen.findByRole("button", { name: "false" });
  render(
    <Analytics>
      <Status />
    </Analytics>,
  );
  await screen.findByRole("button", { name: "true" });
  expect(requests).toBe(2);
  expect(posthog.init).toHaveBeenCalledTimes(1);
});
