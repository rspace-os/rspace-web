/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Paper/Paper.stories.tsx
 * Upstream project license: MIT
 */
import Paper from "@mui/material/Paper";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { createBooleanArgType, createNumberArgType, createSelectArgType } from "../../argTypeTemplates";
import {
  Elevation as ElevationExample,
  SimplePaper as SimplePaperExample,
  Variants as VariantsExample,
} from "./examples";

const meta = {
  title: "Material UI/Surfaces/Paper",
  component: Paper,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: createSelectArgType(["elevation", "outlined"], "elevation", "The variant to use.", "Appearance"),
    elevation: createNumberArgType("Shadow depth, corresponds to dp in the spec.", 1, 0, 24, "Appearance"),
    square: createBooleanArgType("If true, rounded corners are disabled.", false, "Appearance"),
    children: { control: false },
  },
} satisfies Meta<typeof Paper>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    variant: "elevation",
    elevation: 1,
    square: false,
  },
  render: (args) => (
    <Paper {...args} sx={{ p: 2, width: 200 }}>
      Playground Paper
    </Paper>
  ),
};

export const Default: Story = {
  args: { children: "Paper surface", sx: { p: 2 } },
};

export const Elevation: Story = { render: () => <ElevationExample /> };
export const SimplePaper: Story = { render: () => <SimplePaperExample /> };
export const Variants: Story = { render: () => <VariantsExample /> };
