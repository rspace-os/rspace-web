import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen, waitFor, within } from "@testing-library/react";
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
    relationTo: "booking-instruments",
    globalId: "IN123",
    value: { id: 123, name: "Confocal microscope", deleted: false },
  },
  timezone: "Europe/Berlin",
  start: "2026-10-25T01:30:00Z",
  end: "2026-10-25T02:30:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
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

  it("preserves stored instants when an edit opens in a different display timezone", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();

    renderForm(
      <BookingForm
        mode="edit"
        displayTimezone="America/New_York"
        booking={editableBooking}
        configuration={target}
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    expect(within(start).getByLabelText("booking:bookings.form.date")).toHaveValue("2026-10-24");
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveValue("21:30");
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        window: { start: editableBooking.start, end: editableBooking.end },
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
        eventKind="BOOKING"
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

  it.each([
    [1, "60"],
    [5, "300"],
    [10, "600"],
    [15, "900"],
  ])("uses the configured %i-minute increment", async (increment, step) => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));
    renderForm(
      <BookingForm
        mode="add"
        eventKind="BOOKING"
        initialTarget={{ ...target, slotGranularityMinutes: increment }}
        initialDate="2026-08-17"
        token="token"
        pending={false}
        onSubmit={vi.fn()}
      />,
    );

    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveAttribute("step", step);
    expect(within(end).getByLabelText("booking:bookings.form.time")).toHaveAttribute("step", step);
  });

  it("rejects typed times that are off the configured grid", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));
    renderForm(
      <BookingForm
        mode="add"
        eventKind="BOOKING"
        initialTarget={{ ...target, slotGranularityMinutes: 10 }}
        initialDate="2026-08-17"
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    await user.type(within(start).getByLabelText("booking:bookings.form.time"), "09:07");
    await user.type(within(end).getByLabelText("booking:bookings.form.time"), "10:00");

    expect(screen.getByText("booking:bookings.errors.granularity")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));
    expect(submit).not.toHaveBeenCalled();
  });

  it("supports a compact dialog with an initial window, dirty state, and controlled cancellation", async () => {
    const user = userEvent.setup();
    const cancel = vi.fn();
    const stateChanged = vi.fn();
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));

    renderForm(
      <BookingForm
        mode="add"
        eventKind="BOOKING"
        density="compact"
        initialTarget={{ ...target, slotGranularityMinutes: 10 }}
        initialWindow={{
          startDate: "2026-08-17",
          startTime: "09:00",
          endDate: "2026-08-17",
          endTime: "10:00",
        }}
        token="token"
        pending={false}
        onCancel={cancel}
        onStateChange={stateChanged}
        onSubmit={vi.fn()}
      />,
    );

    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    expect(within(start).getByLabelText("booking:bookings.form.date")).toHaveValue("2026-08-17");
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveValue("09:00");
    expect(within(start).getByLabelText("booking:bookings.form.time")).toHaveAttribute("step", "600");
    expect(within(end).getByLabelText("booking:bookings.form.time")).toHaveValue("10:00");
    await waitFor(() => expect(stateChanged).toHaveBeenLastCalledWith(expect.objectContaining({ dirty: false })));

    await user.type(screen.getByRole("textbox", { name: "booking:bookings.form.purpose" }), "Imaging");
    await waitFor(() =>
      expect(stateChanged).toHaveBeenLastCalledWith(expect.objectContaining({ dirty: true, purpose: "Imaging" })),
    );
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.cancel" }));
    expect(cancel).toHaveBeenCalledOnce();
  });

  it("uses today in the selected item timezone when no Calendar date exists", async () => {
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));
    renderForm(
      <BookingForm
        mode="add"
        eventKind="BOOKING"
        initialTarget={target}
        token="token"
        pending={false}
        onSubmit={vi.fn()}
      />,
    );
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
                relationTo: "booking-instruments",
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
                relationTo: "booking-instruments",
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
        eventKind="BOOKING"
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
        eventKind="BOOKING"
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
        eventKind="BOOKING"
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

  it("warns about a past booking without blocking submission", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    const stateChanged = vi.fn();
    const pastEnd = new Date(Math.floor((Date.now() - 5 * 60_000) / (5 * 60_000)) * (5 * 60_000));
    const pastStart = new Date(pastEnd.getTime() - 60 * 60_000);
    const start = currentWallClock(pastStart.toISOString(), "UTC");
    const end = currentWallClock(pastEnd.toISOString(), "UTC");
    const clockTime = ({ minute }: { minute: number }) =>
      `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));

    renderForm(
      <BookingForm
        mode="add"
        displayTimezone="UTC"
        eventKind="BOOKING"
        initialTarget={target}
        initialWindow={{
          startDate: start.date,
          startTime: clockTime(start),
          endDate: end.date,
          endTime: clockTime(end),
        }}
        token="token"
        pending={false}
        onStateChange={stateChanged}
        onSubmit={submit}
      />,
    );

    await waitFor(() =>
      expect(stateChanged).toHaveBeenLastCalledWith(expect.objectContaining({ window: expect.anything() })),
    );
    expect(await screen.findByRole("status")).toHaveTextContent("booking:bookings.warnings.past");
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));
    expect(submit).toHaveBeenCalledOnce();
  });

  it("allows maintenance to span local dates", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    server.use(http.get("/api/v2/booking-configurations", () => HttpResponse.json(collectionPage([]))));

    renderForm(
      <BookingForm
        mode="add"
        displayTimezone="UTC"
        eventKind="MAINTENANCE"
        initialTarget={{ ...target, timezone: "UTC", openingStart: "08:00", openingEnd: "18:00" }}
        initialWindow={{
          startDate: "2026-08-17",
          startTime: "22:00",
          endDate: "2026-08-18",
          endTime: "02:00",
        }}
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    const submitButton = await screen.findByRole("button", { name: "booking:bookings.form.submitMaintenance" });
    expect(screen.queryByText("booking:bookings.errors.openingHours")).not.toBeInTheDocument();
    await user.click(submitButton);
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        eventKind: "MAINTENANCE",
        window: { start: "2026-08-17T22:00:00Z", end: "2026-08-18T02:00:00Z" },
      }),
    );
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
