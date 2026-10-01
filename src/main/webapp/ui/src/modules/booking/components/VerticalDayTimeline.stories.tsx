import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { VerticalDayTimeline, type VerticalDayTimelineProps } from "./VerticalDayTimeline";
import { VERTICAL_DAY_TIMELINE_EVENTS, VerticalDayTimelineStory } from "./VerticalDayTimeline.story";

const meta = {
  title: "Booking/VerticalDayTimeline",
  component: VerticalDayTimeline,
  parameters: { layout: "padded" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking"]}>
        <div className="mx-auto w-full max-w-xl bg-background p-4 text-foreground">
          <Story />
        </div>
      </I18nRoot>
    ),
  ],
  args: {
    date: "2026-10-25",
    onDateChange: () => {},
    timezone: "Europe/Berlin",
    itemName: "Confocal microscope",
    events: VERTICAL_DAY_TIMELINE_EVENTS,
    schedule: { status: "success" },
    scheduleWindow: {
      timezone: "Europe/Berlin",
      openingStart: "08:00",
      openingEnd: "18:00",
      openDays: ALL_ISO_WEEKDAYS,
      openingExceptions: [],
    },
  } satisfies Partial<VerticalDayTimelineProps>,
} satisfies Meta<typeof VerticalDayTimeline>;

export default meta;
type Story = StoryObj<typeof meta>;

export const EditableDraft: Story = {
  render: () => <VerticalDayTimelineStory />,
};

export const DayWithOverlaps: Story = {
  render: () => (
    <VerticalDayTimelineStory
      initialDraft={{
        startDate: "2026-10-25",
        startTime: "09:00",
        endDate: "2026-10-25",
        endTime: "10:00",
      }}
    />
  ),
};

export const ReadOnly: Story = {};

export const Loading: Story = {
  args: { schedule: { status: "loading" } },
};

export const ScheduleError: Story = {
  args: { schedule: { status: "error", onRetry: () => {} } },
};

export const NoTarget: Story = {
  args: { events: [], schedule: { status: "no-target" }, scheduleWindow: undefined },
};
