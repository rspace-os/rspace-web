import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Inputs/ToggleButton/Composition",
  component: ToggleButtonGroup,
  parameters: { a11y: { test: "error" } },
  argTypes: {
    size: { control: "select", options: ["small", "medium", "large"] },
    color: { control: "select", options: ["primary", "secondary", "standard"] },
    disabled: { control: "boolean" },
  },
} satisfies Meta<typeof ToggleButtonGroup>;
export default meta;
type Story = StoryObj<typeof meta>;

/** Exclusive selection uses ToggleButton values; clicking the selected button clears the selection. */
export const Composed: Story = {
  args: { size: "medium", color: "primary", disabled: false },
  render: function ViewSelection(args) {
    const [value, setValue] = useState<string | null>("list");
    return (
      <ToggleButtonGroup
        {...args}
        exclusive
        value={value}
        onChange={(_, next: string | null) => setValue(next)}
        aria-label="Sample view"
      >
        <ToggleButton value="list">List</ToggleButton>
        <ToggleButton value="grid">Grid</ToggleButton>
      </ToggleButtonGroup>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const list = canvas.getByRole("button", { name: "List" });
    const grid = canvas.getByRole("button", { name: "Grid" });
    await expect(list).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(grid);
    await expect(grid).toHaveAttribute("aria-pressed", "true");
    await expect(list).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(grid);
    await expect(grid).toHaveAttribute("aria-pressed", "false");
  },
};
