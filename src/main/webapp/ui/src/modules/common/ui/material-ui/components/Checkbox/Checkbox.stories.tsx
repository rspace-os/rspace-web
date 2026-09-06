/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Checkbox/Checkbox.stories.tsx
 * Upstream project license: MIT
 */

import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  muiCheckedArgType,
  muiColorArgType,
  muiDisabledArgType,
  muiRequiredArgType,
  muiSizeArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/Checkbox",
  component: Checkbox,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: muiColorArgType,
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    required: muiRequiredArgType,
    checked: muiCheckedArgType,
    defaultChecked: createBooleanArgType(
      "The default checked state. Use when the component is not controlled.",
      false,
      "State",
    ),
    indeterminate: createBooleanArgType("If true, the component appears indeterminate.", false, "State"),
    disableRipple: createBooleanArgType("If true, the ripple effect is disabled.", false, "Appearance"),
  },
} satisfies Meta<typeof Checkbox>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  render: (args) => (
    <Checkbox
      {...args}
      slotProps={{
        input: {
          "aria-label": "Checkbox control",
          ref: (input: HTMLInputElement | null) => {
            if (input) input.indeterminate = Boolean(args.indeterminate);
          },
        },
      }}
    />
  ),
  args: {
    color: "primary",
    size: "medium",
    disabled: false,
    defaultChecked: false,
    indeterminate: false,
    slotProps: { input: { "aria-label": "Checkbox control" } },
  },
};

export const Default: Story = {
  args: {
    slotProps: { input: { "aria-label": "Default checkbox" } },
  },
};

export const Colors: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "8px" }}>
      <Checkbox defaultChecked color="primary" slotProps={{ input: { "aria-label": "Primary checkbox" } }} />
      <Checkbox defaultChecked color="secondary" slotProps={{ input: { "aria-label": "Secondary checkbox" } }} />
      <Checkbox defaultChecked color="success" slotProps={{ input: { "aria-label": "Success checkbox" } }} />
      <Checkbox defaultChecked color="error" slotProps={{ input: { "aria-label": "Error checkbox" } }} />
      <Checkbox defaultChecked color="info" slotProps={{ input: { "aria-label": "Info checkbox" } }} />
      <Checkbox defaultChecked color="warning" slotProps={{ input: { "aria-label": "Warning checkbox" } }} />
    </div>
  ),
};

export const Sizes: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
      <Checkbox defaultChecked size="small" slotProps={{ input: { "aria-label": "Small checkbox" } }} />
      <Checkbox defaultChecked size="medium" slotProps={{ input: { "aria-label": "Medium checkbox" } }} />
    </div>
  ),
};

export const Indeterminate: Story = {
  args: {
    indeterminate: true,
    slotProps: {
      input: {
        "aria-label": "Indeterminate checkbox",
        ref: (input: HTMLInputElement | null) => {
          if (input) input.indeterminate = true;
        },
      },
    },
  },
};

export const Disabled: Story = {
  args: {
    disabled: true,
    defaultChecked: true,
    slotProps: { input: { "aria-label": "Disabled checkbox" } },
  },
};

export const ToggleInteraction: Story = {
  args: {
    defaultChecked: false,
    slotProps: { input: { "aria-label": "Toggle interaction checkbox" } },
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const checkbox = canvas.getByRole("checkbox");

    await expect(checkbox).toBeInTheDocument();
    await expect(checkbox).not.toBeChecked();

    await userEvent.click(checkbox);
    await expect(checkbox).toBeChecked();

    await userEvent.click(checkbox);
    await expect(checkbox).not.toBeChecked();
  },
};

/** A visible label also toggles the controlled checkbox. */
export const Controlled: Story = {
  render: function ControlledCheckbox() {
    const [checked, setChecked] = useState(false);
    return (
      <FormControlLabel
        label="Include archived samples"
        control={<Checkbox checked={checked} onChange={(event) => setChecked(event.target.checked)} />}
      />
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByText("Include archived samples"));
    await expect(canvas.getByRole("checkbox", { name: "Include archived samples" })).toBeChecked();
  },
};
