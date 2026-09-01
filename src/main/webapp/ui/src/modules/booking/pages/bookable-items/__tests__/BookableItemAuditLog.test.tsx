import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { BookableItemAuditLog } from "../BookableItemAuditLog";

vi.mock("@/modules/common/hooks/auth", () => ({ useOauthTokenQuery: vi.fn() }));

const snapshotFingerprint = "b".repeat(64);
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

function auditPage(
  docs = [event],
  options: { page?: number; totalPages?: number; hasPrevPage?: boolean; hasNextPage?: boolean } = {},
) {
  const page = options.page ?? 1;
  const totalPages = options.totalPages ?? 1;
  return {
    docs,
    totalDocs: totalPages * 20,
    limit: 20,
    page,
    pagingCounter: (page - 1) * 20 + 1,
    totalPages,
    hasPrevPage: options.hasPrevPage ?? page > 1,
    hasNextPage: options.hasNextPage ?? page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
    snapshotDate: "2026-08-25",
    snapshotFingerprint,
  };
}

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {children}
    </QueryClientProvider>
  );
}

function renderAudit() {
  return render(<BookableItemAuditLog configurationId={7} />, { wrapper });
}

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);

beforeEach(() => {
  mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
});

describe("BookableItemAuditLog", () => {
  it("loads a fresh daily snapshot and renders exact duplicates as separate rows", async () => {
    let request: Request | undefined;
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request: nextRequest }) => {
        request = nextRequest;
        return HttpResponse.json(auditPage([event, event]));
      }),
    );
    const { container } = renderAudit();

    expect((await screen.findAllByText("Ada Lovelace (ada)")).length).toBeGreaterThanOrEqual(2);
    const parameters = new URL(request?.url ?? "http://invalid").searchParams;
    expect(parameters.get("page")).toBe("1");
    expect(parameters.has("snapshotDate")).toBe(false);
    expect(parameters.get("dateFrom")).toMatch(/T00:00:00\.000Z$/);
    expect(parameters.get("dateTo")).toMatch(/T23:59:59\.999Z$/);
    expect(screen.getByText("booking:bookableItemDetails.audit.resultsThrough")).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("booking:bookableItemDetails.audit.status.loaded");
    expect(screen.getAllByRole("link", { name: "bookings:41" })[0]).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/41",
    );
    await expectAccessible(container);
  });

  it("applies inclusive presets as a new result set", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(auditPage());
      }),
    );
    renderAudit();
    await screen.findAllByText("Ada Lovelace (ada)");

    await user.click(screen.getAllByRole("button", { name: "booking:bookableItemDetails.audit.lastDays" })[0]);
    await waitFor(() => expect(requests).toHaveLength(2));
    const from = requests[1].searchParams.get("dateFrom");
    const to = requests[1].searchParams.get("dateTo");
    expect((Date.parse(to ?? "") - Date.parse(from ?? "") + 1) / 86_400_000).toBe(7);
    expect(requests[1].searchParams.has("snapshotDate")).toBe(false);
  });

  it("rejects invalid drafts, associates guidance, and focuses the first invalid field", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () => {
        requests += 1;
        return HttpResponse.json(auditPage());
      }),
    );
    const { container } = renderAudit();
    await screen.findAllByText("Ada Lovelace (ada)");
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

  it("sends the first page snapshot when paging forward and back", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const page = Number(url.searchParams.get("page"));
        return HttpResponse.json(
          auditPage([{ ...event, eventId: `${page}`.repeat(64) }], {
            page,
            totalPages: 2,
          }),
        );
      }),
    );
    renderAudit();
    await screen.findAllByText("Ada Lovelace (ada)");

    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.nextPage" }));
    await waitFor(() => expect(requests).toHaveLength(2));
    expect(Object.fromEntries(requests[1].searchParams)).toMatchObject({
      page: "2",
      snapshotDate: "2026-08-25",
      snapshotFingerprint,
    });

    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.previousPage" }));
    await waitFor(() => expect(requests).toHaveLength(3));
    expect(Object.fromEntries(requests[2].searchParams)).toMatchObject({
      page: "1",
      snapshotDate: "2026-08-25",
      snapshotFingerprint,
    });
  });

  it("Refresh keeps focus and discards the snapshot", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(auditPage([event], { totalPages: 2 }));
      }),
    );
    renderAudit();
    await screen.findAllByText("Ada Lovelace (ada)");
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.nextPage" }));
    await waitFor(() => expect(requests).toHaveLength(2));
    const refresh = screen.getByRole("button", { name: "booking:bookableItemDetails.audit.refresh" });

    await user.click(refresh);
    await waitFor(() => expect(requests).toHaveLength(3));
    expect(refresh).toHaveFocus();
    expect(requests[2].searchParams.get("page")).toBe("1");
    expect(requests[2].searchParams.has("snapshotDate")).toBe(false);
  });

  it("focuses a conflict and restarts without the stale snapshot", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        if (url.searchParams.get("page") === "2") {
          return HttpResponse.json(
            { status: 409, code: "errors.api.v2.audit.snapshot.changed", detail: "Changed" },
            { status: 409 },
          );
        }
        return HttpResponse.json(auditPage([event], { totalPages: 2 }));
      }),
    );
    renderAudit();
    await screen.findAllByText("Ada Lovelace (ada)");
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.audit.nextPage" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("booking:bookableItemDetails.audit.conflict.title");
    expect(alert).toHaveFocus();
    expect(screen.queryByText("Ada Lovelace")).not.toBeInTheDocument();
    await user.click(within(alert).getByRole("button", { name: "booking:bookableItemDetails.audit.restart" }));

    await waitFor(() => expect(requests).toHaveLength(3));
    expect(requests[2].searchParams.has("snapshotDate")).toBe(false);
    expect(screen.getByRole("heading", { name: "booking:bookableItemDetails.audit.plural" })).toHaveFocus();
  });

  it.each([
    [503, "errors.api.v2.audit.unavailable", "bookableItemDetails.audit.unavailable.title"],
    [400, "errors.api.v2.audit.results.tooMany", "bookableItemDetails.audit.tooMany.title"],
    [500, "errors.api.v2.unknown", "bookableItemDetails.audit.error.title"],
  ])("renders a distinct accessible request failure for %s", async (status, code, title) => {
    server.use(
      http.get("/api/v2/booking-configurations/7/audit", () =>
        HttpResponse.json({ status, code, detail: "Internal detail" }, { status }),
      ),
    );
    const { container } = renderAudit();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(`booking:${title}`);
    expect(alert).not.toHaveTextContent("Internal detail");
    expect(screen.queryByText("Ada Lovelace")).not.toBeInTheDocument();
    await expectAccessible(container);
  });
});
