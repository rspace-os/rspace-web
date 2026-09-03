import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import { BookableItemPicker, loadBookableItems } from "../BookableItemPicker";

function cataloguePage(items: unknown[], page = 1, total = items.length) {
  return {
    items,
    page,
    pageSize: 20,
    total,
    facets: { types: ["INSTRUMENT"] },
  };
}

function configuration(id: number, name: string) {
  return {
    configurationId: id,
    configurationVersion: 0,
    targetType: "INSTRUMENT",
    targetId: id,
    globalId: `IN${id}`,
    name,
    timezone: "Europe/Berlin",
    ...DEFAULT_SCHEDULING_SETTINGS,
    effectiveRole: "BOOKER",
    capabilities: {
      canEditConfiguration: false,
      canViewAudit: false,
      canViewAccess: false,
      canManageAssignments: false,
      canManageOwners: false,
      canCreateBooking: true,
      canManageOwnBookings: true,
      canManageAllEvents: false,
      canCreateBlockout: false,
      canSubscribeCalendar: true,
      canLeaveConfiguration: false,
    },
    location: null,
  };
}

function renderPicker() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <BookableItemPicker value={undefined} onChange={() => {}} token="token" />
    </QueryClientProvider>,
  );
}

describe("BookableItemPicker", () => {
  it("always applies enabled and resolves an exact global ID", async () => {
    let requestUrl: URL | undefined;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        requestUrl = new URL(request.url);
        return HttpResponse.json(cataloguePage([configuration(12, "Scope")]));
      }),
    );

    const page = await loadBookableItems({ target: "IN12" }, "token", new AbortController().signal);

    expect(requestUrl?.searchParams.get("target")).toBe("IN12");
    expect(requestUrl?.searchParams.get("page")).toBe("1");
    expect(requestUrl?.searchParams.get("limit")).toBe("20");
    expect(page.options[0]).toEqual({
      configurationId: 12,
      targetId: 12,
      globalId: "IN12",
      name: "Scope",
      timezone: "Europe/Berlin",
      ...DEFAULT_SCHEDULING_SETTINGS,
    });
  });

  it("debounces search and pages through results on demand", async () => {
    const pages: string[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const page = url.searchParams.get("page") ?? "1";
        const query = url.searchParams.get("q") ?? "";
        if (query !== "confocal") return HttpResponse.json(cataloguePage([]));
        pages.push(page);
        return HttpResponse.json(
          page === "1"
            ? cataloguePage([configuration(1, "Confocal A")], 1, 21)
            : cataloguePage([configuration(2, "Confocal B")], 2, 21),
        );
      }),
    );
    const user = userEvent.setup();
    renderPicker();

    await user.type(screen.getByRole("combobox", { name: "booking:bookings.form.item" }), "confocal");

    expect(await screen.findByRole("option", { name: "booking:bookings.form.itemOption" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.nextItems" }));
    await waitFor(() => expect(pages).toEqual(["1", "2"]));
  });

  it("shows failure without inventing an option and honors cancellation", async () => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json({}, { status: 500 })));
    const user = userEvent.setup();
    renderPicker();
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.itemChoose" }));
    expect(await screen.findByText("booking:bookings.errors.itemLoad")).toBeVisible();

    const controller = new AbortController();
    controller.abort();
    await expect(loadBookableItems({}, "token", controller.signal)).rejects.toThrow();
  });

  it("shows a localized empty state", async () => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
    const user = userEvent.setup();
    renderPicker();

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.itemChoose" }));
    expect(await screen.findByText("booking:bookings.form.itemNone")).toBeVisible();
  });

  it("announces loading without showing the empty state", async () => {
    let finishRequest = () => {};
    const requestPending = new Promise<void>((resolve) => {
      finishRequest = resolve;
    });
    server.use(
      http.get("/api/v2/booking-catalogue", async () => {
        await requestPending;
        return HttpResponse.json(cataloguePage([]));
      }),
    );
    renderPicker();

    expect(await screen.findByRole("status")).toHaveTextContent("booking:bookings.loadingConfiguration");
    expect(screen.queryByText("booking:bookings.form.itemNone")).not.toBeInTheDocument();

    finishRequest();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.itemChoose" }));
    expect(await screen.findByText("booking:bookings.form.itemNone")).toBeVisible();
  });
});
