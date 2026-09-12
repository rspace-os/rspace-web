import ButtonBase from "@mui/material/ButtonBase";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Inputs/ButtonBase/Composition",
  component: ButtonBase,
  parameters: { a11y: { test: "error" } },
  argTypes: { disabled: { control: "boolean" } },
} satisfies Meta<typeof ButtonBase>;
export default meta;
type Story = StoryObj<typeof meta>;

/** ButtonBase provides keyboard activation and focus handling for a custom button. */
export const Composed: Story = {
  args: { disabled: false, onClick: fn() },
  render: (args) => (
    <ButtonBase {...args} sx={{ p: 2, border: 1 }}>
      Open sample
    </ButtonBase>
  ),
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    const button = canvas.getByRole("button", { name: "Open sample" });
    button.focus();
    await userEvent.keyboard("{Enter}");
    await expect(button).toHaveFocus();
    await expect(args.onClick).toHaveBeenCalledTimes(1);
  },
};
