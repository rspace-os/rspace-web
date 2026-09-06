/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Toolbar/Toolbar.stories.tsx
 * Upstream project license: MIT
 */
import MenuIcon from "@mui/icons-material/Menu";
import SearchIcon from "@mui/icons-material/Search";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import InputBase from "@mui/material/InputBase";
import { alpha, styled } from "@mui/material/styles";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { createBooleanArgType, muiVariantArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/Toolbar",
  component: Toolbar,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: muiVariantArgType(["regular", "dense"], "regular"),
    disableGutters: createBooleanArgType("If true, disables gutter padding.", false, "Layout"),
    children: { control: false },
  },
} satisfies Meta<typeof Toolbar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    children: (
      <>
        <IconButton size="large" edge="start" color="inherit" aria-label="menu" sx={{ mr: 2 }}>
          <MenuIcon />
        </IconButton>
        <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
          Toolbar
        </Typography>
      </>
    ),
  },
  decorators: [
    (Story) => (
      <Box sx={{ flexGrow: 1 }}>
        <AppBar position="static">
          <Story />
        </AppBar>
      </Box>
    ),
  ],
};

export const Variants: Story = {
  args: {},
  render: () => (
    <Box sx={{ flexGrow: 1, display: "flex", flexDirection: "column", gap: 2 }}>
      <AppBar component="div" position="static">
        <Toolbar variant="regular">
          <Typography>Regular Toolbar</Typography>
        </Toolbar>
      </AppBar>
      <AppBar component="div" position="static">
        <Toolbar variant="dense">
          <Typography>Dense Toolbar</Typography>
        </Toolbar>
      </AppBar>
    </Box>
  ),
};

const Search = styled("div")(({ theme }) => ({
  position: "relative",
  borderRadius: theme.shape.borderRadius,
  backgroundColor: alpha(theme.palette.common.white, 0.15),
  "&:hover": {
    backgroundColor: alpha(theme.palette.common.white, 0.25),
  },
  marginLeft: 0,
  width: "100%",
  [theme.breakpoints.up("sm")]: {
    marginLeft: theme.spacing(1),
    width: "auto",
  },
}));

const SearchIconWrapper = styled("div")(({ theme }) => ({
  padding: theme.spacing(0, 2),
  height: "100%",
  position: "absolute",
  pointerEvents: "none",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
}));

const StyledInputBase = styled(InputBase)(({ theme }) => ({
  color: "inherit",
  width: "100%",
  "& .MuiInputBase-input": {
    padding: theme.spacing(1, 1, 1, 0),
    paddingLeft: `calc(1em + ${theme.spacing(4)})`,
    transition: theme.transitions.create("width"),
    [theme.breakpoints.up("sm")]: {
      width: "12ch",
      "&:focus": {
        width: "20ch",
      },
    },
  },
}));

export const WithSearch: Story = {
  args: {},
  render: () => (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <IconButton size="large" edge="start" color="inherit" aria-label="open drawer" sx={{ mr: 2 }}>
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1, display: { xs: "none", sm: "block" } }}>
            MUI
          </Typography>
          <Search>
            <SearchIconWrapper>
              <SearchIcon />
            </SearchIconWrapper>
            <StyledInputBase placeholder="Search…" inputProps={{ "aria-label": "search" }} />
          </Search>
        </Toolbar>
      </AppBar>
    </Box>
  ),
};

export const DisableGutters: Story = {
  args: {},
  render: () => (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar disableGutters>
          <Typography variant="h6" sx={{ pl: 2 }}>
            No Gutters Toolbar
          </Typography>
        </Toolbar>
      </AppBar>
    </Box>
  ),
};

export const InteractionTest: Story = {
  args: {
    children: (
      <>
        <IconButton size="large" edge="start" color="inherit" aria-label="menu" sx={{ mr: 2 }}>
          <MenuIcon />
        </IconButton>
        <Typography variant="h6" component="div">
          Test Toolbar
        </Typography>
      </>
    ),
  },
  decorators: [
    (Story) => (
      <Box sx={{ flexGrow: 1 }}>
        <AppBar position="static">
          <Story />
        </AppBar>
      </Box>
    ),
  ],
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    const menuButton = canvas.getByRole("button", { name: /menu/i });
    await expect(menuButton).toBeInTheDocument();

    const title = canvas.getByText("Test Toolbar");
    await expect(title).toBeInTheDocument();
  },
};
