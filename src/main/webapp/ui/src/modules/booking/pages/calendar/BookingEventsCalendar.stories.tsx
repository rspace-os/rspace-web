import type { Meta, StoryObj } from "@storybook/tanstack-react";
import * as React from "react";
import { expect, userEvent, within } from "storybook/test";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { BookingEventsCalendar, type CalendarLayout, type CalendarView } from "./BookingEventsCalendar";

const target = (id: number, name: string) => ({
  relationTo: "booking-instruments" as const,
  globalId: `IN${id}`,
  value: { id, name, deleted: false },
});

const timestamps = {
  kind: "BOOKING" as const,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
};
const storyEvents: readonly BookingListDocument[] = [
  {
    id: 41,
    target: target(123, "Confocal microscope"),
    canViewConfiguration: true,
    requesterId: 1,
    timezone: "Europe/Berlin",
    start: "2026-08-17T08:00:00Z",
    end: "2026-08-17T10:00:00Z",
    state: "CONFIRMED",
    purpose: "Cell imaging",
    bookedBy: "Ada Lovelace (ada)",
    privacy: "full",
    canEdit: true,
    ...timestamps,
  },
  {
    id: 42,
    target: target(124, "Electron microscope"),
    canViewConfiguration: true,
    requesterId: 2,
    timezone: "Europe/Berlin",
    start: "2026-08-17T12:00:00Z",
    end: "2026-08-17T13:30:00Z",
    state: "CONFIRMED",
    purpose: "Cryo-grid screening",
    bookedBy: "Grace Hopper (grace)",
    privacy: "full",
    canEdit: false,
    ...timestamps,
  },
  {
    id: 43,
    target: target(124, "Electron microscope"),
    canViewConfiguration: true,
    requesterId: 3,
    timezone: "Europe/Berlin",
    start: "2026-08-19T09:00:00Z",
    end: "2026-08-19T11:00:00Z",
    state: "CONFIRMED",
    purpose: null,
    bookedBy: null,
    privacy: "busy",
    canEdit: false,
    ...timestamps,
  },
  {
    id: 44,
    target: target(125, "Flow cytometer"),
    canViewConfiguration: true,
    requesterId: 1,
    timezone: "Europe/Berlin",
    start: "2026-08-21T13:00:00Z",
    end: "2026-08-21T15:00:00Z",
    state: "CONFIRMED",
    purpose: "Cell sorting",
    bookedBy: "Ada Lovelace (ada)",
    privacy: "full",
    canEdit: true,
    ...timestamps,
  },
];

function InteractiveCalendar() {
  const [date, setDate] = React.useState("2026-08-17");
  const [view, setView] = React.useState<CalendarView>("week");
  const [layout, setLayout] = React.useState<CalendarLayout>("time-grid");
  return (
    <BookingEventsCalendar
      date={date}
      view={view}
      layout={layout}
      timezone="Europe/Berlin"
      events={storyEvents}
      currentUserId={1}
      isLoading={false}
      isError={false}
      onRetry={() => undefined}
      onDateChange={setDate}
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
    expect(canvas.queryByRole("button", { name: /Show details for Confocal microscope/ })).not.toBeInTheDocument();
    expect(canvas.getByRole("button", { name: /Show details for Electron microscope/ })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Clear search" }));
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
