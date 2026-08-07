import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import { Input } from "./input";
import { Label } from "./label";

const meta = {
  title: "DesignSystem/Label",
  component: Label,
  tags: ["autodocs"],
} satisfies Meta<typeof Label>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "8px", width: "260px" }}>
      <Label htmlFor="label-story-email">Email</Label>
      <Input id="label-story-email" placeholder="you@example.com" />
    </div>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const label = canvas.getByText("Email");
    const input = canvas.getByPlaceholderText("you@example.com");
    await userEvent.click(label);
    expect(input).toHaveFocus();
  },
};

export const DisabledGroup: Story = {
  render: () => (
    <div
      className="group"
      data-disabled="true"
      style={{ display: "flex", flexDirection: "column", gap: "8px", width: "260px" }}
    >
      <Label>Disabled field</Label>
      <Input disabled placeholder="Can't type here" />
    </div>
  ),
};
