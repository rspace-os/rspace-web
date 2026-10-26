import type { Meta, StoryObj } from "@storybook/tanstack-react";
import type { ComponentProps } from "react";
import { TableListCard } from "./TableListCardView";

const item = {
  id: "booking-41",
  title: "Confocal microscope · IN41",
  fields: [
    { id: "start", label: "Start", value: "Sep 24, 2026, 2:00 PM" },
    { id: "end", label: "End", value: "Sep 24, 2026, 4:00 PM" },
    { id: "purpose", label: "Purpose", value: "Live-cell imaging", fullWidth: true },
  ],
  actions: <span className="rounded-sm border px-3 py-1.5 text-sm">Booking details</span>,
} satisfies ComponentProps<typeof TableListCard>["item"];

const meta = {
  title: "Common/Table List/TableListCard",
  component: TableListCard,
  args: { item },
} satisfies Meta<typeof TableListCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { variant: "default" } };

export const Plain: Story = {
  args: { variant: "plain" },
  render: (args) => (
    <div className="max-w-md px-3 py-2">
      <TableListCard {...args} />
    </div>
  ),
};
