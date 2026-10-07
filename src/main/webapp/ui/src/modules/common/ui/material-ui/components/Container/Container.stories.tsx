/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Container/Container.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta: Meta<typeof Container> = {
  title: "Material UI/Layout/Container",
  component: Container,
  parameters: {
    a11y: { test: "error" },
    layout: "fullscreen",
  },
  tags: ["autodocs"],
  argTypes: {
    maxWidth: createSelectArgType(
      ["xs", "sm", "md", "lg", "xl", false],
      "lg",
      "Determine the max-width of the container.",
      "Layout",
    ),
    fixed: createBooleanArgType("Set the max-width to match the min-width of the current breakpoint.", false, "Layout"),
    disableGutters: createBooleanArgType("If true, the left and right padding is removed.", false, "Layout"),
    children: { control: false },
  },
};

export default meta;
type Story = StoryObj<typeof Container>;

export const Playground: Story = {
  args: {
    maxWidth: "lg",
    fixed: false,
    disableGutters: false,
  },
  render: (args) => (
    <Container {...args}>
      <Box sx={{ bgcolor: "primary.light", p: 2 }}>
        <Typography>Playground Container - resize window to see responsive behavior</Typography>
      </Box>
    </Container>
  ),
};

/**
 * Default fluid container
 */
export const Default: Story = {
  args: {
    children: (
      <Box sx={{ bgcolor: "primary.light", p: 2 }}>
        <Typography>Default Container</Typography>
      </Box>
    ),
  },
};

/**
 * Fixed container with max width
 */
export const Fixed: Story = {
  args: {
    fixed: true,
    children: (
      <Box sx={{ bgcolor: "primary.light", p: 2 }}>
        <Typography>Fixed Container</Typography>
      </Box>
    ),
  },
};

/**
 * Container with different max widths
 */
export const MaxWidths: Story = {
  render: () => (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <Container maxWidth="xs">
        <Box sx={{ bgcolor: "error.light", p: 2 }}>
          <Typography>maxWidth="xs" (444px)</Typography>
        </Box>
      </Container>

      <Container maxWidth="sm">
        <Box sx={{ bgcolor: "warning.light", p: 2 }}>
          <Typography>maxWidth="sm" (600px)</Typography>
        </Box>
      </Container>

      <Container maxWidth="md">
        <Box sx={{ bgcolor: "info.light", p: 2 }}>
          <Typography>maxWidth="md" (900px)</Typography>
        </Box>
      </Container>

      <Container maxWidth="lg">
        <Box sx={{ bgcolor: "success.light", p: 2 }}>
          <Typography>maxWidth="lg" (1200px)</Typography>
        </Box>
      </Container>

      <Container maxWidth="xl">
        <Box sx={{ bgcolor: "secondary.light", p: 2 }}>
          <Typography>maxWidth="xl" (1536px)</Typography>
        </Box>
      </Container>

      <Container maxWidth={false}>
        <Box sx={{ bgcolor: "grey.300", p: 2 }}>
          <Typography>maxWidth=false (no max width)</Typography>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Container with disabled gutters
 */
export const DisableGutters: Story = {
  args: {
    disableGutters: true,
    maxWidth: "md",
    children: (
      <Box sx={{ bgcolor: "primary.light", p: 2 }}>
        <Typography>Container without gutters (no horizontal padding)</Typography>
      </Box>
    ),
  },
};

/**
 * Nested containers
 */
export const NestedContainers: Story = {
  render: () => (
    <Container maxWidth="lg">
      <Box sx={{ bgcolor: "grey.200", p: 2 }}>
        <Typography variant="h6" gutterBottom>
          Outer Container (lg)
        </Typography>
        <Container maxWidth="sm">
          <Box sx={{ bgcolor: "primary.light", p: 2 }}>
            <Typography>Inner Container (sm) - useful for centered content sections</Typography>
          </Box>
        </Container>
      </Box>
    </Container>
  ),
};

export const InteractionTest: Story = {
  args: {
    maxWidth: "md",
    children: (
      <Box sx={{ bgcolor: "primary.light", p: 2 }} data-testid="container-content">
        <Typography>Container Test Content</Typography>
      </Box>
    ),
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify Container renders with content
    const content = canvas.getByTestId("container-content");
    await expect(content).toBeInTheDocument();

    const typography = canvas.getByText("Container Test Content");
    await expect(typography).toBeInTheDocument();
  },
};
