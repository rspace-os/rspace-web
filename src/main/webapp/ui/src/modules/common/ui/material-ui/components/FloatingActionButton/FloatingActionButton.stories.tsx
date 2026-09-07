/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/FloatingActionButton/FloatingActionButton.stories.tsx
 * Upstream project license: MIT
 */
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import FavoriteIcon from "@mui/icons-material/Favorite";
import NavigationIcon from "@mui/icons-material/Navigation";
import Box from "@mui/material/Box";
import Fab from "@mui/material/Fab";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { createSelectArgType, muiDisabledArgType, muiSizeArgType, muiVariantArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/FloatingActionButton",
  component: Fab,
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
        "default",
        "callToAction",
      ] satisfies NonNullable<React.ComponentProps<typeof Fab>["color"]>[],
      "default",
      "The color of the component.",
      "Appearance",
    ),
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    variant: muiVariantArgType(["circular", "extended"], "circular"),
    disableRipple: {
      control: "boolean",
      description: "If true, the ripple effect is disabled.",
      table: {
        defaultValue: { summary: "false" },
        category: "Appearance",
      },
    },
    href: {
      control: "text",
      description: "The URL to link to when the button is clicked.",
      table: { category: "Navigation" },
    },
    children: { control: false },
  },
} satisfies Meta<typeof Fab>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    color: "primary",
    size: "medium",
    disabled: false,
    variant: "circular",
    "aria-label": "add",
    children: <AddIcon />,
  },
};

export const Default: Story = {
  args: {
    color: "primary",
    "aria-label": "add",
    children: <AddIcon />,
  },
};

export const Colors: Story = {
  args: {} as never,
  render: () => (
    <Box sx={{ "& > :not(style)": { m: 1 } }}>
      <Fab color="primary" aria-label="primary">
        <AddIcon />
      </Fab>
      <Fab color="secondary" aria-label="secondary">
        <EditIcon />
      </Fab>
      <Fab color="success" aria-label="success">
        <AddIcon />
      </Fab>
      <Fab color="error" aria-label="error">
        <FavoriteIcon />
      </Fab>
    </Box>
  ),
};

export const Sizes: Story = {
  args: {} as never,
  render: () => (
    <Box sx={{ "& > :not(style)": { m: 1 } }}>
      <Fab size="small" color="primary" aria-label="small">
        <AddIcon />
      </Fab>
      <Fab size="medium" color="primary" aria-label="medium">
        <AddIcon />
      </Fab>
      <Fab size="large" color="primary" aria-label="large">
        <AddIcon />
      </Fab>
    </Box>
  ),
};

export const Extended: Story = {
  args: {} as never,
  render: () => (
    <Box sx={{ "& > :not(style)": { m: 1 } }}>
      <Fab variant="extended" color="primary">
        <NavigationIcon sx={{ mr: 1 }} />
        Navigate
      </Fab>
      <Fab variant="extended" color="secondary">
        <EditIcon sx={{ mr: 1 }} />
        Edit
      </Fab>
    </Box>
  ),
};

export const FloatingActionButtons: Story = {
  args: {} as never,
  render: () => (
    <Box sx={{ "& > :not(style)": { m: 1 } }}>
      <Fab color="primary" aria-label="add">
        <AddIcon />
      </Fab>
      <Fab color="secondary" aria-label="edit">
        <EditIcon />
      </Fab>
      <Fab variant="extended">
        <NavigationIcon sx={{ mr: 1 }} />
        Navigate
      </Fab>
      <Fab disabled aria-label="like">
        <FavoriteIcon />
      </Fab>
    </Box>
  ),
};

export const InteractionTest: Story = {
  args: { onClick: fn() },
  render: (args) => {
    return (
      <Box sx={{ "& > :not(style)": { m: 1 } }}>
        <Fab color="primary" aria-label="add" onClick={args.onClick}>
          <AddIcon />
        </Fab>
        <Fab color="secondary" aria-label="edit" onClick={args.onClick}>
          <EditIcon />
        </Fab>
        <Fab variant="extended" aria-label="navigate">
          <NavigationIcon sx={{ mr: 1 }} />
          Navigate
        </Fab>
      </Box>
    );
  },
  play: async ({ canvasElement, step, args }) => {
    const canvas = within(canvasElement);

    await step("Verify all FAB buttons render", async () => {
      const addButton = canvas.getByLabelText("add");
      const editButton = canvas.getByLabelText("edit");
      const navigateButton = canvas.getByLabelText("navigate");

      expect(addButton).toBeInTheDocument();
      expect(editButton).toBeInTheDocument();
      expect(navigateButton).toBeInTheDocument();
    });

    await step("Verify extended FAB shows text", async () => {
      const navigateButton = canvas.getByLabelText("navigate");
      expect(navigateButton).toHaveTextContent("Navigate");
    });

    await step("Test FAB button interactions", async () => {
      const addButton = canvas.getByLabelText("add");
      const editButton = canvas.getByLabelText("edit");

      await userEvent.click(addButton);
      await expect(args.onClick).toHaveBeenCalledTimes(1);
      await userEvent.click(editButton);
      await expect(args.onClick).toHaveBeenCalledTimes(2);
    });
  },
};
