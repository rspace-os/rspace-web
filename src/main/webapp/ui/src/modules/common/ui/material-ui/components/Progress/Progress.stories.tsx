/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/LinearProgress/LinearProgress.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import LinearProgress from "@mui/material/LinearProgress";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, within } from "storybook/test";
import { createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/LinearProgress",
  component: LinearProgress,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: createSelectArgType(
      ["primary", "secondary", "success", "error", "info", "warning", "inherit"] satisfies NonNullable<
        React.ComponentProps<typeof LinearProgress>["color"]
      >[],
      "primary",
      "The color of the component.",
      "Appearance",
    ),
    variant: createSelectArgType(
      ["determinate", "indeterminate", "buffer", "query"],
      "indeterminate",
      "The variant to use.",
      "Appearance",
    ),
    value: createNumberArgType(
      "The value of the progress indicator (0-100). Used for determinate variant.",
      0,
      0,
      100,
      "Content",
    ),
    valueBuffer: createNumberArgType(
      "The value for the buffer bar (0-100). Used for buffer variant.",
      0,
      0,
      100,
      "Content",
    ),
  },
} satisfies Meta<typeof LinearProgress>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    color: "primary",
    variant: "indeterminate",
    value: 50,
  },
  render: (args) => (
    <Box sx={{ width: "100%" }}>
      <LinearProgress aria-label="Loading progress" {...args} />
    </Box>
  ),
};

export const Default: Story = {
  render: () => <LinearProgress aria-label="Loading progress" />,
};

export function LinearIndeterminate() {
  return (
    <Box sx={{ width: "100%" }}>
      <LinearProgress aria-label="Loading progress" />
    </Box>
  );
}

export function LinearDeterminate() {
  const [progress, setProgress] = React.useState(0);

  React.useEffect(() => {
    const timer = setInterval(() => {
      setProgress((prev) => (prev >= 100 ? 0 : prev + 10));
    }, 800);
    return () => clearInterval(timer);
  }, []);

  return (
    <Box sx={{ width: "100%" }}>
      <LinearProgress aria-label="Loading progress" variant="determinate" value={progress} />
    </Box>
  );
}

export function LinearBuffer() {
  const [progress, setProgress] = React.useState(0);
  const buffer = Math.min(progress + 10, 100);

  React.useEffect(() => {
    const timer = setInterval(() => {
      setProgress((prev) => (prev >= 100 ? 0 : prev + 10));
    }, 500);
    return () => clearInterval(timer);
  }, []);

  return (
    <Box sx={{ width: "100%" }}>
      <LinearProgress aria-label="Loading progress" variant="buffer" value={progress} valueBuffer={buffer} />
    </Box>
  );
}

export function LinearColors() {
  return (
    <Stack sx={{ width: "100%" }} spacing={2}>
      <LinearProgress aria-label="Loading progress" color="primary" />
      <LinearProgress aria-label="Loading progress" color="secondary" />
      <LinearProgress aria-label="Loading progress" color="success" />
      <LinearProgress aria-label="Loading progress" color="error" />
      <LinearProgress aria-label="Loading progress" color="warning" />
      <LinearProgress aria-label="Loading progress" color="info" />
    </Stack>
  );
}

export function CircularIndeterminate() {
  return (
    <Stack direction="row" spacing={2}>
      <CircularProgress aria-label="Loading progress" />
      <CircularProgress aria-label="Loading progress" color="secondary" />
      <CircularProgress aria-label="Loading progress" color="success" />
    </Stack>
  );
}

export function CircularDeterminate() {
  const [progress, setProgress] = React.useState(0);

  React.useEffect(() => {
    const timer = setInterval(() => {
      setProgress((prev) => (prev >= 100 ? 0 : prev + 10));
    }, 800);
    return () => clearInterval(timer);
  }, []);

  return (
    <Box sx={{ position: "relative", display: "inline-flex" }}>
      <CircularProgress aria-label="Loading progress" variant="determinate" value={progress} />
      <Box
        sx={{
          top: 0,
          left: 0,
          bottom: 0,
          right: 0,
          position: "absolute",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Typography variant="caption" component="div" color="text.secondary">
          {progress}%
        </Typography>
      </Box>
    </Box>
  );
}

export function CircularSizes() {
  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
      <CircularProgress aria-label="Loading progress" size={20} />
      <CircularProgress aria-label="Loading progress" size={30} />
      <CircularProgress aria-label="Loading progress" size={40} />
      <CircularProgress aria-label="Loading progress" size={60} />
    </Stack>
  );
}

export const InteractionTest: Story = {
  args: {},
  render: () => (
    <Stack spacing={3}>
      <Box sx={{ width: "100%" }}>
        <LinearProgress aria-label="Loading progress" data-testid="linear-indeterminate" />
      </Box>
      <Box sx={{ width: "100%" }}>
        <LinearProgress
          aria-label="Loading progress"
          variant="determinate"
          value={50}
          data-testid="linear-determinate"
        />
      </Box>
      <Stack direction="row" spacing={2}>
        <CircularProgress aria-label="Loading progress" data-testid="circular-indeterminate" />
        <CircularProgress
          aria-label="Loading progress"
          variant="determinate"
          value={75}
          data-testid="circular-determinate"
        />
      </Stack>
      <Stack direction="row" spacing={2}>
        <LinearProgress aria-label="Loading progress" color="primary" data-testid="linear-primary" />
        <LinearProgress aria-label="Loading progress" color="secondary" data-testid="linear-secondary" />
        <LinearProgress aria-label="Loading progress" color="success" data-testid="linear-success" />
      </Stack>
    </Stack>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify linear progress variants render", async () => {
      const linearIndeterminate = canvas.getByTestId("linear-indeterminate");
      await expect(linearIndeterminate).toBeInTheDocument();

      const linearDeterminate = canvas.getByTestId("linear-determinate");
      await expect(linearDeterminate).toBeInTheDocument();
      await expect(linearDeterminate).toHaveAttribute("aria-valuenow", "50");
    });

    await step("Verify circular progress variants render", async () => {
      const circularIndeterminate = canvas.getByTestId("circular-indeterminate");
      await expect(circularIndeterminate).toBeInTheDocument();

      const circularDeterminate = canvas.getByTestId("circular-determinate");
      await expect(circularDeterminate).toBeInTheDocument();
      await expect(circularDeterminate).toHaveAttribute("aria-valuenow", "75");
    });

    await step("Verify progress color variants render", async () => {
      const primaryProgress = canvas.getByTestId("linear-primary");
      await expect(primaryProgress).toBeInTheDocument();

      const secondaryProgress = canvas.getByTestId("linear-secondary");
      await expect(secondaryProgress).toBeInTheDocument();

      const successProgress = canvas.getByTestId("linear-success");
      await expect(successProgress).toBeInTheDocument();
    });
  },
};
