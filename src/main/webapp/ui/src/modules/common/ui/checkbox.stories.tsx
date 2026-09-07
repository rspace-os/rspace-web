import type { Meta, StoryObj } from "@storybook/tanstack-react";
import * as React from "react";
import { expect, userEvent, within } from "storybook/test";
import { Checkbox } from "./checkbox";

const meta = {
  title: "DesignSystem/Checkbox",
  component: Checkbox,
  tags: ["autodocs"],
} satisfies Meta<typeof Checkbox>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <label htmlFor="terms" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
      <Checkbox id="terms" defaultChecked />
      Accept terms and conditions
    </label>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const checkbox = canvas.getByRole("checkbox");
    expect(checkbox).toHaveAttribute("aria-checked", "true");
    await userEvent.click(checkbox);
    expect(checkbox).toHaveAttribute("aria-checked", "false");
  },
};

export const Controlled: Story = {
  render: () => {
    const [checked, setChecked] = React.useState(false);
    return (
      <label htmlFor="subscription" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
        <Checkbox id="subscription" checked={checked} onCheckedChange={(value) => setChecked(value)} />
        {checked ? "Subscribed" : "Not subscribed"}
      </label>
    );
  },
};

export const Disabled: Story = {
  render: () => (
    <label
      htmlFor="disabled-option"
      style={{
        display: "flex",
        alignItems: "center",
        gap: "8px",
        opacity: 0.5,
      }}
    >
      <Checkbox id="disabled-option" disabled />
      Disabled option
    </label>
  ),
};

export const Indeterminate: Story = {
  render: () => (
    <label htmlFor="select-all" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
      <Checkbox id="select-all" indeterminate />
      Select all
    </label>
  ),
};
