import { focusManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, it, onTestFinished, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { ListMaintenances200 } from "@/modules/common/api/v2/generated/models/listMaintenances200";
import { useNextMaintenanceQuery } from "@/modules/common/app/queries/nextMaintenance";

it("retains the maintenance notice during foreground polls and advances the cutoff", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
  onTestFinished(() => {
    vi.useRealTimers();
  });
  vi.setSystemTime(new Date("2026-07-01T09:00:00Z"));
  focusManager.setFocused(true);
  onTestFinished(() => focusManager.setFocused(undefined));
  const firstStart = "2026-07-01T10:00:00Z";
  const nextStart = "2026-07-01T11:00:00Z";
  let finishPoll: () => void = () => undefined;
  const pendingPoll = new Promise<void>((resolve) => {
    finishPoll = resolve;
  });
  onTestFinished(() => finishPoll());
  const filters: string[] = [];
  server.use(
    http.get("/api/v2/maintenances", async ({ request }) => {
      filters.push(new URL(request.url).searchParams.get("where") ?? "");
      const startDate = filters.length === 1 ? firstStart : nextStart;
      if (filters.length > 1) await pendingPoll;
      return HttpResponse.json<ListMaintenances200>({ docs: [{ id: 1, startDate }], page: 1, limit: 1 });
    }),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  onTestFinished(() => client.clear());
  const { result, unmount } = renderHook(() => useNextMaintenanceQuery(), {
    wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  });
  onTestFinished(unmount);
  await act(async () => {
    await vi.waitFor(() => expect(result.current.data).toEqual({ startDate: new Date(firstStart) }));
  });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(5 * 60 * 1000);
  });
  await act(async () => {
    await vi.waitFor(() => expect(filters).toHaveLength(2));
  });
  expect(result.current.isFetching).toBe(true);
  expect(result.current.data).toEqual({ startDate: new Date(firstStart) });
  await act(async () => {
    finishPoll();
    await vi.waitFor(() => expect(result.current.data).toEqual({ startDate: new Date(nextStart) }));
  });
  focusManager.setFocused(false);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(5 * 60 * 1000);
  });
  expect(filters).toHaveLength(2);
  expect(filters[0]).toBe("endDate=gt=2026-07-01T09:00:00.000Z");
  expect(Date.parse(filters[1].replace("endDate=gt=", ""))).toBeGreaterThanOrEqual(Date.parse("2026-07-01T09:05:00Z"));
});
