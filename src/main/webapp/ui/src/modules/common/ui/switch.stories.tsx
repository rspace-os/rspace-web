import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { Switch } from "./switch";

const meta = {
  title: "DesignSystem/Switch",
  component: Switch,
  tags: ["autodocs"],
} satisfies Meta<typeof Switch>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    size: "default",
    "aria-label": "Airplane mode",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const switchEl = canvas.getByRole("switch");
    expect(switchEl).not.toBeChecked();
  },
};

export const Checked: Story = {
  args: {
    size: "default",
    defaultChecked: true,
    "aria-label": "Airplane mode",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const switchEl = canvas.getByRole("switch");
    expect(switchEl).toBeChecked();
  },
};

export const Disabled: Story = {
  args: {
    disabled: true,
    "aria-label": "Airplane mode",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const switchEl = canvas.getByRole("switch");
    expect(switchEl).toHaveAttribute("data-disabled");
  },
};

export const AllSizes: Story = {
  render: () => (
    <div style={{ display: "flex", gap: "16px", alignItems: "center" }}>
      <Switch size="sm" aria-label="Small switch" />
      <Switch size="default" aria-label="Default switch" />
    </div>
  ),
};
