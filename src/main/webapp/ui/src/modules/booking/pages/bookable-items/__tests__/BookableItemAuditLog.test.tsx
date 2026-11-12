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
import { wrapWithRealI18n } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import bookingCatalog from "@/modules/common/i18n/locales/en-US/booking.json";
import commonCatalog from "@/modules/common/i18n/locales/en-US/common.json";
import { BookableItemAuditLog } from "../BookableItemAuditLog";
import { auditPage, permanentDeleteEvent } from "./bookableItemAuditFixtures";

const event = {
  eventId: "a".repeat(64),
  timestamp: "2026-08-25T10:42:18Z",
  username: "ada",
  fullName: "Ada Lovelace",
  domain: "RECORD",
  action: "WRITE",
  description: "Updated booking configuration IN123",
  payload: {
    enabled: true,
    bookingConfigurationId: "booking-configurations:7",
    openingHours: [{ dayOfWeek: 6, start: "10:00", end: "16:00" }],
  },
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
  it("distinguishes archives and permanent deletions under the shared DELETE filter", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(
          auditPage([
            {
              ...event,
              action: "DELETE",
              description: null,
              target: "booking-configurations:7",
              payload: { state: "ARCHIVED" },
            },
            {
              ...event,
              eventId: "f".repeat(64),
              ...permanentDeleteEvent,
              target: "booking-configurations:7",
            },
          ]),
        );
      }),
    );
    render(
      await wrapWithRealI18n(<BookableItemAuditLog configurationId={7} />, {
        resources: { booking: bookingCatalog, common: commonCatalog },
        defaultNS: "booking",
      }),
      { wrapper },
    );

    expect(within(await screen.findByRole("article", { name: "Archived" })).getByText("Archived")).toBeVisible();
    const permanentlyDeleted = screen.getByRole("article", { name: "Permanently deleted" });
    expect(within(permanentlyDeleted).getByText("Permanently deleted")).toBeVisible();
    await user.click(within(permanentlyDeleted).getByRole("button", { name: "Expand" }));
    expect(within(permanentlyDeleted).getByText("Deleted at (UTC)")).toBeVisible();
    const filter = screen.getByRole("combobox", { name: "Action" });
    expect(within(filter).getByRole("option", { name: "Archived or deleted" })).toHaveValue("DELETE");
    await user.selectOptions(filter, "Archived or deleted");
    await waitFor(() => expect(requests).toHaveLength(2));
    expect(requests[1].searchParams.getAll("actions")).toEqual(["DELETE"]);
    expect(screen.getAllByRole("article")).toHaveLength(2);
  });

  it("labels the permanent-deletion timestamp and retains unknown recorded field names", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json(
          auditPage([
            {
              ...event,
              action: "DELETE",
              payload: { deletedAt: "2026-08-25T10:42:18Z", enabled: true, customField: "retained" },
            },
          ]),
        ),
      ),
    );
    renderAudit();

    const item = within(await screen.findByRole("article"));
    await user.click(item.getByRole("button", { name: "common:actions.expand" }));
    expect(await item.findByText("booking:bookableItemDetails.audit.values.deletedAt")).toBeVisible();
    expect(item.getByText("2026-08-25T10:42:18Z")).toBeVisible();
    expect(item.getByText("booking:bookableItemDetails.audit.values.enabled")).toBeVisible();
    expect(item.getByText("customField")).toBeVisible();
    expect(item.getByText("retained")).toBeVisible();
    expect(item.queryByText("deletedAt")).not.toBeInTheDocument();
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
    expect(screen.getAllByRole("link", { name: "bookings:41" })[0]).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41",
    );
    await expectAccessible(container);
  });

  it("keeps recorded values collapsed and places Changed by beside ID", async () => {
    const user = userEvent.setup();
    server.use(http.get("/api/v2/booking-configurations/7/audit", () => HttpResponse.json(auditPage([event]))));
    const { container } = renderAudit();

    const item = await screen.findByRole("article", { name: "Updated booking configuration IN123" });
    expect(within(item).getByText("booking:bookableItemDetails.audit.fields.actor")).toBeVisible();
    expect(within(item).getByText("booking:bookableItems.fields.id")).toBeVisible();
    expect(within(item).queryByText("booking:bookableItemDetails.audit.fields.values")).not.toBeInTheDocument();

    const expand = within(item).getByRole("button", { name: "common:actions.expand" });
    expect(expand).toHaveAttribute("aria-expanded", "false");
    await user.click(expand);

    expect(within(item).getByRole("button", { name: "common:actions.collapse" })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(within(item).getByText("booking:bookableItemDetails.audit.fields.values")).toBeVisible();
    expect(within(item).getByText("booking:bookableItemDetails.audit.values.enabled")).toBeVisible();
    expect(
      within(item).getByText("booking:bookableItemDetails.audit.values.openingHours").nextElementSibling,
    ).toHaveTextContent("Saturday 10:00 AM\u201304:00 PM");
    await expectAccessible(container);
  });

  it("offers only Booking actions in Booking terms and labels a cancellation", async () => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json(
          auditPage([
            { ...event, eventId: "c".repeat(64), description: "Cancelled booking 41", payload: { state: "CANCELLED" } },
            { ...event, eventId: "d".repeat(64), description: "Edited booking 41", payload: { state: "CONFIRMED" } },
            { ...event, eventId: "e".repeat(64), action: "CREATE", description: "Created booking 41" },
          ]),
        ),
      ),
    );
    const { container } = renderAudit();

    const cancelled = await screen.findByRole("article", { name: "Cancelled booking 41" });
    expect(within(cancelled).getByText("booking:bookableItemDetails.audit.actions.cancelled")).toBeVisible();
    expect(within(cancelled).queryByText("WRITE")).not.toBeInTheDocument();
    const edited = screen.getByRole("article", { name: "Edited booking 41" });
    expect(within(edited).getByText("booking:bookableItemDetails.audit.actions.changed")).toBeVisible();
    const created = screen.getByRole("article", { name: "Created booking 41" });
    expect(within(created).getByText("booking:bookableItemDetails.audit.actions.created")).toBeVisible();

    const filter = screen.getByRole("combobox", { name: "booking:bookableItemDetails.audit.fields.action" });
    expect(
      within(filter)
        .getAllByRole("option")
        .map((option) => [(option as HTMLOptionElement).value, option.textContent]),
    ).toEqual([
      ["", "booking:bookableItemDetails.audit.allActions"],
      ["CREATE", "booking:bookableItemDetails.audit.actions.created"],
      ["WRITE", "booking:bookableItemDetails.audit.actions.changedOrCancelled"],
      ["DELETE", "booking:bookableItemDetails.audit.actions.archivedOrDeleted"],
      ["RESTORE", "booking:bookableItemDetails.audit.actions.restored"],
    ]);
    await expectAccessible(container);
  });

  it("explains the daily update when a range reaching today has no recorded changes yet", async () => {
    // The server snapshot is the previous completed UTC day, so the default range ending today reaches past it.
    const yesterday = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json({
          ...auditPage([]),
          endExclusive: new Date(new Date(`${yesterday}T00:00:00Z`).getTime() + 86400000).toISOString(),
        }),
      ),
    );
    renderAudit();

    expect(await screen.findByText("booking:bookableItemDetails.audit.emptyThroughSnapshot")).toBeVisible();
    expect(screen.queryByText("booking:bookableItemDetails.audit.emptyStable")).not.toBeInTheDocument();
    expect(screen.queryByText("booking:bookableItemDetails.audit.empty")).not.toBeInTheDocument();
    expect(screen.getByText("booking:bookableItemDetails.audit.resultsThrough")).toBeVisible();
  });

  it("sends free-text searches to the audit endpoint before paging", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        if (url.searchParams.has("search")) return new HttpResponse(null, { status: 400 });
        return HttpResponse.json(auditPage([event]));
      }),
    );
    renderAudit();
    await screen.findAllByText("common:values.userWithUsername");

    const input = screen.getByRole("textbox", { name: "common:tableList.search.label" });
    await user.type(input, "PayloadMarker");
    await user.keyboard("{Enter}");
    await waitFor(() => expect(requests).toHaveLength(2));

    expect(requests[1].searchParams.get("q")).toBe("PayloadMarker");
    expect(requests[1].searchParams.has("page")).toBe(false);
    expect(requests[1].searchParams.has("snapshotFingerprint")).toBe(false);
  });

  it("combines the action facet with search and starts a fresh page set", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        if (url.searchParams.has("search")) return new HttpResponse(null, { status: 400 });
        return HttpResponse.json(auditPage(Array.from({ length: 21 }, () => event)));
      }),
    );
    renderAudit();
    await screen.findAllByText("common:values.userWithUsername");

    expect(screen.queryByRole("button", { name: "common:tableList.toolbar.filters" })).not.toBeInTheDocument();
    const search = screen.getByRole("textbox", { name: "common:tableList.search.label" });
    await user.type(search, "PayloadMarker");
    await user.keyboard("{Enter}");
    await waitFor(() => expect(requests).toHaveLength(2));

    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.nextPage" }));
    await waitFor(() => expect(screen.getAllByRole("article")).toHaveLength(1));
    expect(requests).toHaveLength(2);

    await user.selectOptions(
      screen.getByRole("combobox", { name: "booking:bookableItemDetails.audit.fields.action" }),
      "WRITE",
    );
    await waitFor(() => expect(requests).toHaveLength(3));
    expect(Object.fromEntries(requests[2].searchParams)).toMatchObject({
      q: "PayloadMarker",
      actions: "WRITE",
    });
    expect(requests[2].searchParams.has("snapshotFingerprint")).toBe(false);
  });

  it("applies inclusive presets as a new result set", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        if (url.searchParams.has("search")) return new HttpResponse(null, { status: 400 });
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
