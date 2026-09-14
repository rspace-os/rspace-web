/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/AvatarGroup/AvatarGroup.stories.tsx
 * Upstream project license: MIT
 */
import Avatar from "@mui/material/Avatar";
import AvatarGroup from "@mui/material/AvatarGroup";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createNumberArgType, createSelectArgType, muiVariantArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/AvatarGroup",
  component: AvatarGroup,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    max: createNumberArgType("Max number of avatars to display.", 5, 1, 10),
    total: createNumberArgType("Total number of avatars (for +N display).", 0, 0, 100),
    spacing: createSelectArgType(["small", "medium"], "medium", "Spacing between avatars.", "Layout"),
    variant: muiVariantArgType(["circular", "rounded", "square"], "circular"),
    children: { control: false },
  },
} satisfies Meta<typeof AvatarGroup>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    max: 4,
    children: [
      <Avatar
        key="1"
        alt="User 1"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="2"
        alt="User 2"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="3"
        alt="User 3"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="4"
        alt="User 4"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="5"
        alt="User 5"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
    ],
  },
};

export const MaxAvatars: Story = {
  args: {},
  render: () => (
    <Stack spacing={2}>
      <AvatarGroup max={3}>
        <Avatar
          alt="User 1"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 2"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 3"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 4"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 5"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
      </AvatarGroup>
      <AvatarGroup max={5}>
        <Avatar
          alt="User 1"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 2"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 3"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 4"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 5"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
      </AvatarGroup>
    </Stack>
  ),
};

export const TotalAvatars: Story = {
  args: {
    total: 24,
    children: [
      <Avatar
        key="1"
        alt="User 1"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="2"
        alt="User 2"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="3"
        alt="User 3"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
    ],
  },
};

export const Spacing: Story = {
  args: {},
  render: () => (
    <Stack spacing={2}>
      <AvatarGroup spacing="small">
        <Avatar
          alt="User 1"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 2"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 3"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
      </AvatarGroup>
      <AvatarGroup spacing="medium">
        <Avatar
          alt="User 1"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 2"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
        <Avatar
          alt="User 3"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        />
      </AvatarGroup>
    </Stack>
  ),
};

export const WithLetterAvatars: Story = {
  args: {
    children: [
      <Avatar key="1" sx={{ bgcolor: "#1976d2" }}>
        AB
      </Avatar>,
      <Avatar key="2" sx={{ bgcolor: "#9c27b0" }}>
        CD
      </Avatar>,
      <Avatar key="3" sx={{ bgcolor: "#2e7d32" }}>
        EF
      </Avatar>,
    ],
  },
};

export const InteractionTest: Story = {
  args: {
    max: 3,
    children: [
      <Avatar
        key="1"
        alt="User 1"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="2"
        alt="User 2"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="3"
        alt="User 3"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="4"
        alt="User 4"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
      <Avatar
        key="5"
        alt="User 5"
        src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
      />,
    ],
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify avatars render (max=3 shows 2 images + overflow indicator)
    const images = canvas.getAllByRole("img");
    await expect(images.length).toBeGreaterThanOrEqual(1);

    // Verify overflow indicator shows +3 (5 total - 2 shown = 3 remaining)
    const overflow = canvas.getByText("+3");
    await expect(overflow).toBeInTheDocument();
  },
};
