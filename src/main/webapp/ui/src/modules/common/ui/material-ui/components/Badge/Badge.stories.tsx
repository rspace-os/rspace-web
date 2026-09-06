/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Badge/Badge.stories.tsx
 * Upstream project license: MIT
 */
import MailIcon from "@mui/icons-material/Mail";
import NotificationsIcon from "@mui/icons-material/Notifications";
import Badge from "@mui/material/Badge";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import {
  createBooleanArgType,
  createNumberArgType,
  createSelectArgType,
  muiColorArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/Badge",
  component: Badge,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: muiColorArgType,
    variant: createSelectArgType(["standard", "dot"], "standard", "The variant to use.", "Appearance"),
    anchorOrigin: { control: false }, // Complex object
    overlap: createSelectArgType(
      ["circular", "rectangular"],
      "rectangular",
      "Wrapped shape the badge should overlap.",
      "Layout",
    ),
    invisible: createBooleanArgType("If true, the badge is invisible.", false, "State"),
    showZero: createBooleanArgType("Controls whether the badge is hidden when badgeContent is zero.", false, "State"),
    max: createNumberArgType("Max count to show.", 99, 1, 999, "Content"),
    badgeContent: {
      control: { type: "number", min: 0, max: 1000 },
      description: "The content rendered within the badge.",
      table: { category: "Content" },
    },
    children: { control: false },
  },
} satisfies Meta<typeof Badge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    badgeContent: 4,
    color: "primary",
    variant: "standard",
    invisible: false,
    showZero: false,
    max: 99,
    children: <MailIcon />,
  },
};

export const Default: Story = {
  args: {
    badgeContent: 4,
    color: "primary",
    children: <MailIcon />,
  },
};

export function Colors() {
  return (
    <Stack direction="row" spacing={3}>
      <Badge badgeContent={4} color="primary">
        <MailIcon />
      </Badge>
      <Badge badgeContent={4} color="secondary">
        <MailIcon />
      </Badge>
      <Badge badgeContent={4} color="error">
        <MailIcon />
      </Badge>
      <Badge badgeContent={4} color="success">
        <MailIcon />
      </Badge>
    </Stack>
  );
}

export function DotBadge() {
  return (
    <Stack direction="row" spacing={3}>
      <Badge variant="dot" color="primary">
        <MailIcon />
      </Badge>
      <Badge variant="dot" color="secondary">
        <NotificationsIcon />
      </Badge>
    </Stack>
  );
}

export function MaxBadgeCount() {
  return (
    <Stack direction="row" spacing={3}>
      <Badge badgeContent={99} color="primary">
        <MailIcon />
      </Badge>
      <Badge badgeContent={100} color="primary">
        <MailIcon />
      </Badge>
      <Badge badgeContent={1000} max={999} color="primary">
        <MailIcon />
      </Badge>
    </Stack>
  );
}

export function WithIconButton() {
  return (
    <Stack direction="row" spacing={2}>
      <IconButton aria-label="Mail, 4 unread messages">
        <Badge badgeContent={4} color="primary">
          <MailIcon />
        </Badge>
      </IconButton>
      <IconButton aria-label="17 notifications">
        <Badge badgeContent={17} color="error">
          <NotificationsIcon />
        </Badge>
      </IconButton>
    </Stack>
  );
}

export const InteractionTest: Story = {
  args: {},
  render: () => (
    <Stack direction="row" spacing={3}>
      <Badge badgeContent={4} color="primary">
        <MailIcon />
      </Badge>
      <Badge badgeContent={99} color="secondary">
        <MailIcon />
      </Badge>
      <Badge badgeContent={1000} max={999} color="error">
        <MailIcon />
      </Badge>
    </Stack>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify badges render with correct content
    const badge4 = canvas.getByText("4");
    await expect(badge4).toBeInTheDocument();

    const badge99 = canvas.getByText("99");
    await expect(badge99).toBeInTheDocument();

    const badge999Plus = canvas.getByText("999+");
    await expect(badge999Plus).toBeInTheDocument();
  },
};
