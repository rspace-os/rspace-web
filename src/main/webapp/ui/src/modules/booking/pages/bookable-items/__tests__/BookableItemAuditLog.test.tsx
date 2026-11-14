import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { BookableItemAuditLog } from "../BookableItemAuditLog";
import { auditPage } from "./bookableItemAuditFixtures";

const event = {
  eventId: "a".repeat(64),
  timestamp: "2026-08-25T10:42:18Z",
  username: "ada",
  fullName: "Ada Lovelace",
  domain: "RECORD",
  action: "WRITE",
  description: "Updated booking configuration IN123",
  payload: { enabled: true, bookingConfigurationId: "booking-configurations:7" },
  target: "bookings:41",
};

function wrapper({ children }: { children: ReactNode }) {
  const root = createRootRoute({ component: () => children });
  const booking = createRoute({
    getParentRoute: () => root,
    path: "/booking/calendar/bookings/$id",
    component: Outlet,
  });
  const router = createRouter({
    routeTree: root.addChildren([booking]),
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>
  );
}

function renderAudit() {
  return render(<BookableItemAuditLog configurationId={7} />, { wrapper });
}

describe("BookableItemAuditLog", () => {
  it("labels the permanent-deletion timestamp and retains other recorded field names", async () => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json(
          auditPage([{ ...event, action: "DELETE", payload: { deletedAt: "2026-08-25T10:42:18Z", enabled: true } }]),
        ),
      ),
    );
    renderAudit();

    const table = within(await screen.findByRole("table"));
    expect(await table.findByText("booking:bookableItemDetails.audit.values.deletedAt")).toBeVisible();
    expect(table.getByText("2026-08-25T10:42:18Z")).toBeVisible();
    expect(table.getByText("enabled")).toBeVisible();
    expect(table.queryByText("deletedAt")).not.toBeInTheDocument();
  });

  it("loads a capped completed-day result and renders exact duplicates as separate rows", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request: nextRequest }) => {
        request = nextRequest;
        return HttpResponse.json(auditPage([event, event]));
      }),
    );
    const { container } = renderAudit();

    expect((await screen.findAllByText("common:values.userWithUsername")).length).toBeGreaterThanOrEqual(2);
    const parameters = new URL(request?.url ?? "http://invalid").searchParams;
    expect(parameters.has("page")).toBe(false);
    expect(parameters.has("snapshotDate")).toBe(false);
    expect(parameters.get("start")?.endsWith("T00:00:00.000Z")).toBe(true);
    expect(parameters.get("end")?.endsWith("T00:00:00.000Z")).toBe(true);
    expect(screen.getByText("booking:bookableItemDetails.audit.resultsThrough")).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("booking:bookableItemDetails.audit.status.loaded");
    expect(screen.getAllByText("bookings:41")[0]).toBeVisible();
    await expectAccessible(container);
  });

  it("applies inclusive presets as a new result set", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(auditPage([event]));
      }),
    );
    renderAudit();
    await screen.findAllByText("common:values.userWithUsername");

    await user.click(screen.getAllByRole("button", { name: "booking:bookableItemDetails.audit.lastDays" })[0]);
    await waitFor(() => expect(requests).toHaveLength(2));
    const from = requests[1].searchParams.get("start");
    const to = requests[1].searchParams.get("end");
    expect((Date.parse(to ?? "") - Date.parse(from ?? "")) / 86_400_000).toBe(7);
    expect(requests[1].searchParams.has("snapshotDate")).toBe(false);
  });

  it("rejects invalid drafts, associates guidance, and focuses the first invalid field", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () => {
        requests += 1;
        return HttpResponse.json(auditPage([event]));
      }),
    );
    const { container } = renderAudit();
    await screen.findAllByText("common:values.userWithUsername");
    const from = screen.getByLabelText("booking:bookableItemDetails.audit.from");

    await user.clear(from);
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.apply" }));

    expect(from).toHaveAttribute("aria-invalid", "true");
    expect(from).toHaveFocus();
    const errorId = from.getAttribute("aria-describedby");
    expect(errorId).toBe("audit-from-error");
    expect(document.getElementById(errorId ?? "")).toHaveTextContent("booking:bookableItemDetails.audit.fromError");
    expect(requests).toBe(1);
    await expectAccessible(container);
  });

  it("paginates the capped result locally and refreshes once", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () => {
        requests += 1;
        return HttpResponse.json(
          auditPage(
            Array.from({ length: 21 }, () => event),
            { limit: 21, truncated: true },
          ),
        );
      }),
    );
    const { container } = renderAudit();
    await screen.findAllByText("common:values.userWithUsername");
    expect(screen.getByRole("status")).toHaveTextContent(
      "booking:bookableItemDetails.audit.status.loaded booking:bookableItemDetails.audit.truncated",
    );
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.nextPage" }));
    expect(requests).toBe(1);
    expect(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.nextPage" })).toBeDisabled();
    const refresh = screen.getByRole("button", { name: "booking:bookableItemDetails.audit.refresh" });
    await user.click(refresh);
    await waitFor(() => expect(requests).toBe(2));
    expect(refresh).toHaveFocus();
    await expectAccessible(container);
  });

  it.each([
    [503, "errors.api.v2.audit.unavailable", "bookableItemDetails.audit.unavailable.title"],
    [500, "errors.api.v2.unknown", "bookableItemDetails.audit.error.title"],
  ])("renders a distinct accessible request failure for %s", async (status, code, title) => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json<ApiV2Problem>(
          { status, code, title: "Request failed", detail: "Internal detail" },
          { status },
        ),
      ),
    );
    const { container } = renderAudit();

    const alert = await screen.findByRole("alert");
    expect(alert).toMatchTextContent(`booking:${title}`);
    expect(alert).not.toMatchTextContent("Internal detail");
    expect(screen.queryByText("Ada Lovelace")).not.toBeInTheDocument();
    await expectAccessible(container);
  });
});
