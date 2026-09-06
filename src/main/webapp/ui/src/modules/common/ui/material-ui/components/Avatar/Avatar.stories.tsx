/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Avatar/Avatar.stories.tsx
 * Upstream project license: MIT
 */
import FolderIcon from "@mui/icons-material/Folder";
import Avatar from "@mui/material/Avatar";
import AvatarGroup from "@mui/material/AvatarGroup";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/Avatar",
  component: Avatar,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: createSelectArgType(
      ["circular", "rounded", "square"],
      "circular",
      "The shape of the avatar.",
      "Appearance",
    ),
    src: {
      control: "text",
      description: "The src attribute for the img element.",
      table: { category: "Content" },
    },
    alt: {
      control: "text",
      description: "Used in combination with src to provide an alt attribute for the img element.",
      table: { category: "Content" },
    },
    children: {
      control: "text",
      description: "Used to render icon or text elements inside the Avatar.",
      table: { category: "Content" },
    },
  },
} satisfies Meta<typeof Avatar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    children: "AB",
    variant: "circular",
  },
};

export const Default: Story = {
  args: {
    children: "H",
  },
};

export function ImageAvatar() {
  return (
    <Stack direction="row" spacing={2}>
      <Avatar
        alt="Avatar 1"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
      <Avatar
        alt="Avatar 2"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
      <Avatar
        alt="Avatar 3"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
    </Stack>
  );
}

export function LetterAvatar() {
  return (
    <Stack direction="row" spacing={2}>
      <Avatar>H</Avatar>
      <Avatar sx={{ bgcolor: "primary.main" }}>N</Avatar>
      <Avatar sx={{ bgcolor: "secondary.main" }}>OP</Avatar>
    </Stack>
  );
}

export function IconAvatar() {
  return (
    <Stack direction="row" spacing={2}>
      <Avatar>
        <FolderIcon />
      </Avatar>
      <Avatar sx={{ bgcolor: "primary.main" }}>
        <FolderIcon />
      </Avatar>
    </Stack>
  );
}

export function Sizes() {
  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
      <Avatar sx={{ width: 24, height: 24 }}>S</Avatar>
      <Avatar>M</Avatar>
      <Avatar sx={{ width: 56, height: 56 }}>L</Avatar>
    </Stack>
  );
}

export function Colors() {
  return (
    <Stack direction="row" spacing={2}>
      <Avatar sx={{ bgcolor: "primary.main" }}>P</Avatar>
      <Avatar sx={{ bgcolor: "secondary.main" }}>S</Avatar>
      <Avatar sx={{ bgcolor: "error.main" }}>E</Avatar>
      <Avatar sx={{ bgcolor: "success.main" }}>S</Avatar>
    </Stack>
  );
}

export function Group() {
  return (
    <AvatarGroup max={4}>
      <Avatar
        alt="A1"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
      <Avatar
        alt="A2"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
      <Avatar
        alt="A3"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
      <Avatar
        alt="A4"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
      <Avatar
        alt="A5"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
    </AvatarGroup>
  );
}

export const InteractionTest: Story = {
  args: {},
  render: () => (
    <Stack direction="row" spacing={2} sx={{ alignItems: "center" }}>
      <Avatar>H</Avatar>
      <Avatar
        alt="User Avatar"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />
      <Avatar sx={{ width: 56, height: 56, bgcolor: "secondary.main" }}>XL</Avatar>
    </Stack>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify letter avatar renders
    const letterAvatar = canvas.getByText("H");
    await expect(letterAvatar).toBeInTheDocument();

    // Verify image avatar renders
    const imageAvatar = canvas.getByRole("img", { name: /user avatar/i });
    await expect(imageAvatar).toBeInTheDocument();

    // Verify size variant avatar renders
    const largeAvatar = canvas.getByText("XL");
    await expect(largeAvatar).toBeInTheDocument();
  },
};
