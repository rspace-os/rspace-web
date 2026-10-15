import type { Meta, StoryObj } from "@storybook/tanstack-react";
import * as React from "react";
import { expect, userEvent, within } from "storybook/test";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { busyBooking, otherBooking, ownBooking } from "../mocks/bookingCalendarFixtures";
import { BookingEventsCalendar, type CalendarLayout, type CalendarView } from "./BookingEventsCalendar";

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
      events={[ownBooking, otherBooking, busyBooking]}
      currentUserId={1}
      isLoading={false}
      isError={false}
      onRetry={() => undefined}
      onDateChange={setDate}
      onControlsReset={() => {
        setDate(todayInTimeZone("Europe/Berlin"));
        setView("day");
        setLayout("resources");
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
