/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Alert/Alert.stories.tsx
 * Upstream project license: MIT
 */
import CheckIcon from "@mui/icons-material/Check";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutlineOutlined";
import CloseIcon from "@mui/icons-material/Close";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";
import { createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/Alert",
  component: Alert,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    severity: createSelectArgType(
      ["error", "warning", "info", "success"],
      "success",
      "The severity of the alert. This defines the color and icon used.",
      "Appearance",
    ),
    variant: createSelectArgType(["standard", "outlined", "filled"], "standard", "The variant to use.", "Appearance"),
    color: createSelectArgType(
      ["error", "warning", "info", "success"],
      "success",
      "The color of the component. Unless provided, the value is taken from the severity prop.",
      "Appearance",
    ),
  },
} satisfies Meta<typeof Alert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    severity: "success",
    variant: "standard",
  },
  render: (args) => <Alert {...args}>This is an alert — check it out!</Alert>,
};

export const Default: Story = {
  args: {
    severity: "success",
    variant: "standard",
  },
  render: (args) => <Alert {...args}>This is an alert — check it out!</Alert>,
};

export function BasicAlerts1() {
  return (
    <Stack sx={{ width: "100%" }} spacing={2}>
      <Alert severity="error">This is an error alert — check it out!</Alert>
      <Alert severity="warning">This is a warning alert — check it out!</Alert>
      <Alert severity="info">This is an info alert — check it out!</Alert>
      <Alert severity="success">This is a success alert — check it out!</Alert>
    </Stack>
  );
}

export function BasicAlerts2() {
  return (
    <Stack sx={{ width: "100%" }} spacing={2}>
      <Alert variant="outlined" severity="error">
        This is an error alert — check it out!
      </Alert>
      <Alert variant="outlined" severity="warning">
        This is a warning alert — check it out!
      </Alert>
      <Alert variant="outlined" severity="info">
        This is an info alert — check it out!
      </Alert>
      <Alert variant="outlined" severity="success">
        This is a success alert — check it out!
      </Alert>
    </Stack>
  );
}

export function DescriptionAlerts() {
  return (
    <Stack sx={{ width: "100%" }} spacing={2}>
      <Alert severity="error">
        <AlertTitle>Error</AlertTitle>
        This is an error alert — <strong>check it out!</strong>
      </Alert>
      <Alert severity="warning">
        <AlertTitle>Warning</AlertTitle>
        This is a warning alert — <strong>check it out!</strong>
      </Alert>
      <Alert severity="info">
        <AlertTitle>Info</AlertTitle>
        This is an info alert — <strong>check it out!</strong>
      </Alert>
      <Alert severity="success">
        <AlertTitle>Success</AlertTitle>
        This is a success alert — <strong>check it out!</strong>
      </Alert>
    </Stack>
  );
}
export function ActionAlerts() {
  return (
    <Stack sx={{ width: "100%" }} spacing={2}>
      <Alert onClose={() => {}}>This is a success alert — check it out!</Alert>
      <Alert
        action={
          <Button color="inherit" size="small">
            UNDO
          </Button>
        }
      >
        This is a success alert — check it out!
      </Alert>
    </Stack>
  );
}

export function TransitionAlerts() {
  const [open, setOpen] = React.useState(true);

  return (
    <Box sx={{ width: "100%" }}>
      <Collapse in={open}>
        <Alert
          action={
            <IconButton
              aria-label="close"
              color="inherit"
              size="small"
              onClick={() => {
                setOpen(false);
              }}
            >
              <CloseIcon fontSize="inherit" />
            </IconButton>
          }
          sx={{ mb: 2 }}
        >
          Close me!
        </Alert>
      </Collapse>
      <Button
        disabled={open}
        variant="outlined"
        onClick={() => {
          setOpen(true);
        }}
      >
        Re-open
      </Button>
    </Box>
  );
}

export function IconAlerts() {
  return (
    <Stack sx={{ width: "100%" }} spacing={2}>
      <Alert icon={<CheckIcon fontSize="inherit" />} severity="success">
        This is a success alert — check it out!
      </Alert>
      <Alert
        iconMapping={{
          success: <CheckCircleOutlineIcon fontSize="inherit" />,
        }}
      >
        This is a success alert — check it out!
      </Alert>
      <Alert icon={false} severity="success">
        This is a success alert — check it out!
      </Alert>
    </Stack>
  );
}

export function ColorAlerts() {
  return (
    <Alert severity="success" color="info">
      This is a success alert — check it out!
    </Alert>
  );
}

export const InteractionTest: StoryObj<typeof Alert> = {
  args: { onClose: fn(), variant: "standard" },
  render: (args) => {
    return (
      <Stack sx={{ width: "100%" }} spacing={2}>
        <Alert severity="success">This is a success alert!</Alert>
        <Alert severity="error" onClose={args.onClose}>
          This is a closeable error alert!
        </Alert>
        <Alert severity="warning">
          <AlertTitle>Warning</AlertTitle>
          This alert has a title
        </Alert>
      </Stack>
    );
  },
  play: async ({ canvasElement, step, args }) => {
    const canvas = within(canvasElement);

    await step("Verify alerts render with correct severities", async () => {
      const successAlert = canvas.getByText("This is a success alert!");
      await expect(successAlert).toBeInTheDocument();

      const errorAlert = canvas.getByText("This is a closeable error alert!");
      await expect(errorAlert).toBeInTheDocument();

      const warningTitle = canvas.getByText("Warning");
      await expect(warningTitle).toBeInTheDocument();
    });

    await step("Test alert close button", async () => {
      const closeButton = canvas.getByRole("button", { name: /close/i });
      await expect(closeButton).toBeInTheDocument();
      await userEvent.click(closeButton);
      await expect(args.onClose).toHaveBeenCalledTimes(1);
    });

    await step("Verify alert title functionality", async () => {
      const alertTitle = canvas.getByText("Warning");
      await expect(alertTitle).toBeInTheDocument();
      await expect(alertTitle.tagName).toBe("DIV");
    });
  },
};

export const DismissAndReopen: Story = {
  args: { variant: "standard" },
  render: () => <TransitionAlerts />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "close" }));
    await waitFor(() => expect(canvas.getByText("Close me!")).not.toBeVisible());
    await userEvent.click(canvas.getByRole("button", { name: "Re-open" }));
    await expect(canvas.getByText("Close me!")).toBeVisible();
  },
};
