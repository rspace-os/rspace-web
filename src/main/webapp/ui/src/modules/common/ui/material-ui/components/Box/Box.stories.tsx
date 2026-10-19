/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Box/Box.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

const meta = {
  title: "Material UI/Layout/Box",
  component: Box,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    sx: { control: "object", description: "Theme-aware styles and responsive breakpoint values." },
    children: { control: false },
  },
} satisfies Meta<typeof Box>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Interactive playground for the Box component.
 * Note: Box is primarily styled via the sx prop.
 */
export const Playground: Story = {
  args: {
    sx: {
      width: 300,
      height: 200,
      backgroundColor: "primary.main",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      borderRadius: 2,
    },
  },
  render: (args) => (
    <Box {...args}>
      <Typography color="white">Playground Box</Typography>
    </Box>
  ),
};

export const Default: Story = {
  render: () => <Box sx={{ width: 300, height: 300, backgroundColor: "primary.main" }} />,
};

export function BasicBox() {
  return (
    <Box
      sx={{
        width: 300,
        height: 300,
        backgroundColor: "primary.dark",
        "&:hover": {
          backgroundColor: "primary.main",
          opacity: [0.9, 0.8, 0.7],
        },
      }}
    />
  );
}

export function BoxWithSxProp() {
  return (
    <Box
      sx={{
        width: 300,
        height: 300,
        backgroundColor: "success.light",
        borderRadius: 2,
        border: "2px solid",
        borderColor: "success.dark",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        p: 2,
      }}
    >
      <Typography variant="h6" color="white">
        Box with sx styling
      </Typography>
    </Box>
  );
}

export function FlexboxContainer() {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "row",
        gap: 2,
        p: 2,
        border: "1px solid grey",
        borderRadius: 1,
      }}
    >
      <Box sx={{ p: 2, backgroundColor: "primary.light", flex: 1 }}>Flex Item 1</Box>
      <Box sx={{ p: 2, backgroundColor: "secondary.light", flex: 2 }}>Flex Item 2 (flex: 2)</Box>
      <Box sx={{ p: 2, backgroundColor: "error.light", flex: 1 }}>Flex Item 3</Box>
    </Box>
  );
}

export function GridLayout() {
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: 2,
        p: 2,
      }}
    >
      <Box sx={{ p: 2, backgroundColor: "primary.light", borderRadius: 1 }}>Grid Item 1</Box>
      <Box sx={{ p: 2, backgroundColor: "secondary.light", borderRadius: 1 }}>Grid Item 2</Box>
      <Box sx={{ p: 2, backgroundColor: "success.light", borderRadius: 1 }}>Grid Item 3</Box>
    </Box>
  );
}

export function ResponsiveBox() {
  return (
    <Box
      sx={{
        width: {
          xs: "100%",
          sm: "75%",
          md: "50%",
          lg: "33%",
        },
        height: 200,
        backgroundColor: {
          xs: "error.light",
          sm: "warning.light",
          md: "info.light",
          lg: "success.light",
        },
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        p: 2,
        borderRadius: 2,
      }}
    >
      <Typography variant="h6" color="white" sx={{ textAlign: "center" }}>
        Resize window to see changes
      </Typography>
    </Box>
  );
}

export const InteractionTest: Story = {
  render: () => (
    <Box
      sx={{
        width: 300,
        height: 200,
        backgroundColor: "primary.main",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        p: 2,
      }}
      data-testid="test-box"
    >
      <Typography variant="h6" color="white">
        Test Content
      </Typography>
    </Box>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify Box renders with content", async () => {
      const box = canvas.getByTestId("test-box");
      expect(box).toBeInTheDocument();

      const typography = canvas.getByText("Test Content");
      expect(typography).toBeInTheDocument();
    });

    await step("Verify Box has correct styles", async () => {
      const box = canvas.getByTestId("test-box");
      const styles = window.getComputedStyle(box);
      expect(styles.display).toBe("flex");
    });
  },
};
