/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Snackbar/Snackbar.stories.tsx
 * Upstream project license: MIT
 */

import CloseIcon from "@mui/icons-material/Close";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import Snackbar from "@mui/material/Snackbar";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType, createNumberArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/Snackbar",
  component: Snackbar,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    autoHideDuration: createNumberArgType(
      "The number of milliseconds to wait before automatically dismissing.",
      6000,
      1000,
      60000,
      "Timing",
    ),
    message: {
      control: "text",
      description: "The message to display.",
      table: { category: "Content" },
    },
    // Disable action and children as they require JSX
    action: { control: false },
    children: { control: false },
  },
} satisfies Meta<typeof Snackbar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    open: true,
    message: "This is a snackbar message",
    autoHideDuration: 6000,
  },
  render: function Playground(args) {
    const [, updateArgs] = useArgs();
    return <Snackbar {...args} onClose={() => updateArgs({ open: false })} />;
  },
};

export const Default: Story = {
  args: {
    open: true,
    message: "This is a snackbar message",
  },
};

export function Basic() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Open Snackbar
      </Button>
      <Snackbar open={open} autoHideDuration={6000} onClose={() => setOpen(false)} message="Note archived" />
    </div>
  );
}

export function WithSuccessAlert() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" color="success" onClick={() => setOpen(true)}>
        Show Success
      </Button>
      <Snackbar open={open} autoHideDuration={6000} onClose={() => setOpen(false)}>
        <Alert onClose={() => setOpen(false)} severity="success" sx={{ width: "100%" }}>
          This is a success message!
        </Alert>
      </Snackbar>
    </div>
  );
}

export function WithErrorAlert() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" color="error" onClick={() => setOpen(true)}>
        Show Error
      </Button>
      <Snackbar open={open} autoHideDuration={6000} onClose={() => setOpen(false)}>
        <Alert onClose={() => setOpen(false)} severity="error" sx={{ width: "100%" }}>
          This is an error message!
        </Alert>
      </Snackbar>
    </div>
  );
}

export function TopCenter() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Top Center
      </Button>
      <Snackbar
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
        open={open}
        autoHideDuration={6000}
        onClose={() => setOpen(false)}
        message="I am at the top center"
      />
    </div>
  );
}

export function WithAction() {
  const [open, setOpen] = React.useState(false);

  const action = (
    <React.Fragment>
      <Button color="secondary" size="small" onClick={() => setOpen(false)}>
        UNDO
      </Button>
      <IconButton size="small" aria-label="close" color="inherit" onClick={() => setOpen(false)}>
        <CloseIcon fontSize="small" />
      </IconButton>
    </React.Fragment>
  );

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Show with Action
      </Button>
      <Snackbar
        open={open}
        autoHideDuration={6000}
        onClose={() => setOpen(false)}
        message="Note archived"
        action={action}
      />
    </div>
  );
}

export const InteractionTest: Story = {
  args: {},
  render: () => {
    const [open, setOpen] = React.useState(false);
    const handleClose = fn(() => setOpen(false));

    return (
      <div>
        <Button variant="contained" onClick={() => setOpen(true)}>
          Open Snackbar
        </Button>
        <Snackbar
          open={open}
          autoHideDuration={6000}
          onClose={handleClose}
          message="This is a test snackbar message"
          action={
            <IconButton size="small" aria-label="close" color="inherit" onClick={handleClose}>
              <CloseIcon fontSize="small" />
            </IconButton>
          }
        />
      </div>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify open button renders", async () => {
      const openButton = canvas.getByRole("button", { name: /open snackbar/i });
      await expect(openButton).toBeInTheDocument();
    });

    await step("Test snackbar open interaction", async () => {
      const openButton = canvas.getByRole("button", { name: /open snackbar/i });
      await userEvent.click(openButton);

      // Snackbar renders in portal, use screen instead of canvas
      const snackbarMessage = await screen.findByText(/this is a test snackbar message/i);
      await expect(snackbarMessage).toBeInTheDocument();
    });

    await step("Test snackbar close button", async () => {
      // Find close button in the snackbar portal
      const closeButton = await screen.findByRole("button", { name: /close/i });
      await expect(closeButton).toBeInTheDocument();
      await userEvent.click(closeButton);
      await waitFor(() => expect(screen.queryByText(/this is a test snackbar message/i)).not.toBeInTheDocument());
    });
  },
};
