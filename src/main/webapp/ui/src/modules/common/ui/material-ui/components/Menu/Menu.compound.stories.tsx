import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Navigation/Menu/Composition",
  component: MenuList,
  parameters: { a11y: { test: "error" } },
  argTypes: { dense: { control: "boolean" } },
} satisfies Meta<typeof MenuList>;
export default meta;
type Story = StoryObj<typeof meta>;

/** MenuList provides arrow-key navigation between MenuItems. */
export const Composed: Story = {
  args: { dense: false },
  render: function ActionsMenu(args) {
    const [message, setMessage] = useState("Choose an action");
    return (
      <>
        <MenuList {...args} aria-label="Sample actions">
          <MenuItem onClick={() => setMessage("Sample opened")}>Open</MenuItem>
          <MenuItem onClick={() => setMessage("Sample copied")}>Copy</MenuItem>
        </MenuList>
        <p role="status">{message}</p>
      </>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    canvas.getByRole("menuitem", { name: "Open" }).focus();
    await userEvent.keyboard("{ArrowDown}");
    await expect(canvas.getByRole("menuitem", { name: "Copy" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    await expect(canvas.getByRole("status")).toHaveTextContent("Sample copied");
  },
};
