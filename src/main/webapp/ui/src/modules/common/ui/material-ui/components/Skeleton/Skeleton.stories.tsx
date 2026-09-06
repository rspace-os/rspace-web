/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Skeleton/Skeleton.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/Skeleton",
  component: Skeleton,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: createSelectArgType(
      ["text", "circular", "rectangular", "rounded"],
      "text",
      "The type of content that will be rendered.",
      "Appearance",
    ),
    animation: createSelectArgType(
      ["pulse", "wave", false],
      "pulse",
      "The animation. If false the animation effect is disabled.",
      "Appearance",
    ),
    width: createNumberArgType("Width of the skeleton.", 210, 0, 500, "Layout"),
    height: createNumberArgType("Height of the skeleton.", 60, 0, 500, "Layout"),
  },
} satisfies Meta<typeof Skeleton>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    variant: "text",
    animation: "pulse",
    width: 210,
    height: 61,
  },
};

export const Default: Story = {
  args: {
    variant: "text",
    width: 210,
  },
};

export function Variants() {
  return (
    <Stack spacing={1}>
      <Skeleton variant="text" sx={{ fontSize: "1rem" }} />
      <Skeleton variant="circular" width={40} height={40} />
      <Skeleton variant="rectangular" width={210} height={60} />
      <Skeleton variant="rounded" width={210} height={60} />
    </Stack>
  );
}

export function Animations() {
  return (
    <Stack spacing={1}>
      <Skeleton animation="pulse" />
      <Skeleton animation="wave" />
      <Skeleton animation={false} />
    </Stack>
  );
}

export function CardExample() {
  return (
    <Box sx={{ width: 300 }}>
      <Skeleton variant="rectangular" height={140} />
      <Box sx={{ pt: 0.5 }}>
        <Skeleton />
        <Skeleton width="60%" />
      </Box>
    </Box>
  );
}

export function ListExample() {
  return (
    <Stack spacing={1}>
      {[1, 2, 3].map((item) => (
        <Box key={item} sx={{ display: "flex", alignItems: "center" }}>
          <Skeleton variant="circular" width={40} height={40} sx={{ mr: 2 }} />
          <Box sx={{ width: "100%" }}>
            <Skeleton width="80%" />
            <Skeleton width="60%" />
          </Box>
        </Box>
      ))}
    </Stack>
  );
}

export const InteractionTest: Story = {
  args: {},
  render: () => (
    <Stack spacing={2}>
      <Skeleton variant="text" sx={{ fontSize: "1rem" }} data-testid="text-skeleton" />
      <Skeleton variant="circular" width={40} height={40} data-testid="circular-skeleton" />
      <Skeleton variant="rectangular" width={210} height={60} data-testid="rect-skeleton" />
      <Skeleton variant="rounded" width={210} height={60} data-testid="rounded-skeleton" />
      <Box sx={{ width: 300 }}>
        <Skeleton animation="pulse" data-testid="pulse-skeleton" />
        <Skeleton animation="wave" data-testid="wave-skeleton" />
        <Skeleton animation={false} data-testid="static-skeleton" />
      </Box>
    </Stack>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify skeleton variants render", async () => {
      const textSkeleton = canvas.getByTestId("text-skeleton");
      await expect(textSkeleton).toBeInTheDocument();

      const circularSkeleton = canvas.getByTestId("circular-skeleton");
      await expect(circularSkeleton).toBeInTheDocument();

      const rectSkeleton = canvas.getByTestId("rect-skeleton");
      await expect(rectSkeleton).toBeInTheDocument();

      const roundedSkeleton = canvas.getByTestId("rounded-skeleton");
      await expect(roundedSkeleton).toBeInTheDocument();
    });

    await step("Verify skeleton animations render", async () => {
      const pulseSkeleton = canvas.getByTestId("pulse-skeleton");
      await expect(pulseSkeleton).toBeInTheDocument();

      const waveSkeleton = canvas.getByTestId("wave-skeleton");
      await expect(waveSkeleton).toBeInTheDocument();

      const staticSkeleton = canvas.getByTestId("static-skeleton");
      await expect(staticSkeleton).toBeInTheDocument();
    });
  },
};
