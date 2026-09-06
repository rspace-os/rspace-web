/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Menu/Menu.stories.tsx
 * Upstream project license: MIT
 */
import ContentCopy from "@mui/icons-material/ContentCopy";
import ContentCut from "@mui/icons-material/ContentCut";
import ContentPaste from "@mui/icons-material/ContentPaste";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/Menu",
  component: Menu,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    autoFocus: createBooleanArgType("If true, will focus on the element at the start of the list.", true, "Behavior"),
    disableAutoFocusItem: createBooleanArgType(
      "If true, the menu will not automatically focus on items.",
      false,
      "Behavior",
    ),
    variant: createSelectArgType(["menu", "selectedMenu"], "selectedMenu", "The variant to use.", "Appearance"),
    // Disable anchorEl and children as they require JSX/refs
    anchorEl: { control: false },
    children: { control: false },
  },
} satisfies Meta<typeof Menu>;

export default meta;
type Story = StoryObj<typeof meta>;

function PlaygroundMenu({
  onOpenChange,
  ...args
}: React.ComponentProps<typeof Menu> & { onOpenChange: (open: boolean) => void }) {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const [open, setOpen] = React.useState(args.open);
  React.useEffect(() => setOpen(args.open), [args.open]);
  const changeOpen = (next: boolean) => {
    setOpen(next);
    onOpenChange(next);
  };

  return (
    <div>
      <Button
        variant="contained"
        onClick={(e) => {
          setAnchorEl(e.currentTarget);
          changeOpen(true);
        }}
      >
        Open Menu
      </Button>
      <Menu
        {...args}
        anchorEl={anchorEl}
        anchorReference={anchorEl ? "anchorEl" : "anchorPosition"}
        anchorPosition={{ top: 100, left: 100 }}
        open={open}
        onClose={() => changeOpen(false)}
      >
        <MenuItem onClick={() => changeOpen(false)}>Profile</MenuItem>
        <MenuItem onClick={() => changeOpen(false)}>My account</MenuItem>
        <MenuItem onClick={() => changeOpen(false)}>Logout</MenuItem>
      </Menu>
    </div>
  );
}

/**
 * Interactive playground for the Menu component.
 * Note: Menu requires an anchorEl, so this playground uses a render function.
 */
export const Playground: Story = {
  args: {
    open: false,
  },
  render: (args) => {
    const [, updateArgs] = useArgs();
    return <PlaygroundMenu {...args} onOpenChange={(open) => updateArgs({ open })} />;
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const page = within(canvasElement.ownerDocument.body);
    const trigger = canvas.getByRole("button", { name: "Open Menu" });
    await userEvent.click(trigger);
    await userEvent.click(await page.findByRole("menuitem", { name: "Profile" }));
    await waitFor(() => expect(page.queryByRole("menu")).not.toBeInTheDocument());
    await expect(trigger).toHaveFocus();
    await userEvent.click(trigger);
    await page.findByRole("menu");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(page.queryByRole("menu")).not.toBeInTheDocument());
    // Leave the overlay visible for the automatic accessibility scan.
    await userEvent.click(trigger);
    await waitFor(() => expect(page.getByRole("menu")).toBeVisible());
  },
};

export const Default: Story = { ...Playground };

export function Basic() {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  return (
    <div>
      <Button variant="contained" onClick={(e) => setAnchorEl(e.currentTarget)}>
        Dashboard
      </Button>
      <Menu anchorEl={anchorEl} open={open} onClose={() => setAnchorEl(null)}>
        <MenuItem onClick={() => setAnchorEl(null)}>Profile</MenuItem>
        <MenuItem onClick={() => setAnchorEl(null)}>My account</MenuItem>
        <MenuItem onClick={() => setAnchorEl(null)}>Logout</MenuItem>
      </Menu>
    </div>
  );
}

export function WithIcons() {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  return (
    <div>
      <Button variant="contained" onClick={(e) => setAnchorEl(e.currentTarget)}>
        Edit
      </Button>
      <Menu anchorEl={anchorEl} open={open} onClose={() => setAnchorEl(null)}>
        <MenuItem onClick={() => setAnchorEl(null)}>
          <ListItemIcon>
            <ContentCut fontSize="small" />
          </ListItemIcon>
          <ListItemText>Cut</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => setAnchorEl(null)}>
          <ListItemIcon>
            <ContentCopy fontSize="small" />
          </ListItemIcon>
          <ListItemText>Copy</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => setAnchorEl(null)}>
          <ListItemIcon>
            <ContentPaste fontSize="small" />
          </ListItemIcon>
          <ListItemText>Paste</ListItemText>
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => setAnchorEl(null)}>Select All</MenuItem>
      </Menu>
    </div>
  );
}

export function Dense() {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  return (
    <div>
      <Button variant="contained" onClick={(e) => setAnchorEl(e.currentTarget)}>
        Dense Menu
      </Button>
      <Menu anchorEl={anchorEl} open={open} onClose={() => setAnchorEl(null)}>
        <MenuItem dense onClick={() => setAnchorEl(null)}>
          Single
        </MenuItem>
        <MenuItem dense onClick={() => setAnchorEl(null)}>
          1.15
        </MenuItem>
        <MenuItem dense onClick={() => setAnchorEl(null)}>
          Double
        </MenuItem>
        <MenuItem dense onClick={() => setAnchorEl(null)}>
          Custom: 1.2
        </MenuItem>
      </Menu>
    </div>
  );
}

export const InteractionTest: Story = {
  args: {} as never,
  render: () => {
    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
    const open = Boolean(anchorEl);

    return (
      <div>
        <Button variant="contained" onClick={(e) => setAnchorEl(e.currentTarget)}>
          Open Menu
        </Button>
        <Menu anchorEl={anchorEl} open={open} onClose={() => setAnchorEl(null)}>
          <MenuItem onClick={() => setAnchorEl(null)}>Profile</MenuItem>
          <MenuItem onClick={() => setAnchorEl(null)}>My account</MenuItem>
          <MenuItem onClick={() => setAnchorEl(null)}>Logout</MenuItem>
        </Menu>
      </div>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const menuButton = canvas.getByRole("button", { name: /open menu/i });

    await userEvent.click(menuButton);

    const menu = await within(document.body).findByRole("menu");
    await expect(menu).toBeInTheDocument();

    const profileItem = within(menu).getByRole("menuitem", { name: /profile/i });
    await userEvent.click(profileItem);
    await waitFor(() => expect(within(document.body).queryByRole("menu")).not.toBeInTheDocument());
  },
};
