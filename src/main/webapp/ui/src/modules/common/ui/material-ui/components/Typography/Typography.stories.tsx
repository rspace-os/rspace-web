/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Typography/Typography.stories.tsx
 * Upstream project license: MIT
 */

import Typography, { type TypographyProps } from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createBooleanArgType, muiAlignArgType, muiTypographyVariantArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/Typography",
  component: Typography,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: muiTypographyVariantArgType,
    align: muiAlignArgType,
    color: {
      control: "select",
      options: ["initial", "inherit", "primary", "secondary", "textPrimary", "textSecondary", "error"],
      description: "The color of the component.",
      table: {
        defaultValue: { summary: "inherit" },
        category: "Appearance",
      },
    },
    gutterBottom: createBooleanArgType("If true, the text will have a bottom margin.", false, "Layout"),
    noWrap: createBooleanArgType("If true, the text will not wrap.", false, "Layout"),
    children: {
      control: "text",
      description: "The content of the component.",
      table: { category: "Content" },
    },
  },
} satisfies Meta<typeof Typography>;

export default meta;
type Story = StoryObj<TypographyProps>;

export const Playground: Story = {
  args: {
    children: "Typography Text",
    variant: "body1",
    align: "inherit",
    color: "initial",
    gutterBottom: false,
    noWrap: false,
  },
};

export const Default: Story = {
  args: {
    children: "Default Typography",
  },
};

export const AllVariants: Story = {
  render: () => (
    <>
      <Typography variant="h1">Heading 1</Typography>
      <Typography variant="h2">Heading 2</Typography>
      <Typography variant="h3">Heading 3</Typography>
      <Typography variant="h4">Heading 4</Typography>
      <Typography variant="h5">Heading 5</Typography>
      <Typography variant="h6">Heading 6</Typography>
      <Typography variant="subtitle1">Subtitle 1</Typography>
      <Typography variant="subtitle2">Subtitle 2</Typography>
      <Typography variant="body1">Body 1</Typography>
      <Typography variant="body2">Body 2</Typography>
      <Typography variant="button">Button</Typography>
      <Typography variant="caption">Caption</Typography>
      <Typography variant="overline">Overline</Typography>
    </>
  ),
};

export const Colors: Story = {
  render: () => (
    <>
      <Typography color="primary">Primary Color</Typography>
      <Typography color="secondary">Secondary Color</Typography>
      <Typography color="textPrimary">Text Primary</Typography>
      <Typography color="textSecondary">Text Secondary</Typography>
      <Typography color="error">Error Color</Typography>
    </>
  ),
};

export const InteractionTest: Story = {
  render: () => (
    <>
      <Typography variant="h1">Test Heading</Typography>
      <Typography variant="body1">Test body text content</Typography>
      <Typography color="primary">Primary colored text</Typography>
    </>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify heading renders", async () => {
      const heading = canvas.getByText("Test Heading");
      await expect(heading).toBeInTheDocument();
      await expect(heading.tagName).toBe("H1");
    });

    await step("Verify body text renders", async () => {
      const body = canvas.getByText("Test body text content");
      await expect(body).toBeInTheDocument();
    });

    await step("Verify colored text renders", async () => {
      const coloredText = canvas.getByText("Primary colored text");
      await expect(coloredText).toBeInTheDocument();
    });

    await step("Test accessibility", async () => {
      await expect(canvas.getByRole("heading", { level: 1, name: "Test Heading" })).toBeVisible();
    });
  },
};

/** Choose heading semantics separately from its visual size. */
export const SemanticHeading: Story = {
  args: { variant: "h4", component: "h2", children: "Sample details" },
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByRole("heading", { level: 2, name: "Sample details" })).toBeVisible();
  },
};
