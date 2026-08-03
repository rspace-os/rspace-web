import type { Meta, StoryObj } from "@storybook/react-vite";
import { within } from "storybook/test";
import { InboxIcon } from "lucide-react";
import { Button } from "./button";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "./empty";

const meta = {
  title: "DesignSystem/Empty",
  component: Empty,
  tags: ["autodocs"],
} satisfies Meta<typeof Empty>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Empty>
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <InboxIcon />
        </EmptyMedia>
        <EmptyTitle>No messages</EmptyTitle>
        <EmptyDescription>
          You don&apos;t have any messages yet. New messages will appear here.
        </EmptyDescription>
      </EmptyHeader>
      <EmptyContent>
        <Button>Compose message</Button>
      </EmptyContent>
    </Empty>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await canvas.findByText("No messages");
  },
};

export const MediaVariants: Story = {
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="default">
            <InboxIcon size={32} />
          </EmptyMedia>
          <EmptyTitle>Default media</EmptyTitle>
          <EmptyDescription>Renders the icon with no background.</EmptyDescription>
        </EmptyHeader>
      </Empty>
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <InboxIcon />
          </EmptyMedia>
          <EmptyTitle>Icon media</EmptyTitle>
          <EmptyDescription>
            Renders the icon inside a rounded, muted badge.
          </EmptyDescription>
        </EmptyHeader>
      </Empty>
    </div>
  ),
};
