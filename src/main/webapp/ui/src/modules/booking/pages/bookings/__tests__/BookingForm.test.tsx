import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { type ReactNode, Suspense } from "react";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { currentWallClock } from "@/modules/booking/domain/bookingTime";
import { BookingForm, type BookingFormSubmission, type EditableBooking } from "../BookingForm";

const target = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

const editableBooking = {
  id: 41,
  target: {
    relationTo: "instruments",
    globalId: "IN123",
    value: { id: 123, name: "Confocal microscope", deleted: false },
  },
  timezone: "Europe/Berlin",
  start: "2026-10-25T01:30:00Z",
  end: "2026-10-25T02:30:00Z",
  state: "CONFIRMED",
  privacy: "full",
  purpose: "Imaging",
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  createdAt: "2026-08-12T10:00:00Z",
  updatedAt: "2026-08-12T10:00:00Z",
} satisfies EditableBooking;

function collectionPage(docs: readonly unknown[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 20,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length ? 1 : 0,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

function renderForm(node: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRootRoute();
  const formRoute = createRoute({ getParentRoute: () => root, path: "/", component: () => node });
  const calendarRoute = createRoute({
    getParentRoute: () => root,
    path: "/booking/calendar",
    component: () => <h1>{"Calendar"}</h1>,
  });
  const router = createRouter({
    routeTree: root.addChildren([formRoute, calendarRoute]),
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe("BookingForm", () => {
  it("round-trips an editable repeated-hour booking and renders a read-only item", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();

    renderForm(
      <BookingForm
        mode="edit"
        booking={editableBooking}
        configuration={target}
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "booking:bookings.form.laterOccurrence" })).toBeChecked();
    expect(screen.getByText("booking:bookings.form.purposeCount")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    expect(submit).toHaveBeenCalledOnce();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        window: { start: "2026-10-25T01:30:00Z", end: "2026-10-25T02:30:00Z" },
        target: expect.objectContaining({ globalId: "IN123" }),
      }),
    );
  });

  it("prefills only the Calendar date and reports the missing window", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));

    renderForm(
      <BookingForm
        mode="add"
        initialTarget={target}
        initialDate="2026-08-17"
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    expect(within(start).getByLabelText("booking:bookings.form.date")).toHaveValue("2026-08-17");
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveValue("");
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveAttribute("step", "300");
    expect(within(end).getByLabelText("booking:bookings.form.date")).toHaveValue("2026-08-17");
    expect(screen.getByText("booking:bookings.form.openingHours")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));

    expect(screen.getByText("booking:bookings.errors.windowRequired")).toBeVisible();
    expect(submit).not.toHaveBeenCalled();
  });

  it("uses today in the selected item timezone when no Calendar date exists", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));
    renderForm(<BookingForm mode="add" initialTarget={target} token="token" pending={false} onSubmit={vi.fn()} />);
    const expected = currentWallClock(new Date().toISOString(), target.timezone).date;
    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });

    expect(within(start).getByLabelText("booking:bookings.form.date")).toHaveValue(expected);
    expect(within(end).getByLabelText("booking:bookings.form.date")).toHaveValue(expected);
  });

  it("clears repeated-hour choices when the item timezone changes", async () => {
    const user = userEvent.setup();
    const paris = { ...target, configurationId: 8, targetId: 124, globalId: "IN124", timezone: "Europe/Paris" };
    server.use(
      http.get("/api/v2/booking-configurations", () =>
        HttpResponse.json(
          collectionPage([
            {
              id: target.configurationId,
              target: {
                relationTo: "instruments",
                globalId: target.globalId,
                value: { id: target.targetId, name: target.name, deleted: false },
              },
              timezone: target.timezone,
              slotGranularityMinutes: target.slotGranularityMinutes,
              openingStart: target.openingStart,
              openingEnd: target.openingEnd,
              bufferBeforeMinutes: target.bufferBeforeMinutes,
              bufferAfterMinutes: target.bufferAfterMinutes,
              maxBookingDurationMinutes: target.maxBookingDurationMinutes,
              allowDoubleBooking: target.allowDoubleBooking,
            },
            {
              id: paris.configurationId,
              target: {
                relationTo: "instruments",
                globalId: paris.globalId,
                value: { id: paris.targetId, name: paris.name, deleted: false },
              },
              timezone: paris.timezone,
              slotGranularityMinutes: paris.slotGranularityMinutes,
              openingStart: paris.openingStart,
              openingEnd: paris.openingEnd,
              bufferBeforeMinutes: paris.bufferBeforeMinutes,
              bufferAfterMinutes: paris.bufferAfterMinutes,
              maxBookingDurationMinutes: paris.maxBookingDurationMinutes,
              allowDoubleBooking: paris.allowDoubleBooking,
            },
          ]),
        ),
      ),
    );
    renderForm(
      <BookingForm
        mode="add"
        initialTarget={target}
        initialDate="2026-10-25"
        token="token"
        pending={false}
        onSubmit={vi.fn()}
      />,
    );
    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    await user.type(within(start).getByLabelText("booking:bookings.form.time"), "02:30");
    await user.type(within(end).getByLabelText("booking:bookings.form.time"), "04:00");
    await user.click(screen.getByRole("radio", { name: "booking:bookings.form.earlierOccurrence" }));
    expect(screen.getByRole("radio", { name: "booking:bookings.form.earlierOccurrence" })).toBeChecked();

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.itemChoose" }));
    const options = await screen.findAllByRole("option", { name: "booking:bookings.form.itemOption" });
    await user.click(options[1]);

    expect(screen.getByRole("radio", { name: "booking:bookings.form.earlierOccurrence" })).not.toBeChecked();
    expect(screen.getByText("booking:bookings.errors.occurrenceRequired")).toBeVisible();
  });

  it("canonicalizes a blank purpose to null and prevents duplicate submits", async () => {
    const user = userEvent.setup();
    let release: (() => void) | undefined;
    const submit = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          release = resolve;
        }),
    );
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));
    renderForm(
      <BookingForm
        mode="add"
        initialTarget={target}
        initialDate="2026-08-17"
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );
    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    await user.type(within(start).getByLabelText("booking:bookings.form.time"), "09:00");
    await user.type(within(end).getByLabelText("booking:bookings.form.time"), "10:00");
    const button = screen.getByRole("button", { name: "booking:bookings.form.submit" });

    await user.dblClick(button);

    expect(submit).toHaveBeenCalledOnce();
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ purpose: null }));
    expect(button).toBeDisabled();
    await act(async () => release?.());
  });

  it("shows the maximum and blocks an over-limit booking", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));
    renderForm(
      <BookingForm
        mode="add"
        initialTarget={{ ...target, maxBookingDurationMinutes: 60 }}
        initialDate="2026-08-17"
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );
    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    expect(screen.getByText("booking:bookings.form.maximumDuration")).toBeVisible();
    await user.type(within(start).getByLabelText("booking:bookings.form.time"), "09:00");
    await user.type(within(end).getByLabelText("booking:bookings.form.time"), "10:05");

    expect(screen.getByText("booking:bookings.errors.maximumDuration")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));
    expect(submit).not.toHaveBeenCalled();
  });

  it("allows an unchanged over-limit interval on a purpose-only edit", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    renderForm(
      <BookingForm
        mode="edit"
        booking={editableBooking}
        configuration={{ ...target, maxBookingDurationMinutes: 30 }}
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    expect(screen.queryByText("booking:bookings.errors.maximumDuration")).not.toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "booking:bookings.form.save" }));
    expect(submit).toHaveBeenCalledOnce();
  });
});
