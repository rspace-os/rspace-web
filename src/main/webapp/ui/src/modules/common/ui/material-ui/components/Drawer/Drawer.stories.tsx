/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Drawer/Drawer.stories.tsx
 * Upstream project license: MIT
 */
import InboxIcon from "@mui/icons-material/Inbox";
import MailIcon from "@mui/icons-material/Mail";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import Drawer from "@mui/material/Drawer";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/Drawer",
  component: Drawer,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    anchor: createSelectArgType(
      ["left", "right", "top", "bottom"],
      "left",
      "Side from which the drawer will appear.",
      "Layout",
    ),
    variant: createSelectArgType(
      ["permanent", "persistent", "temporary"],
      "temporary",
      "The variant to use.",
      "Appearance",
    ),
    elevation: {
      control: { type: "number", min: 0, max: 24 },
      description: "The elevation of the drawer.",
      table: {
        category: "Appearance",
        defaultValue: { summary: "16" },
      },
    },
    children: { control: false },
  },
} satisfies Meta<typeof Drawer>;

export default meta;
type Story = StoryObj<typeof meta>;

function DrawerContent() {
  const [selected, setSelected] = React.useState("Inbox");
  return (
    <Box sx={{ width: 250 }}>
      <p role="status">Current folder: {selected}</p>
      <List>
        {["Inbox", "Starred", "Send email", "Drafts"].map((text, index) => (
          <ListItem key={text} disablePadding>
            <ListItemButton selected={selected === text} onClick={() => setSelected(text)}>
              <ListItemIcon>{index % 2 === 0 ? <InboxIcon /> : <MailIcon />}</ListItemIcon>
              <ListItemText primary={text} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
      <Divider />
      <List>
        {["All mail", "Trash", "Spam"].map((text, index) => (
          <ListItem key={text} disablePadding>
            <ListItemButton selected={selected === text} onClick={() => setSelected(text)}>
              <ListItemIcon>{index % 2 === 0 ? <InboxIcon /> : <MailIcon />}</ListItemIcon>
              <ListItemText primary={text} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Box>
  );
}

export const Playground: Story = {
  args: {
    open: true,
    anchor: "left",
    variant: "temporary",
  },
  render: function PlaygroundDrawer(args) {
    const [, updateArgs] = useArgs();
    return (
      <>
        <Button onClick={() => updateArgs({ open: true })}>Open playground drawer</Button>
        <Drawer
          {...args}
          open={args.open}
          onClose={() => updateArgs({ open: false })}
          slotProps={{ paper: { "aria-label": "Mail navigation" } }}
        >
          <DrawerContent />
        </Drawer>
      </>
    );
  },
};

export const Default: Story = {
  args: {
    open: true,
    children: <DrawerContent />,
    slotProps: { paper: { "aria-label": "Mail navigation" } },
  },
};

export function TemporaryLeft() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Open Left Drawer
      </Button>
      <Drawer
        slotProps={{ paper: { "aria-label": "Mail navigation" } }}
        anchor="left"
        open={open}
        onClose={() => setOpen(false)}
      >
        <DrawerContent />
      </Drawer>
    </div>
  );
}

export function TemporaryRight() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Open Right Drawer
      </Button>
      <Drawer
        slotProps={{ paper: { "aria-label": "Mail navigation" } }}
        anchor="right"
        open={open}
        onClose={() => setOpen(false)}
      >
        <DrawerContent />
      </Drawer>
    </div>
  );
}

export function TemporaryTop() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Open Top Drawer
      </Button>
      <Drawer
        slotProps={{ paper: { "aria-label": "Mail navigation" } }}
        anchor="top"
        open={open}
        onClose={() => setOpen(false)}
      >
        <Box sx={{ p: 2 }}>
          <List>
            {["Item 1", "Item 2", "Item 3"].map((text) => (
              <ListItem key={text}>
                <ListItemText primary={text} />
              </ListItem>
            ))}
          </List>
        </Box>
      </Drawer>
    </div>
  );
}

export function TemporaryBottom() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Open Bottom Drawer
      </Button>
      <Drawer
        slotProps={{ paper: { "aria-label": "Mail navigation" } }}
        anchor="bottom"
        open={open}
        onClose={() => setOpen(false)}
      >
        <Box sx={{ p: 2 }}>
          <List>
            {["Item 1", "Item 2", "Item 3"].map((text) => (
              <ListItem key={text}>
                <ListItemText primary={text} />
              </ListItem>
            ))}
          </List>
        </Box>
      </Drawer>
    </div>
  );
}

export function Permanent() {
  return (
    <Box sx={{ display: "flex" }}>
      <Drawer
        slotProps={{ paper: { "aria-label": "Mail navigation" } }}
        variant="permanent"
        sx={{
          width: 240,
          flexShrink: 0,
          "& .MuiDrawer-paper": {
            width: 240,
            boxSizing: "border-box",
            position: "relative",
          },
        }}
      >
        <DrawerContent />
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        Main content area
      </Box>
    </Box>
  );
}

export const InteractionTest: Story = {
  args: {} as never,
  render: () => {
    const [open, setOpen] = React.useState(false);
    const handleOpen = fn(() => setOpen(true));
    const handleClose = fn(() => setOpen(false));

    return (
      <div data-testid="drawer-container">
        <Button variant="contained" onClick={handleOpen}>
          Open Drawer
        </Button>
        <Drawer
          slotProps={{ paper: { "aria-label": "Mail navigation" } }}
          anchor="left"
          open={open}
          onClose={handleClose}
        >
          <DrawerContent />
        </Drawer>
      </div>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify the open drawer button renders
    const openButton = canvas.getByRole("button", { name: /open drawer/i });
    await userEvent.click(openButton);
    const page = within(canvasElement.ownerDocument.body);
    await expect(await page.findByRole("button", { name: "Inbox" })).toBeVisible();
    await userEvent.click(page.getByRole("button", { name: "Drafts" }));
    await expect(page.getByRole("status")).toHaveTextContent("Current folder: Drafts");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(page.queryByText("Inbox")).not.toBeInTheDocument());
    await expect(openButton).toHaveFocus();
  },
};
