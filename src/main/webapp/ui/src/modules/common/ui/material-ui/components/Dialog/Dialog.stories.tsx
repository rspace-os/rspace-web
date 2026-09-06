/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Dialog/Dialog.stories.tsx
 * Upstream project license: MIT
 */
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import TextField from "@mui/material/TextField";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { type FormEvent, useState } from "react";
import { useArgs } from "storybook/preview-api";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/Dialog",
  component: Dialog,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    fullWidth: createBooleanArgType("If true, the dialog stretches to maxWidth.", false, "Layout"),
    fullScreen: createBooleanArgType("If true, the dialog is full-screen.", false, "Layout"),
    maxWidth: createSelectArgType(
      ["xs", "sm", "md", "lg", "xl", false],
      "sm",
      "Determine the max-width of the dialog.",
      "Layout",
    ),
    scroll: createSelectArgType(
      ["paper", "body"],
      "paper",
      "Determine the container for scrolling the dialog.",
      "Layout",
    ),
    children: { control: false },
  },
} satisfies Meta<typeof Dialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    open: true,
    fullWidth: false,
    fullScreen: false,
    maxWidth: "sm",
    scroll: "paper",
  },
  render: function PlaygroundDialog(args) {
    const [, updateArgs] = useArgs();
    return (
      <>
        <Button onClick={() => updateArgs({ open: true })}>Open playground dialog</Button>
        <Dialog {...args} open={args.open} onClose={() => updateArgs({ open: false })}>
          <DialogTitle>Playground Dialog</DialogTitle>
          <DialogContent>
            <DialogContentText>
              This is an interactive playground for the Dialog component. Use the Controls panel to experiment with all
              props.
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => updateArgs({ open: false })}>Cancel</Button>
            <Button onClick={() => updateArgs({ open: false })}>Confirm</Button>
          </DialogActions>
        </Dialog>
      </>
    );
  },
};

export const Default: Story = {
  args: {} as never,
  render: () => {
    const [open, setOpen] = useState(false);

    const handleClickOpen = () => {
      setOpen(true);
    };

    const handleClose = () => {
      setOpen(false);
    };

    return (
      <>
        <Button variant="outlined" onClick={handleClickOpen}>
          Open dialog
        </Button>
        <Dialog open={open} onClose={handleClose}>
          <DialogTitle>Dialog Title</DialogTitle>
          <DialogContent>
            <DialogContentText>This is a simple dialog example. You can put any content here.</DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Close</Button>
          </DialogActions>
        </Dialog>
      </>
    );
  },
};

export const AlertDialog: Story = {
  args: {} as never,
  render: () => {
    const [open, setOpen] = useState(false);

    const handleClickOpen = () => {
      setOpen(true);
    };

    const handleClose = () => {
      setOpen(false);
    };

    return (
      <>
        <Button variant="outlined" onClick={handleClickOpen}>
          Open alert dialog
        </Button>
        <Dialog
          open={open}
          onClose={handleClose}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title">Use Google&apos;s location service?</DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Let Google help apps determine location. This means sending anonymous location data to Google, even when
              no apps are running.
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Disagree</Button>
            {/* eslint-disable-next-line jsx-a11y/no-autofocus */}
            <Button onClick={handleClose} autoFocus>
              Agree
            </Button>
          </DialogActions>
        </Dialog>
      </>
    );
  },
};

export const FormDialog: Story = {
  args: {} as never,
  render: () => {
    const [open, setOpen] = useState(false);

    const handleClickOpen = () => {
      setOpen(true);
    };

    const handleClose = () => {
      setOpen(false);
    };

    return (
      <>
        <Button variant="outlined" onClick={handleClickOpen}>
          Open form dialog
        </Button>
        <Dialog
          open={open}
          onClose={handleClose}
          slotProps={{
            paper: {
              component: "form",
              onSubmit: (event: FormEvent) => {
                event.preventDefault();
                handleClose();
              },
            },
          }}
        >
          <DialogTitle>Subscribe</DialogTitle>
          <DialogContent>
            <DialogContentText>
              To subscribe to this website, please enter your email address here. We will send updates occasionally.
            </DialogContentText>
            <TextField
              // eslint-disable-next-line jsx-a11y/no-autofocus
              autoFocus
              required
              margin="dense"
              id="name"
              name="email"
              label="Email Address"
              type="email"
              fullWidth
              variant="standard"
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit">Subscribe</Button>
          </DialogActions>
        </Dialog>
      </>
    );
  },
};

/**
 * Visual demonstration of dialog open/close behavior.
 * Use the button to test dialog interaction manually.
 */
export const OpenCloseDemo: Story = {
  args: {} as never,
  render: () => {
    const [open, setOpen] = useState(false);

    return (
      <>
        <Button variant="outlined" onClick={() => setOpen(true)}>
          Open Dialog
        </Button>
        <Dialog open={open} onClose={() => setOpen(false)}>
          <DialogTitle>Test Dialog</DialogTitle>
          <DialogContent>
            <DialogContentText>Dialog content for testing interaction</DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={() => setOpen(false)}>Confirm</Button>
          </DialogActions>
        </Dialog>
      </>
    );
  },
};
