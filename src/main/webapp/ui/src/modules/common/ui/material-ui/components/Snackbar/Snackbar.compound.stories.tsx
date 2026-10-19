import Button from "@mui/material/Button";
import SnackbarContent from "@mui/material/SnackbarContent";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Feedback/Snackbar/Composition",
  component: SnackbarContent,
  parameters: { a11y: { test: "error" } },
  argTypes: { message: { control: "text" } },
} satisfies Meta<typeof SnackbarContent>;
export default meta;
type Story = StoryObj<typeof meta>;

/** SnackbarContent can render inline; Snackbar supplies positioning and dismissal in an application. */
export const Composed: Story = {
  args: { message: "Sample saved" },
  render: function UndoMessage(args) {
    const [undone, setUndone] = useState(false);
    return (
      <SnackbarContent
        {...args}
        message={undone ? "Save undone" : args.message}
        action={
          <Button color="inherit" disabled={undone} onClick={() => setUndone(true)}>
            Undo
          </Button>
        }
      />
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Undo" }));
    await expect(canvas.getByRole("alert")).toHaveTextContent("Save undone");
    await expect(canvas.getByRole("button", { name: "Undo" })).toBeDisabled();
  },
};
