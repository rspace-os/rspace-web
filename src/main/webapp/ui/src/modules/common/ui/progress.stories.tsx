import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { Progress, ProgressLabel, ProgressValue } from "./progress";

const meta = {
  title: "DesignSystem/Progress",
  component: Progress,
  tags: ["autodocs"],
} satisfies Meta<typeof Progress>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    value: 50,
  },
  render: (args) => (
    <div style={{ width: "300px" }}>
      <Progress {...args}>
        <ProgressLabel>Uploading files</ProgressLabel>
        <ProgressValue />
      </Progress>
    </div>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const bar = canvas.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "50");
  },
};

export const AllStates: Story = {
  args: {
    value: null,
  },
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "24px", width: "300px" }}>
      <Progress value={0}>
        <ProgressLabel>Not started</ProgressLabel>
        <ProgressValue />
      </Progress>
      <Progress value={50}>
        <ProgressLabel>In progress</ProgressLabel>
        <ProgressValue />
      </Progress>
      <Progress value={100}>
        <ProgressLabel>Complete</ProgressLabel>
        <ProgressValue />
      </Progress>
      <Progress value={null}>
        <ProgressLabel>Indeterminate</ProgressLabel>
        <ProgressValue />
      </Progress>
    </div>
  ),
};
