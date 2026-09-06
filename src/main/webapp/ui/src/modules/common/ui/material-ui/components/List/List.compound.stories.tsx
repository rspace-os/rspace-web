import Avatar from "@mui/material/Avatar";
import IconButton from "@mui/material/IconButton";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemSecondaryAction from "@mui/material/ListItemSecondaryAction";
import ListItemText from "@mui/material/ListItemText";
import ListSubheader from "@mui/material/ListSubheader";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Data Display/List/Composition",
  component: List,
  parameters: { a11y: { test: "error" } },
  argTypes: { dense: { control: "boolean" } },
} satisfies Meta<typeof List>;
export default meta;
type Story = StoryObj<typeof meta>;

/** Use List for list semantics and keep each ListItemButton inside a ListItem. */
export const Composed: Story = {
  args: { dense: false },
  render: function SampleList(args) {
    const [selected, setSelected] = useState(false);
    const [message, setMessage] = useState("No action selected");
    return (
      <>
        <List {...args} aria-label="Samples" subheader={<ListSubheader>Samples</ListSubheader>}>
          <ListItem>
            <ListItemAvatar>
              <Avatar>A</Avatar>
            </ListItemAvatar>
            <ListItemText primary="Sample A" secondary="Refrigerated" />
            <ListItemSecondaryAction>
              <IconButton aria-label="More about Sample A" onClick={() => setMessage("Sample A actions opened")}>
                ⋯
              </IconButton>
            </ListItemSecondaryAction>
          </ListItem>
          <ListItem disablePadding>
            <ListItemButton
              selected={selected}
              onClick={() => {
                setSelected(!selected);
                setMessage(selected ? "Sample B deselected" : "Sample B selected");
              }}
            >
              <ListItemIcon>
                <span aria-hidden="true">★</span>
              </ListItemIcon>
              <ListItemText primary="Sample B" />
            </ListItemButton>
          </ListItem>
        </List>
        <p role="status">{message}</p>
      </>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Sample B" }));
    await expect(canvas.getByRole("status")).toHaveTextContent("Sample B selected");
    await userEvent.click(canvas.getByRole("button", { name: "Sample B" }));
    await expect(canvas.getByRole("status")).toHaveTextContent("Sample B deselected");
    await userEvent.click(canvas.getByRole("button", { name: "More about Sample A" }));
    await expect(canvas.getByRole("status")).toHaveTextContent("Sample A actions opened");
  },
};
