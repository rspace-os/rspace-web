/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Icon/Icon.stories.tsx
 * Upstream project license: MIT
 */
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import FavoriteIcon from "@mui/icons-material/Favorite";
import HomeIcon from "@mui/icons-material/Home";
import SearchIcon from "@mui/icons-material/Search";
import SettingsIcon from "@mui/icons-material/Settings";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/SvgIcon",
  component: HomeIcon,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: createSelectArgType(
      [
        "primary",
        "secondary",
        "success",
        "error",
        "info",
        "warning",
        "inherit",
        "action",
        "disabled",
      ] satisfies NonNullable<React.ComponentProps<typeof HomeIcon>["color"]>[],
      "inherit",
      "The color of the component.",
      "Appearance",
    ),
    fontSize: createSelectArgType(
      ["inherit", "small", "medium", "large"],
      "medium",
      "The fontSize applied to the icon.",
      "Appearance",
    ),
    titleAccess: { control: "text", description: "Accessible name for the SVG icon." },
  },
} satisfies Meta<typeof HomeIcon>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    titleAccess: "Home",
    color: "primary",
    fontSize: "medium",
  },
};

export const Default: Story = {
  args: {
    titleAccess: "Home",
  },
};

/** SVG icons use @mui/icons-material; unlike Icon ligatures, they require no icon font. */
export function VariousIcons() {
  return (
    <Stack direction="row" spacing={2}>
      <HomeIcon />
      <SettingsIcon />
      <DeleteIcon />
      <AddIcon />
      <SearchIcon />
      <FavoriteIcon />
    </Stack>
  );
}

export function IconSizes() {
  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
      <HomeIcon fontSize="small" />
      <HomeIcon fontSize="medium" />
      <HomeIcon fontSize="large" />
      <HomeIcon sx={{ fontSize: 48 }} />
    </Stack>
  );
}

export function IconColors() {
  return (
    <Stack direction="row" spacing={2}>
      <HomeIcon color="primary" />
      <HomeIcon color="secondary" />
      <HomeIcon color="error" />
      <HomeIcon color="success" />
      <HomeIcon color="warning" />
      <HomeIcon color="info" />
      <HomeIcon color="disabled" />
    </Stack>
  );
}

export function IconWithButton() {
  return (
    <Stack direction="row" spacing={1}>
      <IconButton aria-label="delete">
        <DeleteIcon />
      </IconButton>
      <IconButton aria-label="add" color="primary">
        <AddIcon />
      </IconButton>
      <IconButton aria-label="favorite" color="secondary">
        <FavoriteIcon />
      </IconButton>
      <IconButton aria-label="settings" disabled>
        <SettingsIcon />
      </IconButton>
    </Stack>
  );
}

export function IconButtonSizes() {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
      <IconButton aria-label="delete" size="small">
        <DeleteIcon fontSize="small" />
      </IconButton>
      <IconButton aria-label="delete" size="medium">
        <DeleteIcon fontSize="medium" />
      </IconButton>
      <IconButton aria-label="delete" size="large">
        <DeleteIcon fontSize="large" />
      </IconButton>
    </Stack>
  );
}

export function MaterialIcons() {
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2}>
        <HomeIcon />
        <SettingsIcon />
        <DeleteIcon />
        <SearchIcon />
      </Stack>
      <Stack direction="row" spacing={2}>
        <HomeIcon color="primary" />
        <SettingsIcon color="secondary" />
        <DeleteIcon color="error" />
        <SearchIcon color="success" />
      </Stack>
      <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
        <HomeIcon fontSize="small" />
        <SettingsIcon fontSize="medium" />
        <DeleteIcon fontSize="large" />
      </Stack>
    </Stack>
  );
}

export const InteractionTest: Story = {
  args: {
    titleAccess: "Home",
  },
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByRole("img", { name: "Home" })).toBeVisible();
  },
};
