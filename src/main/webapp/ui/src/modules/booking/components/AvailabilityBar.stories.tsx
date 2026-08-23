import type { Meta, StoryObj } from "@storybook/tanstack-react";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { AvailabilityBar, type AvailabilityInterval } from "./AvailabilityBar";

const periodStart = new Date("2026-08-12T00:00:00.000Z");

const typicalIntervals: Array<AvailabilityInterval> = [
  {
    kind: "booking",
    startsAt: new Date("2026-08-12T08:30:00.000Z"),
    endsAt: new Date("2026-08-12T10:00:00.000Z"),
  },
  {
    kind: "booking",
    startsAt: new Date("2026-08-12T09:45:00.000Z"),
    endsAt: new Date("2026-08-12T11:30:00.000Z"),
  },
  {
    kind: "blockout",
    startsAt: new Date("2026-08-12T14:00:00.000Z"),
    endsAt: new Date("2026-08-12T15:30:00.000Z"),
  },
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
  },
} satisfies Meta<typeof AvailabilityBar>;

export default meta;

type Story = StoryObj<typeof meta>;

export const TypicalDay: Story = {};

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
      {
        kind: "booking",
        startsAt: new Date("2026-08-12T11:30:00.000Z"),
        endsAt: new Date("2026-08-12T16:00:00.000Z"),
      },
      {
        kind: "blockout",
        startsAt: new Date("2026-08-12T10:30:00.000Z"),
        endsAt: new Date("2026-08-12T13:00:00.000Z"),
      },
    ],
  },
};

export const ThreeDayPeriod: Story = {
  args: {
    periodEnd: new Date("2026-08-15T00:00:00.000Z"),
    intervals: [
      ...typicalIntervals,
      {
        kind: "booking",
        startsAt: new Date("2026-08-14T08:00:00.000Z"),
        endsAt: new Date("2026-08-14T18:00:00.000Z"),
      },
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
