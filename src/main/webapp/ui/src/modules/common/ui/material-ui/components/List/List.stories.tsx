/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/List/List.stories.tsx
 * Upstream project license: MIT
 */
import DeleteIcon from "@mui/icons-material/Delete";
import DraftsIcon from "@mui/icons-material/Drafts";
import ExpandLess from "@mui/icons-material/ExpandLess";
import ExpandMore from "@mui/icons-material/ExpandMore";
import FolderIcon from "@mui/icons-material/Folder";
import InboxIcon from "@mui/icons-material/Inbox";
import SendIcon from "@mui/icons-material/Send";
import StarBorder from "@mui/icons-material/StarBorder";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, within } from "storybook/test";
import { createBooleanArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/List",
  component: List,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    dense: createBooleanArgType(
      "If true, compact vertical padding designed for keyboard and mouse input is used.",
      false,
      "Appearance",
    ),
    disablePadding: createBooleanArgType("If true, vertical padding is removed from the list.", false, "Layout"),
    subheader: {
      control: "text",
      description: "The content of the subheader, normally ListSubheader.",
      table: { category: "Content" },
    },
    children: { control: false },
  },
} satisfies Meta<typeof List>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    dense: false,
    disablePadding: false,
  },
  render: (args) => (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List {...args}>
        <ListItem>
          <ListItemText primary="Item 1" />
        </ListItem>
        <ListItem>
          <ListItemText primary="Item 2" />
        </ListItem>
        <ListItem>
          <ListItemText primary="Item 3" />
        </ListItem>
      </List>
    </Box>
  ),
};

export const Default: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        <ListItem>
          <ListItemText primary="Item 1" />
        </ListItem>
        <ListItem>
          <ListItemText primary="Item 2" />
        </ListItem>
        <ListItem>
          <ListItemText primary="Item 3" />
        </ListItem>
      </List>
    </Box>
  ),
};

export function WithIcons() {
  return (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        <ListItem disablePadding>
          <ListItemButton>
            <ListItemIcon>
              <InboxIcon />
            </ListItemIcon>
            <ListItemText primary="Inbox" />
          </ListItemButton>
        </ListItem>
        <ListItem disablePadding>
          <ListItemButton>
            <ListItemIcon>
              <DraftsIcon />
            </ListItemIcon>
            <ListItemText primary="Drafts" />
          </ListItemButton>
        </ListItem>
        <ListItem disablePadding>
          <ListItemButton>
            <ListItemIcon>
              <SendIcon />
            </ListItemIcon>
            <ListItemText primary="Send" />
          </ListItemButton>
        </ListItem>
      </List>
    </Box>
  );
}

export function WithAvatars() {
  return (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        <ListItem>
          <ListItemAvatar>
            <Avatar>
              <FolderIcon />
            </Avatar>
          </ListItemAvatar>
          <ListItemText primary="Photos" secondary="Jan 9, 2024" />
        </ListItem>
        <ListItem>
          <ListItemAvatar>
            <Avatar>
              <FolderIcon />
            </Avatar>
          </ListItemAvatar>
          <ListItemText primary="Work" secondary="Jan 7, 2024" />
        </ListItem>
      </List>
    </Box>
  );
}

export function NestedList() {
  const [open, setOpen] = React.useState(true);

  return (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        <ListItem disablePadding>
          <ListItemButton>
            <ListItemIcon>
              <SendIcon />
            </ListItemIcon>
            <ListItemText primary="Sent mail" />
          </ListItemButton>
        </ListItem>
        <ListItem disablePadding>
          <ListItemButton aria-expanded={open} aria-controls="nested-inbox" onClick={() => setOpen(!open)}>
            <ListItemIcon>
              <InboxIcon />
            </ListItemIcon>
            <ListItemText primary="Inbox" />
            {open ? <ExpandLess /> : <ExpandMore />}
          </ListItemButton>
        </ListItem>
        <ListItem disablePadding sx={{ display: "block" }}>
          <Collapse id="nested-inbox" in={open} timeout="auto" unmountOnExit>
            <List component="div" disablePadding>
              <ListItemButton sx={{ pl: 4 }}>
                <ListItemIcon>
                  <StarBorder />
                </ListItemIcon>
                <ListItemText primary="Starred" />
              </ListItemButton>
            </List>
          </Collapse>
        </ListItem>
      </List>
    </Box>
  );
}

export function WithCheckboxes() {
  const [checked, setChecked] = React.useState([0]);

  const handleToggle = (value: number) => () => {
    const currentIndex = checked.indexOf(value);
    const newChecked = [...checked];
    if (currentIndex === -1) {
      newChecked.push(value);
    } else {
      newChecked.splice(currentIndex, 1);
    }
    setChecked(newChecked);
  };

  return (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        {[0, 1, 2].map((value) => (
          <ListItem key={value} disablePadding>
            <ListItemText
              primary={
                <label htmlFor={`list-checkbox-${value}`} style={{ display: "flex", alignItems: "center" }}>
                  <Checkbox
                    id={`list-checkbox-${value}`}
                    checked={checked.includes(value)}
                    onChange={handleToggle(value)}
                  />
                  {`Line item ${value + 1}`}
                </label>
              }
            />
          </ListItem>
        ))}
      </List>
    </Box>
  );
}

export function WithSecondaryActions() {
  return (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        {[1, 2, 3].map((value) => (
          <ListItem
            key={value}
            secondaryAction={
              <IconButton edge="end" aria-label="delete">
                <DeleteIcon />
              </IconButton>
            }
          >
            <ListItemAvatar>
              <Avatar>
                <FolderIcon />
              </Avatar>
            </ListItemAvatar>
            <ListItemText primary={`Item ${value}`} secondary="Secondary text" />
          </ListItem>
        ))}
      </List>
    </Box>
  );
}

export function WithDividers() {
  return (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List>
        <ListItem divider>
          <ListItemText primary="Item 1" />
        </ListItem>
        <ListItem divider>
          <ListItemText primary="Item 2" />
        </ListItem>
        <ListItem>
          <ListItemText primary="Item 3" />
        </ListItem>
      </List>
    </Box>
  );
}

export const InteractionTest: Story = {
  render: () => (
    <Box sx={{ width: "100%", maxWidth: 360, bgcolor: "background.paper" }}>
      <List aria-label="test list">
        <ListItem>
          <ListItemText primary="First Item" secondary="First description" />
        </ListItem>
        <ListItem>
          <ListItemIcon>
            <InboxIcon />
          </ListItemIcon>
          <ListItemText primary="Second Item" secondary="With icon" />
        </ListItem>
        <ListItem>
          <ListItemText primary="Third Item" />
        </ListItem>
      </List>
    </Box>
  ),
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify list renders", async () => {
      const list = canvas.getByRole("list", { name: "test list" });
      await expect(list).toBeInTheDocument();
    });

    await step("Verify list items render", async () => {
      const listItems = canvas.getAllByRole("listitem");
      await expect(listItems).toHaveLength(3);
    });

    await step("Verify list item content", async () => {
      const firstItem = canvas.getByText("First Item");
      const secondItem = canvas.getByText("Second Item");
      const thirdItem = canvas.getByText("Third Item");
      await expect(firstItem).toBeInTheDocument();
      await expect(secondItem).toBeInTheDocument();
      await expect(thirdItem).toBeInTheDocument();
    });

    await step("Test accessibility", async () => {
      const list = canvas.getByRole("list");
      await expect(list).toHaveAttribute("aria-label", "test list");
    });
  },
};
