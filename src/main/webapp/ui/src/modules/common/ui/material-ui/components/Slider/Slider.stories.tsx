/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Slider/Slider.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Slider from "@mui/material/Slider";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  createSelectArgType,
  muiColorArgType,
  muiDisabledArgType,
  muiOrientationArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/Slider",
  component: Slider,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: muiColorArgType,
    size: {
      control: "radio",
      options: ["small", "medium"],
      description: "The size of the slider.",
      table: {
        defaultValue: { summary: "medium" },
        category: "Appearance",
        type: { summary: '"small" | "medium"' },
      },
    },
    disabled: muiDisabledArgType,
    orientation: muiOrientationArgType,
    valueLabelDisplay: createSelectArgType(
      ["auto", "on", "off"],
      "off",
      "Controls when the value label is displayed.",
      "Appearance",
    ),
    track: createSelectArgType(["normal", "inverted", false], "normal", "The track presentation.", "Appearance"),
    marks: createBooleanArgType(
      "Marks indicate predetermined values to which the user can move the slider.",
      false,
      "Appearance",
    ),
    step: {
      control: { type: "number", min: 1, max: 100 },
      description: "The granularity with which the slider can step through values.",
      table: {
        defaultValue: { summary: "1" },
        category: "Content",
        type: { summary: "number | null" },
      },
    },
    min: {
      control: { type: "number" },
      description: "The minimum allowed value of the slider.",
      table: {
        defaultValue: { summary: "0" },
        category: "Content",
        type: { summary: "number" },
      },
    },
    max: {
      control: { type: "number" },
      description: "The maximum allowed value of the slider.",
      table: {
        defaultValue: { summary: "100" },
        category: "Content",
        type: { summary: "number" },
      },
    },
    defaultValue: {
      control: { type: "number", min: 0, max: 100 },
      description: "The default value. Use when the component is not controlled.",
      table: { category: "Content" },
    },
  },
} satisfies Meta<typeof Slider>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    defaultValue: 50,
    "aria-label": "Playground slider",
    color: "primary",
    size: "medium",
    disabled: false,
    valueLabelDisplay: "auto",
    min: 0,
    max: 100,
    step: 1,
  },
};

export const Default: Story = {
  args: {
    defaultValue: 30,
    "aria-label": "Default slider",
  },
};

export const InteractionTest: Story = {
  args: {
    defaultValue: 50,
    "aria-label": "Test slider",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const slider = canvas.getByRole("slider");

    await expect(slider).toBeInTheDocument();
    await expect(slider).toHaveAttribute("aria-valuenow", "50");
    slider.focus();
    await userEvent.keyboard("{ArrowRight}");
    await expect(slider).toHaveAttribute("aria-valuenow", "51");
  },
};

export function Continuous() {
  const [value, setValue] = React.useState<number>(30);

  return (
    <Box sx={{ width: 300 }}>
      <Typography gutterBottom>Volume: {value}</Typography>
      <Slider value={value} onChange={(_, newValue) => setValue(newValue as number)} aria-label="Volume" />
    </Box>
  );
}

export function Range() {
  const [value, setValue] = React.useState<number[]>([20, 37]);

  return (
    <Box sx={{ width: 300 }}>
      <Typography gutterBottom>
        Range: {value[0]} - {value[1]}
      </Typography>
      <Slider
        value={value}
        onChange={(_, newValue) => setValue(newValue as number[])}
        valueLabelDisplay="auto"
        aria-label="Range slider"
      />
    </Box>
  );
}

export function DiscreteWithMarks() {
  const marks = [
    { value: 0, label: "0°C" },
    { value: 20, label: "20°C" },
    { value: 37, label: "37°C" },
    { value: 100, label: "100°C" },
  ];

  return (
    <Box sx={{ width: 300 }}>
      <Slider defaultValue={37} step={10} marks={marks} valueLabelDisplay="auto" aria-label="Temperature" />
    </Box>
  );
}

export function Vertical() {
  return (
    <Box sx={{ height: 300 }}>
      <Stack spacing={1} direction="row" sx={{ height: "100%" }}>
        <Slider orientation="vertical" defaultValue={30} aria-label="Vertical slider" />
        <Slider orientation="vertical" defaultValue={[20, 37]} aria-label="Vertical range slider" />
      </Stack>
    </Box>
  );
}

export function Colors() {
  return (
    <Stack spacing={2} sx={{ width: 300 }}>
      <Slider defaultValue={30} color="primary" aria-label="primary slider" />
      <Slider defaultValue={30} color="secondary" aria-label="secondary slider" />
      <Slider defaultValue={30} color="error" aria-label="error slider" />
    </Stack>
  );
}

export function Sizes() {
  return (
    <Stack spacing={2} sx={{ width: 300 }}>
      <Slider size="small" defaultValue={30} aria-label="Small slider" />
      <Slider defaultValue={30} aria-label="Default slider" />
    </Stack>
  );
}
