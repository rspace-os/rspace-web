/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/IconButton/IconButton.stories.tsx
 * Upstream project license: MIT
 */
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import MenuIcon from "@mui/icons-material/Menu";
import SettingsIcon from "@mui/icons-material/Settings";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  createSelectArgType,
  muiColorArgType,
  muiDisabledArgType,
  muiSizeArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/IconButton",
  component: IconButton,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: muiColorArgType,
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    disableRipple: createBooleanArgType("If true, the ripple effect is disabled.", false, "Appearance"),
    edge: createSelectArgType(
      ["start", "end", false],
      false,
      "If given, uses a negative margin to counteract padding.",
      "Layout",
    ),
    children: { control: false },
  },
  args: {
    "aria-label": "icon button",
    onClick: fn(),
  },
} satisfies Meta<typeof IconButton>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    children: <MenuIcon />,
    "aria-label": "menu",
  },
};

export const Colors: Story = {
  args: {},
  render: () => (
    <Stack direction="row" spacing={1}>
      <IconButton aria-label="default">
        <MenuIcon />
      </IconButton>
      <IconButton aria-label="primary" color="primary">
        <MenuIcon />
      </IconButton>
      <IconButton aria-label="secondary" color="secondary">
        <MenuIcon />
      </IconButton>
      <IconButton aria-label="error" color="error">
        <DeleteIcon />
      </IconButton>
      <IconButton aria-label="success" color="success">
        <EditIcon />
      </IconButton>
    </Stack>
  ),
};

export const Sizes: Story = {
  args: {},
  render: () => (
    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
      <IconButton aria-label="small" size="small">
        <SettingsIcon fontSize="small" />
      </IconButton>
      <IconButton aria-label="medium" size="medium">
        <SettingsIcon />
      </IconButton>
      <IconButton aria-label="large" size="large">
        <SettingsIcon fontSize="large" />
      </IconButton>
    </Stack>
  ),
};

export const Disabled: Story = {
  args: {
    children: <DeleteIcon />,
    disabled: true,
    "aria-label": "disabled delete",
  },
};

export const InteractionTest: Story = {
  args: {
    children: <EditIcon />,
    "aria-label": "edit",
    onClick: fn(),
  },
  play: async ({ args, canvasElement }) => {
    const canvas = within(canvasElement);
    const button = canvas.getByRole("button", { name: /edit/i });

    await expect(button).toBeInTheDocument();
    await userEvent.click(button);
    await expect(args.onClick).toHaveBeenCalledTimes(1);
  },
};
