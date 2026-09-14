/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/BottomNavigation/BottomNavigation.stories.tsx
 * Upstream project license: MIT
 */
import FavoriteIcon from "@mui/icons-material/Favorite";
import FolderIcon from "@mui/icons-material/Folder";
import LocationOnIcon from "@mui/icons-material/LocationOn";
import RestoreIcon from "@mui/icons-material/Restore";
import BottomNavigation from "@mui/material/BottomNavigation";
import BottomNavigationAction from "@mui/material/BottomNavigationAction";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { useArgs } from "storybook/preview-api";
import { expect, userEvent, within } from "storybook/test";
import { createBooleanArgType } from "../../argTypeTemplates";

const meta: Meta<typeof BottomNavigation> = {
  title: "Material UI/Navigation/BottomNavigation",
  component: BottomNavigation,
  parameters: {
    a11y: { test: "error" },
    layout: "padded",
  },
  tags: ["autodocs"],
  argTypes: {
    showLabels: createBooleanArgType(
      "If true, all BottomNavigationActions will show their labels.",
      false,
      "Appearance",
    ),
    value: {
      control: { type: "number", min: 0, max: 10 },
      description: "The value of the currently selected BottomNavigationAction.",
      table: { category: "State" },
    },
    children: { control: false },
  },
};

export default meta;
type Story = StoryObj<typeof BottomNavigation>;

export const Playground: Story = {
  args: {
    showLabels: true,
    value: 0,
  },
  render: function PlaygroundNavigation(args) {
    const [, updateArgs] = useArgs();
    return (
      <Box sx={{ width: 500 }}>
        <BottomNavigation {...args} value={args.value} onChange={(_, next) => updateArgs({ value: next })}>
          <BottomNavigationAction label="Recents" icon={<RestoreIcon />} />
          <BottomNavigationAction label="Favorites" icon={<FavoriteIcon />} />
          <BottomNavigationAction label="Nearby" icon={<LocationOnIcon />} />
        </BottomNavigation>
      </Box>
    );
  },
};

/**
 * Basic bottom navigation with three actions
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box sx={{ width: 500 }}>
        <BottomNavigation showLabels value={value} onChange={(_, newValue) => setValue(newValue)}>
          <BottomNavigationAction label="Recents" icon={<RestoreIcon />} />
          <BottomNavigationAction label="Favorites" icon={<FavoriteIcon />} />
          <BottomNavigationAction label="Nearby" icon={<LocationOnIcon />} />
        </BottomNavigation>
      </Box>
    );
  },
};

/**
 * Bottom navigation without labels (icons only)
 */
export const IconsOnly: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box sx={{ width: 500 }}>
        <BottomNavigation value={value} onChange={(_, newValue) => setValue(newValue)}>
          <BottomNavigationAction aria-label="Recents" icon={<RestoreIcon />} />
          <BottomNavigationAction aria-label="Favorites" icon={<FavoriteIcon />} />
          <BottomNavigationAction aria-label="Nearby" icon={<LocationOnIcon />} />
          <BottomNavigationAction aria-label="Folders" icon={<FolderIcon />} />
        </BottomNavigation>
      </Box>
    );
  },
};

/**
 * Fixed position bottom navigation
 */
export const FixedPosition: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box sx={{ pb: 7, minHeight: 200 }}>
        <Box sx={{ p: 2 }}>Scroll down to see the fixed bottom navigation</Box>
        <Paper sx={{ position: "fixed", bottom: 0, left: 0, right: 0 }} elevation={3}>
          <BottomNavigation showLabels value={value} onChange={(_, newValue) => setValue(newValue)}>
            <BottomNavigationAction label="Recents" icon={<RestoreIcon />} />
            <BottomNavigationAction label="Favorites" icon={<FavoriteIcon />} />
            <BottomNavigationAction label="Nearby" icon={<LocationOnIcon />} />
          </BottomNavigation>
        </Paper>
      </Box>
    );
  },
};

/**
 * Interaction test for bottom navigation
 */
export const InteractionTest: Story = {
  render: () => {
    const [value, setValue] = useState(0);

    return (
      <Box sx={{ width: 500 }}>
        <BottomNavigation
          showLabels
          value={value}
          onChange={(_, newValue) => setValue(newValue)}
          data-testid="bottom-navigation"
        >
          <BottomNavigationAction label="Recents" icon={<RestoreIcon />} data-testid="recents-action" />
          <BottomNavigationAction label="Favorites" icon={<FavoriteIcon />} data-testid="favorites-action" />
          <BottomNavigationAction label="Nearby" icon={<LocationOnIcon />} data-testid="nearby-action" />
        </BottomNavigation>
      </Box>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Click on Favorites
    const favoritesAction = canvas.getByTestId("favorites-action");
    await userEvent.click(favoritesAction);
    await expect(favoritesAction).toHaveClass("Mui-selected");

    // Click on Nearby
    const nearbyAction = canvas.getByTestId("nearby-action");
    await userEvent.click(nearbyAction);
    await expect(nearbyAction).toHaveClass("Mui-selected");

    // Click back to Recents
    const recentsAction = canvas.getByTestId("recents-action");
    await userEvent.click(recentsAction);
    await expect(recentsAction).toHaveClass("Mui-selected");
  },
};
