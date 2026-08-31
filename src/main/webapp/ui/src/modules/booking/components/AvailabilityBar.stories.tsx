import type { Meta, StoryObj } from "@storybook/tanstack-react";
import type { SourcedAvailabilityInterval } from "@/modules/booking/domain/availability";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { AvailabilityBar } from "./AvailabilityBar";

const periodStart = new Date("2026-08-12T00:00:00.000Z");

const sourced = (
  id: string,
  kind: SourcedAvailabilityInterval["kind"],
  startsAt: string,
  endsAt: string,
): SourcedAvailabilityInterval => ({
  kind,
  startsAt: new Date(startsAt),
  endsAt: new Date(endsAt),
  source: { id, startsAt: new Date(startsAt), endsAt: new Date(endsAt) },
});

const typicalIntervals: Array<SourcedAvailabilityInterval> = [
  sourced("booking:1", "booking", "2026-08-12T08:30:00.000Z", "2026-08-12T10:00:00.000Z"),
  sourced("booking:2", "booking", "2026-08-12T09:45:00.000Z", "2026-08-12T11:30:00.000Z"),
  sourced("opening-hours:1", "blockout", "2026-08-12T14:00:00.000Z", "2026-08-12T15:30:00.000Z"),
];

const meta = {
  title: "Booking/AvailabilityBar",
  component: AvailabilityBar,
  tags: ["autodocs"],
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking"]}>
        <div className="p-8">
          <Story />
        </div>
      </I18nRoot>
    ),
  ],
  args: {
    itemName: "Zeiss LSM 900 confocal",
    intervals: typicalIntervals,
    periodStart,
    timeZone: "UTC",
    userTimeZone: "UTC",
  },
} satisfies Meta<typeof AvailabilityBar>;

export default meta;

type Story = StoryObj<typeof meta>;

export const TypicalDay: Story = {};

export const WithPeriodLabels: Story = {
  args: {
    periodStart: new Date("2026-08-12T08:00:00.000Z"),
    periodEnd: new Date("2026-08-12T18:00:00.000Z"),
    showPeriodLabels: true,
  },
};

export const FullyAvailable: Story = {
  args: { intervals: [] },
};

export const WithCurrentTime: Story = {
  args: {
    now: new Date("2026-08-12T10:30:00.000Z"),
    showCurrentAvailability: true,
  },
};

export const DenseAndOverlapping: Story = {
  args: {
    intervals: [
      ...typicalIntervals,
      sourced("booking:3", "booking", "2026-08-12T11:30:00.000Z", "2026-08-12T16:00:00.000Z"),
      sourced("opening-hours:2", "blockout", "2026-08-12T10:30:00.000Z", "2026-08-12T13:00:00.000Z"),
    ],
  },
};

export const ThreeDayPeriod: Story = {
  args: {
    periodEnd: new Date("2026-08-15T00:00:00.000Z"),
    intervals: [
      ...typicalIntervals,
      sourced("booking:4", "booking", "2026-08-14T08:00:00.000Z", "2026-08-14T18:00:00.000Z"),
    ],
  },
};

export const NarrowItemCard: Story = {
  render: (args) => (
    <div className="w-64 space-y-3 rounded-2xl border p-4">
      <div>
        <p className="truncate text-sm font-medium">Zeiss LSM 900 confocal</p>
        <p className="text-xs text-muted-foreground">Lab 2.14</p>
      </div>
      <AvailabilityBar {...args} />
    </div>
  ),
};

export const SourceFreeCompatibility: Story = {
  args: {
    intervals: typicalIntervals.map(({ kind, startsAt, endsAt }) => ({ kind, startsAt, endsAt })),
  },
};
