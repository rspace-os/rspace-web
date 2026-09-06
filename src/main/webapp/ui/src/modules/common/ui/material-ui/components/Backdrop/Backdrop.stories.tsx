/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Backdrop/Backdrop.stories.tsx
 * Upstream project license: MIT
 */
import Backdrop from "@mui/material/Backdrop";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/Backdrop",
  component: Backdrop,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    invisible: createBooleanArgType(
      "If true, the backdrop is invisible. Useful for disabling click-away.",
      false,
      "Appearance",
    ),
    children: { control: false },
  },
} satisfies Meta<typeof Backdrop>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    open: true,
    invisible: false,
  },
  render: (args) => (
    <Backdrop {...args} sx={{ color: "#fff", zIndex: (theme) => theme.zIndex.drawer + 1 }}>
      <CircularProgress aria-label="Loading samples" color="inherit" />
    </Backdrop>
  ),
};

export const Default: Story = {
  args: {
    open: true,
    children: <CircularProgress aria-label="Loading samples" color="inherit" />,
  },
};

export function Basic() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Show Backdrop
      </Button>
      <Backdrop
        sx={{ color: "#fff", zIndex: (theme) => theme.zIndex.drawer + 1 }}
        open={open}
        onClick={() => setOpen(false)}
      >
        <Typography>Click to close</Typography>
      </Backdrop>
    </div>
  );
}

export function WithCircularProgress() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Show Loading
      </Button>
      <Backdrop
        sx={{ color: "#fff", zIndex: (theme) => theme.zIndex.drawer + 1 }}
        open={open}
        onClick={() => setOpen(false)}
      >
        <CircularProgress aria-label="Loading samples" color="inherit" />
      </Backdrop>
    </div>
  );
}

export function WithCustomContent() {
  const [open, setOpen] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpen(true)}>
        Show Custom Content
      </Button>
      <Backdrop
        sx={{ color: "#fff", zIndex: (theme) => theme.zIndex.drawer + 1 }}
        open={open}
        onClick={() => setOpen(false)}
      >
        <Box sx={{ textAlign: "center" }}>
          <CircularProgress aria-label="Loading samples" color="inherit" />
          <Typography sx={{ mt: 2 }}>Loading...</Typography>
        </Box>
      </Backdrop>
    </div>
  );
}

export const InteractionTest: Story = {
  args: {
    open: false,
  },
  render: () => {
    const [open, setOpen] = React.useState(false);

    return (
      <div>
        <Button variant="contained" onClick={() => setOpen(true)}>
          Show Backdrop
        </Button>
        <Backdrop
          sx={{ color: "#fff", zIndex: (theme) => theme.zIndex.drawer + 1 }}
          open={open}
          onClick={() => setOpen(false)}
          data-testid="test-backdrop"
        >
          <Typography>Click to close backdrop</Typography>
        </Backdrop>
      </div>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify button renders
    const button = canvas.getByRole("button", { name: /show backdrop/i });
    await userEvent.click(button);
    const backdrop = canvas.getByTestId("test-backdrop");
    await expect(backdrop).toBeVisible();
    await userEvent.click(backdrop);
    await waitFor(() => expect(backdrop).not.toBeVisible());
  },
};
