import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettings";
import { BookableItemPicker, loadBookableItems } from "../BookableItemPicker";

function envelope(docs: unknown[], page = 1, totalPages = 1) {
  return {
    docs,
    totalDocs: totalPages > 1 ? 21 : docs.length,
    limit: 20,
    page,
    pagingCounter: (page - 1) * 20 + 1,
    totalPages,
    hasPrevPage: page > 1,
    hasNextPage: page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
  };
}

function configuration(id: number, name: string) {
  return {
    id,
    target: {
      relationTo: "instruments",
      globalId: `IN${id}`,
      value: { id, name, deleted: false },
    },
    timezone: "Europe/Berlin",
    ...DEFAULT_SCHEDULING_SETTINGS,
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
      http.get("/api/v2/booking-configurations", ({ request }) => {
        requestUrl = new URL(request.url);
        return HttpResponse.json(envelope([configuration(12, "Scope")]));
      }),
    );

    const page = await loadBookableItems({ target: "IN12" }, "token", new AbortController().signal);

    expect(requestUrl?.searchParams.get("where")).toBe("enabled==true;target==IN12");
    expect(requestUrl?.searchParams.get("fields[booking-configurations]")).toBe(
      "id,target,timezone,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking",
    );
    expect(page.options[0]).toEqual({
      configurationId: 12,
      targetId: 12,
      globalId: "IN12",
      name: "Scope",
      timezone: "Europe/Berlin",
      ...DEFAULT_SCHEDULING_SETTINGS,
    });
  });

  it("debounces search and loads every result page", async () => {
    const pages: string[] = [];
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        const url = new URL(request.url);
        const page = url.searchParams.get("page") ?? "1";
        const where = url.searchParams.get("where") ?? "";
        if (!where.includes("confocal")) return HttpResponse.json(envelope([]));
        pages.push(page);
        return HttpResponse.json(
          page === "1"
            ? envelope([configuration(1, "Confocal A")], 1, 2)
            : envelope([configuration(2, "Confocal B")], 2, 2),
        );
      }),
    );
    const user = userEvent.setup();
    renderPicker();

    await user.type(screen.getByRole("combobox", { name: "booking:bookings.form.item" }), "confocal");

    const options = await screen.findAllByRole("option", { name: "booking:bookings.form.itemOption" });
    expect(options).toHaveLength(2);
    expect(pages).toEqual(["1", "2"]);
  });

  it("shows failure without inventing an option and honors cancellation", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json({}, { status: 500 })));
    const user = userEvent.setup();
    renderPicker();
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.itemChoose" }));
    expect(await screen.findByText("booking:bookings.errors.itemLoad")).toBeVisible();

    const controller = new AbortController();
    controller.abort();
    await expect(loadBookableItems({}, "token", controller.signal)).rejects.toThrow();
  });

  it("shows a localized empty state", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(envelope([]))));
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
      http.get("/api/v2/booking-configurations", async () => {
        await requestPending;
        return HttpResponse.json(envelope([]));
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
