import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import { buttonVariants } from "./button";
import { Menu, MenuContent, MenuItem, MenuLinkItem, MenuSeparator, MenuTrigger } from "./menu";

const meta = {
  title: "DesignSystem/Menu",
  component: Menu,
  tags: ["autodocs"],
} satisfies Meta<typeof Menu>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Menu>
      <MenuTrigger className={buttonVariants({ variant: "outline" })}>
        Open menu
      </MenuTrigger>
      <MenuContent>
        <MenuLinkItem href="/profile">Profile</MenuLinkItem>
        <MenuLinkItem href="/settings">Settings</MenuLinkItem>
        <MenuSeparator />
        <MenuItem onClick={() => {}}>Log out</MenuItem>
      </MenuContent>
    </Menu>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const trigger = canvas.getByRole("button", { name: /open menu/i });
    await userEvent.click(trigger);

    const body = within(document.body);
    expect(await body.findByText("Log out")).toBeVisible();
    expect(body.getByText("Profile")).toBeVisible();
  },
};
