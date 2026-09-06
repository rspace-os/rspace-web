/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/AppBar/AppBar.stories.tsx
 * Upstream project license: MIT
 */
import MenuIcon from "@mui/icons-material/Menu";
import SearchIcon from "@mui/icons-material/Search";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { createBooleanArgType, createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Surfaces/AppBar",
  component: AppBar,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: {
      control: "select",
      options: ["default", "inherit", "primary", "secondary", "transparent"],
      description: "The color of the component.",
      table: {
        defaultValue: { summary: "primary" },
        category: "Appearance",
        type: {
          summary: '"default" | "inherit" | "primary" | "secondary" | "transparent"',
        },
      },
    },
    position: createSelectArgType(
      ["fixed", "absolute", "sticky", "static", "relative"],
      "fixed",
      "The positioning type.",
      "Layout",
    ),
    enableColorOnDark: createBooleanArgType("If true, the color prop is applied in dark mode.", false, "Appearance"),
    elevation: createNumberArgType("Shadow depth, corresponds to dp in the spec.", 4, 0, 24, "Appearance"),
    children: { control: false },
  },
} satisfies Meta<typeof AppBar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    color: "primary",
    position: "static",
    elevation: 4,
  },
  render: (args) => (
    <AppBar {...args}>
      <Toolbar>
        <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
          Playground AppBar
        </Typography>
      </Toolbar>
    </AppBar>
  ),
};

export const Default: Story = {
  render: () => (
    <AppBar position="static">
      <Toolbar>
        <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
          App Title
        </Typography>
      </Toolbar>
    </AppBar>
  ),
};

export function Basic() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            My Application
          </Typography>
        </Toolbar>
      </AppBar>
    </Box>
  );
}

export function WithMenuAndSearch() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <IconButton size="large" edge="start" color="inherit" aria-label="menu" sx={{ mr: 2 }}>
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            News
          </Typography>
          <IconButton size="large" color="inherit" aria-label="search">
            <SearchIcon />
          </IconButton>
        </Toolbar>
      </AppBar>
    </Box>
  );
}

export function SecondaryColor() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static" color="secondary">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Secondary Color
          </Typography>
        </Toolbar>
      </AppBar>
    </Box>
  );
}

export function Transparent() {
  return (
    <Box sx={{ flexGrow: 1, backgroundColor: "grey.200", p: 2 }}>
      <AppBar position="static" color="transparent" elevation={0}>
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Transparent AppBar
          </Typography>
        </Toolbar>
      </AppBar>
    </Box>
  );
}

export function Dense() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar variant="dense">
          <IconButton edge="start" color="inherit" aria-label="menu" sx={{ mr: 2 }}>
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" color="inherit" component="div">
            Dense Toolbar
          </Typography>
        </Toolbar>
      </AppBar>
    </Box>
  );
}

export const InteractionTest: Story = {
  args: { onClick: fn() },
  render: (args) => {
    return (
      <Box sx={{ flexGrow: 1 }}>
        <AppBar position="static">
          <Toolbar>
            <IconButton
              size="large"
              edge="start"
              color="inherit"
              aria-label="menu"
              onClick={args.onClick}
              sx={{ mr: 2 }}
            >
              <MenuIcon />
            </IconButton>
            <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
              Test AppBar
            </Typography>
            <IconButton size="large" color="inherit" aria-label="search" onClick={args.onClick}>
              <SearchIcon />
            </IconButton>
          </Toolbar>
        </AppBar>
      </Box>
    );
  },
  play: async ({ canvasElement, step, args }) => {
    const canvas = within(canvasElement);

    await step("Verify AppBar renders with title", async () => {
      const title = canvas.getByText("Test AppBar");
      expect(title).toBeInTheDocument();
    });

    await step("Verify menu and search buttons exist", async () => {
      const menuButton = canvas.getByLabelText("menu");
      const searchButton = canvas.getByLabelText("search");

      expect(menuButton).toBeInTheDocument();
      expect(searchButton).toBeInTheDocument();
    });

    await step("Test button interactions", async () => {
      const menuButton = canvas.getByLabelText("menu");
      const searchButton = canvas.getByLabelText("search");

      await userEvent.click(menuButton);
      await expect(args.onClick).toHaveBeenCalledTimes(1);
      await userEvent.click(searchButton);
      await expect(args.onClick).toHaveBeenCalledTimes(2);
    });
  },
};
