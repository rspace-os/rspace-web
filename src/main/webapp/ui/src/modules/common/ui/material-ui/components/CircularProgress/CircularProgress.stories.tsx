/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/CircularProgress/CircularProgress.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { createNumberArgType, createSelectArgType, muiColorArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/CircularProgress",
  component: CircularProgress,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },

  args: { "aria-label": "Loading samples" },
  argTypes: {
    color: muiColorArgType,
    size: {
      control: { type: "number", min: 16, max: 100 },
      description: "The size of the component.",
      table: {
        defaultValue: { summary: "40" },
        category: "Appearance",
        type: { summary: "number | string" },
      },
    },
    thickness: createNumberArgType("The thickness of the circle.", 3.6, 1, 10, "Appearance"),
    variant: createSelectArgType(
      ["determinate", "indeterminate"],
      "indeterminate",
      "The variant to use.",
      "Appearance",
    ),
    value: createNumberArgType(
      "The value of the progress indicator (0-100). Only works with determinate variant.",
      0,
      0,
      100,
      "Content",
    ),
    disableShrink: {
      control: "boolean",
      description: "If true, the shrink animation is disabled.",
      table: {
        defaultValue: { summary: "false" },
        category: "Appearance",
        type: { summary: "boolean" },
      },
    },
  },
} satisfies Meta<typeof CircularProgress>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    color: "primary",
    size: 40,
    thickness: 3.6,
    variant: "indeterminate",
    value: 0,
    disableShrink: false,
  },
};

export const Default: Story = {
  args: {},
};

export function Determinate() {
  return (
    <Box sx={{ display: "flex", gap: 2 }}>
      <CircularProgress aria-label="Loading samples" variant="determinate" value={25} />
      <CircularProgress aria-label="Loading samples" variant="determinate" value={50} />
      <CircularProgress aria-label="Loading samples" variant="determinate" value={75} />
      <CircularProgress aria-label="Loading samples" variant="determinate" value={100} />
    </Box>
  );
}

export function Colors() {
  return (
    <Box sx={{ display: "flex", gap: 2 }}>
      <CircularProgress aria-label="Loading samples" color="primary" />
      <CircularProgress aria-label="Loading samples" color="secondary" />
      <CircularProgress aria-label="Loading samples" color="success" />
      <CircularProgress aria-label="Loading samples" color="error" />
      <CircularProgress aria-label="Loading samples" color="warning" />
      <CircularProgress aria-label="Loading samples" color="info" />
    </Box>
  );
}

export function Sizes() {
  return (
    <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
      <CircularProgress aria-label="Loading samples" size={20} />
      <CircularProgress aria-label="Loading samples" size={30} />
      <CircularProgress aria-label="Loading samples" size={40} />
      <CircularProgress aria-label="Loading samples" size={60} />
    </Box>
  );
}
