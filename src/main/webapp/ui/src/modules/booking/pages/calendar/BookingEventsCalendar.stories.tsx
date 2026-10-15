import type { Meta, StoryObj } from "@storybook/tanstack-react";
import * as React from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { bookingTarget, busyBooking, otherBooking, ownBooking } from "../mocks/bookingCalendarFixtures";
import { BookingEventsCalendar, type CalendarLayout, type CalendarView } from "./BookingEventsCalendar";

const calendarEventFallbackConfig = resolveCollectionConfig<BookingListDocument>({
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["target.name", "target.globalId", "purpose"],
  labels: {
    singularKey: "booking:calendar.event",
    pluralKey: "booking:calendar.title",
  },
  fields: [
    { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false, form: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "booking-instruments",
      hasMany: false,
      labelKey: "booking:calendar.fields.target",
      capabilities: { filterOperators: [] },
    },
    { name: "purpose", type: "text", maximumLength: 1_000, labelKey: "booking:calendar.fields.purpose" },
    {
      name: "bookedBy",
      type: "text",
      maximumLength: 255,
      labelKey: "booking:calendar.fields.bookedBy",
      capabilities: { filterOperators: [] },
    },
  ],
});

const storyEvents: readonly BookingListDocument[] = [
  ownBooking,
  { ...otherBooking, start: "2026-08-17T12:00:00Z", end: "2026-08-17T13:30:00Z" },
  { ...busyBooking, start: "2026-08-19T09:00:00Z", end: "2026-08-19T11:00:00Z" },
  {
    ...ownBooking,
    id: 44,
    target: bookingTarget(125, "Flow cytometer"),
    start: "2026-08-21T13:00:00Z",
    end: "2026-08-21T15:00:00Z",
    purpose: "Cell sorting",
  },
];

function InteractiveCalendar() {
  const [date, setDate] = React.useState("2026-08-17");
  const [view, setView] = React.useState<CalendarView>("week");
  const [layout, setLayout] = React.useState<CalendarLayout>("time-grid");
  const [search, setSearch] = React.useState("");
  const [mineOnly, setMineOnly] = React.useState(false);
  // The page asks the server to filter; the story filters its fixture so Search and My calendar visibly work.
  const events = storyEvents.filter(
    (event) =>
      (!mineOnly || event.requesterId === 1) &&
      [event.target?.value.name, event.purpose, event.bookedBy].some((value) =>
        value?.toLocaleLowerCase().includes(search.toLocaleLowerCase()),
      ),
  );
  return (
    <BookingEventsCalendar
      date={date}
      view={view}
      layout={layout}
      timezone="Europe/Berlin"
      events={events}
      eventFilterConfig={calendarEventFallbackConfig}
      searchControl={{ value: search, onChange: setSearch }}
      mineOnly={mineOnly}
      onMineChange={setMineOnly}
      isLoading={false}
      isError={false}
      onRetry={() => undefined}
      onDateChange={setDate}
      onControlsReset={() => {
        setDate(todayInTimeZone("Europe/Berlin"));
        setView("day");
        setLayout("resources");
        setSearch("");
        setMineOnly(false);
      }}
      onViewChange={setView}
      onLayoutChange={setLayout}
    />
  );
}

const meta = {
  title: "Booking/Calendar/Booking Events Calendar",
  component: InteractiveCalendar,
  parameters: {
    layout: "fullscreen",
    a11y: { config: { rules: [{ id: "landmark-unique", enabled: false }] } },
  },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof InteractiveCalendar>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Interactive: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const search = await canvas.findByRole("textbox", { name: "Search Calendar" });
    await userEvent.type(search, "Grace");
    // Search is debounced, as it is for the server-filtered page.
    await waitFor(() =>
      expect(canvas.queryByRole("button", { name: /Show details for Confocal microscope/ })).not.toBeInTheDocument(),
    );
    expect(canvas.getByRole("button", { name: /Show details for Electron microscope/ })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Clear search" }));
    expect(await canvas.findByRole("button", { name: /Show details for Confocal microscope/ })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Month" }));
    expect(canvas.getByRole("button", { name: "Month" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(canvas.getByRole("button", { name: "Resources" }));
    expect(canvas.getByRole("region", { name: "Resources" })).toBeVisible();
    expect(canvas.getByRole("button", { name: "Month" })).toBeDisabled();
    expect(canvas.getByRole("button", { name: "Week" })).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(canvas.getByRole("button", { name: "Day" }));
    expect(canvas.getAllByTestId("day-timeline-scroller")).toHaveLength(3);
    await userEvent.click(canvas.getByRole("button", { name: "My calendar" }));
    expect(canvas.getAllByTestId("day-timeline-scroller")).toHaveLength(2);
  },
};
