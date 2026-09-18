/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Rating/Rating.stories.tsx
 * Upstream project license: MIT
 */
import FavoriteIcon from "@mui/icons-material/Favorite";
import FavoriteBorderIcon from "@mui/icons-material/FavoriteBorder";
import Box from "@mui/material/Box";
import Rating from "@mui/material/Rating";
import Stack from "@mui/material/Stack";
import { styled } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, userEvent, within } from "storybook/test";
import { createBooleanArgType, createNumberArgType, muiDisabledArgType, muiSizeArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/Rating",
  component: Rating,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    readOnly: createBooleanArgType("If true, the component is read-only.", false, "State"),
    max: createNumberArgType("Maximum rating.", 5, 1, 10, "Content"),
    precision: {
      control: { type: "select" },
      options: [0.1, 0.5, 1],
      description: "The minimum increment value change allowed.",
      table: {
        defaultValue: { summary: "1" },
        category: "Content",
        type: { summary: "number" },
      },
    },
    defaultValue: {
      control: { type: "number", min: 0, max: 5, step: 0.5 },
      description: "The default value. Use when the component is not controlled.",
      table: { category: "Content" },
    },
    highlightSelectedOnly: createBooleanArgType(
      "If true, only the selected icon will be highlighted.",
      false,
      "Appearance",
    ),
  },
} satisfies Meta<typeof Rating>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    defaultValue: 3,
    max: 5,
    precision: 1,
    size: "medium",
    disabled: false,
    readOnly: false,
  },
};

export const Default: Story = {
  args: {
    defaultValue: 2,
  },
};

/**
 * Visual demonstration of rating interaction behavior.
 * Keyboard selection and clearing are exercised below.
 */
export const InteractionDemo: Story = {
  args: {
    defaultValue: 3,
    name: "rating-demo",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const twoStars = canvas.getByRole("radio", { name: "2 Stars" });
    twoStars.focus();
    await userEvent.keyboard(" ");
    await expect(twoStars).toBeChecked();
    await userEvent.keyboard("{ArrowRight}");
    await expect(canvas.getByRole("radio", { name: "3 Stars" })).toBeChecked();
  },
};

export function Controlled() {
  const [value, setValue] = React.useState<number | null>(2);

  return (
    <Box>
      <Typography component="div">Controlled</Typography>
      <Rating value={value} onChange={(_, newValue) => setValue(newValue)} />
    </Box>
  );
}

export function ReadOnly() {
  return (
    <Box>
      <Typography component="div">Read only</Typography>
      <Rating value={3.5} readOnly precision={0.5} />
    </Box>
  );
}

export function Disabled() {
  return (
    <Box>
      <Typography component="div">Disabled</Typography>
      <Rating value={2} disabled />
    </Box>
  );
}

export function Sizes() {
  return (
    <Stack spacing={1}>
      <Rating defaultValue={2} size="small" />
      <Rating defaultValue={2} />
      <Rating defaultValue={2} size="large" />
    </Stack>
  );
}

const StyledRating = styled(Rating)({
  "& .MuiRating-iconFilled": {
    color: "#ff6d75",
  },
  "& .MuiRating-iconHover": {
    color: "#ff3d47",
  },
});

export function CustomIcon() {
  return (
    <Box>
      <Typography component="div">Custom icon</Typography>
      <StyledRating
        defaultValue={2}
        icon={<FavoriteIcon fontSize="inherit" />}
        emptyIcon={<FavoriteBorderIcon fontSize="inherit" />}
      />
    </Box>
  );
}

export function HalfRating() {
  return (
    <Stack spacing={1}>
      <Rating defaultValue={2.5} precision={0.5} />
      <Rating defaultValue={3.5} precision={0.5} readOnly />
    </Stack>
  );
}
