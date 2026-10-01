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
import { afterEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import { currentWallClock } from "@/modules/booking/domain/bookingTime";
import { BookingForm, type BookingFormSubmission } from "../BookingForm";
import { cataloguePage, editableBooking, confocalTarget as target } from "./creationTestFixtures";

it("shows every independently configured weekday and the scheduling timezone", async () => {
  server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
  renderForm(
    <BookingForm
      mode="add"
      token="token"
      pending={false}
      eventKind="BOOKING"
      initialTarget={{
        ...target,
        openingHours: [
          { dayOfWeek: 1, start: "08:00", end: "17:00" },
          { dayOfWeek: 2, start: "10:00", end: "16:00" },
        ],
      }}
      initialDate="2026-08-17"
      onSubmit={vi.fn(async () => undefined)}
    />,
  );
  expect(await screen.findByText("08:00–17:00")).toBeVisible();
  expect(screen.getByText("10:00–16:00")).toBeVisible();
  expect(screen.getAllByText("booking:bookableItemDetails.openingHours.closed")).toHaveLength(5);
  expect(screen.getByText(target.timezone)).toBeVisible();
});

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
  afterEach(() => {
    vi.useRealTimers();
  });

  async function submitBookingWithStartTime(startTime: string) {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-17T12:00:00Z"));
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
    renderForm(
      <BookingForm
        mode="add"
        displayTimezone="UTC"
        eventKind="BOOKING"
        initialTarget={target}
        initialWindow={{
          startDate: "2026-08-17",
          startTime,
          endDate: "2026-08-17",
          endTime: "13:00",
        }}
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "booking:bookings.form.submit" }));
    return submit;
  }

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
    expect(screen.getByText("booking:bookings.form.openingHours")).toBeVisible();
    expect(screen.queryByText("booking:bookings.form.openingHoursDifferentTimezone")).not.toBeInTheDocument();
    expect(screen.getByText("booking:bookings.form.timezone")).toBeVisible();
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

  it("submits the inline form from an external button without rendering a footer", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();

    renderForm(
      <>
        <button type="submit" form="inline-booking">
          {"Save externally"}
        </button>
        <BookingForm
          mode="edit"
          layout="inline"
          formId="inline-booking"
          booking={editableBooking}
          configuration={target}
          token="token"
          pending={false}
          onSubmit={submit}
        />
      </>,
    );

    expect(await screen.findByRole("button", { name: "Save externally" })).toHaveAttribute("form", "inline-booking");
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookings.form.save" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookings.form.cancel" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save externally" }));

    expect(submit).toHaveBeenCalledOnce();
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
    expect(screen.getByText("booking:bookings.form.openingHoursDifferentTimezone")).toBeVisible();
    expect(screen.getByText("booking:bookings.form.timezone")).toBeVisible();
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
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));

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
    const startTime = within(start).getByLabelText("booking:bookings.form.time");
    expect(startTime).toHaveAttribute("aria-invalid", "true");
    expect(startTime).toHaveAccessibleDescription("booking:bookings.errors.windowRequired");
    await waitFor(() => expect(startTime).toHaveFocus());
    expect(submit).not.toHaveBeenCalled();
  });

  it.each([
    [1, "60"],
    [15, "900"],
  ])("uses the configured %i-minute increment", async (increment, step) => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
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

  it.each(["add", "edit"] as const)("snaps typed %s-form times to the nearest configured interval", async (mode) => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
    const form =
      mode === "add" ? (
        <BookingForm
          mode="add"
          eventKind="BOOKING"
          initialTarget={{ ...target, slotGranularityMinutes: 10 }}
          initialDate="2026-08-17"
          token="token"
          pending={false}
          onSubmit={submit}
        />
      ) : (
        <BookingForm
          mode="edit"
          booking={{
            ...editableBooking,
            start: "2026-08-17T07:00:00Z",
            end: "2026-08-17T08:00:00Z",
          }}
          configuration={{ ...target, slotGranularityMinutes: 10 }}
          token="token"
          pending={false}
          onSubmit={submit}
        />
      );
    renderForm(form);

    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    const startTime = within(start).getByLabelText("booking:bookings.form.time");
    const endTime = within(end).getByLabelText("booking:bookings.form.time");
    if (mode === "edit") {
      await user.clear(startTime);
      await user.clear(endTime);
    }
    await user.type(startTime, "09:04");
    await user.tab();
    expect(startTime).toHaveValue("09:00");
    await user.type(endTime, "10:06");
    await user.tab();
    expect(endTime).toHaveValue("10:10");

    await user.click(
      screen.getByRole("button", {
        name: mode === "add" ? "booking:bookings.form.submit" : "booking:bookings.form.save",
      }),
    );
    expect(screen.queryByText("booking:bookings.errors.granularity")).not.toBeInTheDocument();
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        window: { start: "2026-08-17T07:00:00Z", end: "2026-08-17T08:10:00Z" },
      }),
    );
  });

  it("supports a compact dialog with an initial window, dirty state, and controlled cancellation", async () => {
    const user = userEvent.setup();
    const cancel = vi.fn();
    const stateChanged = vi.fn();
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));

    renderForm(
      <BookingForm
        mode="add"
        eventKind="BOOKING"
        density="compact"
        initialTarget={{ ...target, slotGranularityMinutes: 10 }}
        lockTarget
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

    expect(await screen.findByLabelText("booking:bookings.form.date")).toHaveValue("2026-08-17");
    expect(screen.queryByText("booking:bookings.form.item")).not.toBeInTheDocument();
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();
    const startTime = screen.getByLabelText("booking:bookings.form.startTime");
    const endTime = screen.getByLabelText("booking:bookings.form.endTime");
    expect(startTime).toHaveValue("09:00");
    expect(startTime).toHaveAttribute("step", "600");
    expect(endTime).toHaveValue("10:00");
    await waitFor(() => expect(stateChanged).toHaveBeenLastCalledWith(expect.objectContaining({ dirty: false })));

    await user.clear(startTime);
    await user.type(startTime, "09:04");
    await user.tab();
    expect(startTime).toHaveValue("09:00");
    await user.clear(endTime);
    await user.type(endTime, "10:06");
    await user.tab();
    expect(endTime).toHaveValue("10:10");
    await user.type(screen.getByRole("textbox", { name: "booking:bookings.form.purpose" }), "Imaging");
    await waitFor(() =>
      expect(stateChanged).toHaveBeenLastCalledWith(expect.objectContaining({ dirty: true, purpose: "Imaging" })),
    );
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.cancel" }));
    expect(cancel).toHaveBeenCalledOnce();
  });

  it("uses today in the selected item timezone when no Calendar date exists", async () => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
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
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([target, paris]))));
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
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));
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
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
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
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));
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

    await user.click(screen.getByRole("button", { name: "booking:bookings.form.submit" }));
    expect(screen.getByText("booking:bookings.errors.maximumDuration")).toBeVisible();
    expect(submit).not.toHaveBeenCalled();
  });

  it("warns about a past booking without blocking submission", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-08-17T12:00:00Z"));
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    const stateChanged = vi.fn();
    const pastStart = new Date(Date.now() - 60 * 60_000);
    const futureEnd = new Date(Date.now() + 30 * 60_000);
    const start = currentWallClock(pastStart.toISOString(), "UTC");
    const end = currentWallClock(futureEnd.toISOString(), "UTC");
    const clockTime = ({ minute }: { minute: number }) =>
      `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));

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

  it("warns when a booking starts exactly now without blocking submission", async () => {
    const submit = await submitBookingWithStartTime("12:00");

    expect(screen.getByRole("status")).toHaveTextContent("booking:bookings.warnings.past");
    expect(submit).toHaveBeenCalledOnce();
  });

  it("does not warn when a booking starts strictly in the future", async () => {
    const submit = await submitBookingWithStartTime("12:05");

    expect(screen.queryByText("booking:bookings.warnings.past")).not.toBeInTheDocument();
    expect(submit).toHaveBeenCalledOnce();
  });

  it("shows save errors and guidance when the outcome is uncertain", async () => {
    renderForm(
      <BookingForm
        mode="edit"
        booking={editableBooking}
        configuration={target}
        token="token"
        pending={false}
        error="booking:bookings.errors.outcomeUncertain"
        outcomeUncertain
        onSubmit={vi.fn()}
      />,
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("booking:bookings.errors.outcomeUncertain");
    expect(alert).toHaveTextContent("booking:bookings.errors.outcomeUncertainGuidance");
    expect(screen.getByRole("link", { name: "booking:bookings.errors.checkExistingBookings" })).toHaveAttribute(
      "href",
      "/booking/my-bookings?period=upcoming",
    );
  });

  it("allows maintenance to span local dates", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(cataloguePage([]))));

    renderForm(
      <BookingForm
        mode="add"
        displayTimezone="UTC"
        eventKind="MAINTENANCE"
        initialTarget={{
          ...target,
          timezone: "UTC",
          openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
        }}
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

  it("preserves off-grid original endpoints during a purpose-only edit", async () => {
    const user = userEvent.setup();
    const submit = vi.fn<(submission: BookingFormSubmission) => Promise<void>>().mockResolvedValue();
    renderForm(
      <BookingForm
        mode="edit"
        booking={{
          ...editableBooking,
          start: "2026-08-17T07:02:00Z",
          end: "2026-08-17T08:02:00Z",
        }}
        configuration={{ ...target, slotGranularityMinutes: 10 }}
        token="token"
        pending={false}
        onSubmit={submit}
      />,
    );

    const start = await screen.findByRole("group", { name: "booking:bookings.form.start" });
    const end = screen.getByRole("group", { name: "booking:bookings.form.end" });
    const startTime = within(start).getByLabelText("booking:bookings.form.time");
    const endTime = within(end).getByLabelText("booking:bookings.form.time");
    await user.click(startTime);
    await user.tab();
    expect(startTime).toHaveValue("09:02");
    await user.click(endTime);
    await user.tab();
    expect(endTime).toHaveValue("10:02");

    await user.type(screen.getByRole("textbox", { name: "booking:bookings.form.purpose" }), " updated");
    await user.click(screen.getByRole("button", { name: "booking:bookings.form.save" }));

    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({
        window: { start: "2026-08-17T07:02:00Z", end: "2026-08-17T08:02:00Z" },
        purpose: "Imaging updated",
      }),
    );
  });
});
