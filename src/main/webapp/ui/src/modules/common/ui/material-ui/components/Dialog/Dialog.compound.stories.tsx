import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";

const meta = {
  title: "Material UI/Feedback/Dialog/Composition",
  component: Dialog,
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: { control: false },
    fullWidth: { control: "boolean" },
    maxWidth: { control: "select", options: ["xs", "sm", "md"] },
  },
} satisfies Meta<typeof Dialog>;
export default meta;
type Story = StoryObj<typeof meta>;

/** Dialog labels its portal content and restores focus to its trigger when closed. */
export const Composed: Story = {
  args: { open: false, fullWidth: true, maxWidth: "sm" },
  render: function ConfirmDialog(args) {
    const [open, setOpen] = useState(args.open);
    return (
      <>
        <Button onClick={() => setOpen(true)}>Open dialog</Button>
        <Dialog
          {...args}
          open={open}
          onClose={() => setOpen(false)}
          aria-labelledby="compound-dialog-title"
          aria-describedby="compound-dialog-description"
        >
          <DialogTitle id="compound-dialog-title">Sample details</DialogTitle>
          <DialogContent>
            <DialogContentText id="compound-dialog-description">Keep this sample refrigerated.</DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpen(false)}>Close</Button>
          </DialogActions>
        </Dialog>
      </>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const page = within(canvasElement.ownerDocument.body);
    const trigger = canvas.getByRole("button", { name: "Open dialog" });
    await userEvent.click(trigger);
    const dialog = await page.findByRole("dialog", { name: "Sample details" });
    await expect(dialog).toHaveAccessibleDescription("Keep this sample refrigerated.");
    await userEvent.click(within(dialog).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(page.queryByRole("dialog")).not.toBeInTheDocument());
    await expect(trigger).toHaveFocus();
    await userEvent.click(trigger);
    await page.findByRole("dialog", { name: "Sample details" });
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(page.queryByRole("dialog")).not.toBeInTheDocument());
  },
};

/** Keep the portal open for visual review and automated accessibility checks. */
export const Open: Story = {
  ...Composed,
  args: { ...Composed.args, open: true },
  play: undefined,
};
