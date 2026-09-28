/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/SwipeableDrawer/SwipeableDrawer.stories.tsx
 * Upstream project license: MIT
 */

import MailIcon from "@mui/icons-material/Mail";
import InboxIcon from "@mui/icons-material/MoveToInbox";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import SwipeableDrawer from "@mui/material/SwipeableDrawer";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType, createNumberArgType, muiAnchorArgType, muiOpenArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/SwipeableDrawer",
  component: SwipeableDrawer,
  tags: ["autodocs"],
  parameters: {
    a11y: { test: "error" },
    layout: "centered",
  },
  argTypes: {
    anchor: muiAnchorArgType,
    open: muiOpenArgType,
    disableBackdropTransition: createBooleanArgType(
      "If true, the backdrop transition is disabled.",
      false,
      "Appearance",
    ),
    disableDiscovery: createBooleanArgType("If true, swipe discovery is disabled.", false, "State"),
    disableSwipeToOpen: createBooleanArgType("If true, swipe to open is disabled.", false, "State"),
    hysteresis: createNumberArgType("Swipe sensitivity (0 to 1).", 0.52, 0, 1),
    minFlingVelocity: createNumberArgType("Minimum velocity to trigger swipe.", 450, 0, 1000),
    swipeAreaWidth: createNumberArgType("Width of the swipe area (in px).", 20, 0, 100),
    // Callback props
    onOpen: { control: false, action: "opened" },
    onClose: { control: false, action: "closed" },
    children: { control: false },
  },
} satisfies Meta<typeof SwipeableDrawer>;

export default meta;
type Story = StoryObj<typeof meta>;

type Anchor = "top" | "left" | "bottom" | "right";

const DrawerContent = () => (
  <Box sx={{ width: 250 }} role="presentation">
    <List>
      {["Inbox", "Starred", "Send email", "Drafts"].map((text, index) => (
        <ListItem key={text} disablePadding>
          <ListItemButton>
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
          <ListItemButton>
            <ListItemIcon>{index % 2 === 0 ? <InboxIcon /> : <MailIcon />}</ListItemIcon>
            <ListItemText primary={text} />
          </ListItemButton>
        </ListItem>
      ))}
    </List>
  </Box>
);

export const Left: Story = {
  args: {
    anchor: "left",
    open: false,
    onOpen: () => {},
    onClose: () => {},
  },
  render: function LeftStory(args) {
    const [open, setOpen] = React.useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Left Drawer</Button>
        <SwipeableDrawer
          {...args}
          anchor="left"
          open={open}
          onClose={() => setOpen(false)}
          onOpen={() => setOpen(true)}
        >
          <DrawerContent />
        </SwipeableDrawer>
      </>
    );
  },
};

export const Right: Story = {
  args: {
    anchor: "right",
    open: false,
    onOpen: () => {},
    onClose: () => {},
  },
  render: function RightStory(args) {
    const [open, setOpen] = React.useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Right Drawer</Button>
        <SwipeableDrawer
          {...args}
          anchor="right"
          open={open}
          onClose={() => setOpen(false)}
          onOpen={() => setOpen(true)}
        >
          <DrawerContent />
        </SwipeableDrawer>
      </>
    );
  },
};

export const Top: Story = {
  args: {
    anchor: "top",
    open: false,
    onOpen: () => {},
    onClose: () => {},
  },
  render: function TopStory(args) {
    const [open, setOpen] = React.useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Top Drawer</Button>
        <SwipeableDrawer {...args} anchor="top" open={open} onClose={() => setOpen(false)} onOpen={() => setOpen(true)}>
          <Box sx={{ height: 200, p: 2 }}>
            <h3>Top Drawer Content</h3>
            <p>This drawer slides from the top.</p>
          </Box>
        </SwipeableDrawer>
      </>
    );
  },
};

export const Bottom: Story = {
  args: {
    anchor: "bottom",
    open: false,
    onOpen: () => {},
    onClose: () => {},
  },
  render: function BottomStory(args) {
    const [open, setOpen] = React.useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)}>Open Bottom Drawer</Button>
        <SwipeableDrawer
          {...args}
          anchor="bottom"
          open={open}
          onClose={() => setOpen(false)}
          onOpen={() => setOpen(true)}
        >
          <Box sx={{ height: 200, p: 2 }}>
            <h3>Bottom Drawer Content</h3>
            <p>This drawer slides from the bottom.</p>
          </Box>
        </SwipeableDrawer>
      </>
    );
  },
};

export const AllAnchors: Story = {
  args: {
    open: false,
    onOpen: () => {},
    onClose: () => {},
  },
  render: function AllAnchorsStory(args) {
    const [state, setState] = React.useState({
      top: false,
      left: false,
      bottom: false,
      right: false,
    });

    const toggleDrawer = (anchor: Anchor, open: boolean) => (event: React.KeyboardEvent | React.MouseEvent) => {
      if (
        event &&
        event.type === "keydown" &&
        ((event as React.KeyboardEvent).key === "Tab" || (event as React.KeyboardEvent).key === "Shift")
      ) {
        return;
      }
      setState({ ...state, [anchor]: open });
    };

    return (
      <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
        {(["left", "right", "top", "bottom"] as const).map((anchor) => (
          <React.Fragment key={anchor}>
            <Button onClick={toggleDrawer(anchor, true)}>{anchor.charAt(0).toUpperCase() + anchor.slice(1)}</Button>
            <SwipeableDrawer
              {...args}
              anchor={anchor}
              open={state[anchor]}
              onClose={toggleDrawer(anchor, false)}
              onOpen={toggleDrawer(anchor, true)}
            >
              {anchor === "top" || anchor === "bottom" ? (
                <Box sx={{ height: 200, p: 2 }}>
                  <h3>{anchor.charAt(0).toUpperCase() + anchor.slice(1)} Drawer</h3>
                </Box>
              ) : (
                <DrawerContent />
              )}
            </SwipeableDrawer>
          </React.Fragment>
        ))}
      </Box>
    );
  },
};

export const InteractionTest: Story = {
  args: {} as never,
  render: function InteractionStory(args) {
    const [open, setOpen] = React.useState(false);

    return (
      <>
        <Button onClick={() => setOpen(true)} data-testid="open-drawer">
          Open Drawer
        </Button>
        <SwipeableDrawer
          {...args}
          anchor="left"
          open={open}
          onClose={() => setOpen(false)}
          onOpen={() => setOpen(true)}
        >
          <DrawerContent />
        </SwipeableDrawer>
      </>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify open drawer button renders
    const openButton = canvas.getByTestId("open-drawer");
    await expect(openButton).toBeInTheDocument();
    await userEvent.click(openButton);
    const page = within(canvasElement.ownerDocument.body);
    await expect(await page.findByRole("button", { name: "Inbox" })).toBeVisible();
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(page.queryByRole("button", { name: "Inbox" })).not.toBeInTheDocument());
  },
};

/** The Controls panel and the drawer events update the same open state. */
export const Playground: Story = {
  args: { anchor: "left", open: false, onOpen: () => {}, onClose: () => {} },
  render: function Playground(args) {
    const [, updateArgs] = useArgs();
    return (
      <>
        <Button onClick={() => updateArgs({ open: true })}>Open drawer</Button>
        <SwipeableDrawer
          {...args}
          onOpen={() => updateArgs({ open: true })}
          onClose={() => updateArgs({ open: false })}
        >
          <DrawerContent />
        </SwipeableDrawer>
      </>
    );
  },
};
