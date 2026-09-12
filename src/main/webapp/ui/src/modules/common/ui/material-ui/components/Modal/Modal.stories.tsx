/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Modal/Modal.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Fade from "@mui/material/Fade";
import Modal from "@mui/material/Modal";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Feedback/Modal",
  component: Modal,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    keepMounted: createBooleanArgType("Always keep the children in the DOM.", false, "Behavior"),
    disableAutoFocus: createBooleanArgType("If true, the modal will not automatically shift focus.", false, "Behavior"),
    disableEnforceFocus: createBooleanArgType(
      "If true, the modal will not prevent focus from leaving.",
      false,
      "Behavior",
    ),
    disableRestoreFocus: createBooleanArgType(
      "If true, the modal will not restore focus after closing.",
      false,
      "Behavior",
    ),
    hideBackdrop: createBooleanArgType("If true, the backdrop is not rendered.", false, "Appearance"),
    children: { control: false },
  },
} satisfies Meta<typeof Modal>;

export default meta;
type Story = StoryObj<typeof meta>;

const style = {
  position: "absolute" as const,
  top: "50%",
  left: "50%",
  transform: "translate(-50%, -50%)",
  width: 400,
  bgcolor: "background.paper",
  border: "2px solid #000",
  boxShadow: 24,
  p: 4,
};

export const Playground: Story = {
  args: { open: false, children: <Box /> },
  render: function PlaygroundModal(args) {
    const [, updateArgs] = useArgs();
    return (
      <>
        <Button onClick={() => updateArgs({ open: true })}>Open modal</Button>
        <Modal {...args} onClose={() => updateArgs({ open: false })}>
          <Box sx={style} role="dialog" aria-modal="true" aria-labelledby="playground-modal-title">
            <Typography id="playground-modal-title" variant="h6">
              Playground Modal
            </Typography>
            <Button onClick={() => updateArgs({ open: false })}>Close</Button>
          </Box>
        </Modal>
      </>
    );
  },
};

export const Default: Story = {
  args: {
    open: true,
    children: (
      <Box sx={style} role="dialog" aria-modal="true" aria-label="Example modal">
        <Typography variant="h6">Default Modal</Typography>
        <Typography sx={{ mt: 2 }}>This is a basic modal example.</Typography>
      </Box>
    ),
  },
};

export function BasicModal() {
  const [open, setOpen] = React.useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  return (
    <div>
      <Button variant="contained" onClick={handleOpen}>
        Open Modal
      </Button>
      <Modal
        open={open}
        onClose={handleClose}
        aria-labelledby="modal-modal-title"
        aria-describedby="modal-modal-description"
      >
        <Box sx={style} role="dialog" aria-modal="true" aria-label="Example modal">
          <Typography id="modal-modal-title" variant="h6" component="h2">
            Basic Modal
          </Typography>
          <Typography id="modal-modal-description" sx={{ mt: 2 }}>
            This is a basic modal with open/close functionality.
          </Typography>
        </Box>
      </Modal>
    </div>
  );
}

export function KeepMounted() {
  const [open, setOpen] = React.useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  return (
    <div>
      <Button variant="contained" onClick={handleOpen}>
        Open Keep Mounted Modal
      </Button>
      <Modal keepMounted open={open} onClose={handleClose} aria-labelledby="keep-mounted-modal-title">
        <Box sx={style} role="dialog" aria-modal="true" aria-label="Example modal">
          <Typography id="keep-mounted-modal-title" variant="h6" component="h2">
            Keep Mounted Modal
          </Typography>
          <Typography sx={{ mt: 2 }}>This modal stays in the DOM even when closed.</Typography>
        </Box>
      </Modal>
    </div>
  );
}

export function TransitionsModal() {
  const [open, setOpen] = React.useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  return (
    <div>
      <Button variant="contained" onClick={handleOpen}>
        Open Modal with Fade
      </Button>
      <Modal open={open} onClose={handleClose} closeAfterTransition aria-labelledby="transition-modal-title">
        <Fade in={open}>
          <Box sx={style} role="dialog" aria-modal="true" aria-label="Example modal">
            <Typography id="transition-modal-title" variant="h6" component="h2">
              Fade Transition
            </Typography>
            <Typography sx={{ mt: 2 }}>This modal uses a Fade transition effect.</Typography>
          </Box>
        </Fade>
      </Modal>
    </div>
  );
}

export const InteractionTest: Story = {
  args: {} as never,
  render: () => {
    const [open, setOpen] = React.useState(false);
    return (
      <div>
        <Button variant="contained" onClick={() => setOpen(true)}>
          Open Modal
        </Button>
        <Modal open={open} onClose={() => setOpen(false)}>
          <Box sx={style} role="dialog" aria-modal="true" aria-label="Example modal">
            <Typography variant="h6">Test Modal</Typography>
            <Button onClick={() => setOpen(false)}>Close</Button>
          </Box>
        </Modal>
      </div>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const openButton = canvas.getByRole("button", { name: /open modal/i });

    await userEvent.click(openButton);

    const modalContent = await within(document.body).findByText("Test Modal");
    await expect(modalContent).toBeInTheDocument();

    const closeButton = within(document.body).getByRole("button", {
      name: /close/i,
    });
    await userEvent.click(closeButton);
    await waitFor(() => expect(within(document.body).queryByText("Test Modal")).not.toBeInTheDocument());
    await expect(openButton).toHaveFocus();
  },
};

export function NestedModals() {
  const [openParent, setOpenParent] = React.useState(false);
  const [openChild, setOpenChild] = React.useState(false);

  return (
    <div>
      <Button variant="contained" onClick={() => setOpenParent(true)}>
        Open Parent Modal
      </Button>
      <Modal open={openParent} onClose={() => setOpenParent(false)} aria-labelledby="parent-modal-title">
        <Box sx={{ ...style, width: 500 }} role="dialog" aria-modal="true" aria-labelledby="parent-modal-title">
          <Typography id="parent-modal-title" variant="h6" component="h2">
            Parent Modal
          </Typography>
          <Typography sx={{ mt: 2 }}>Click below to open a nested modal.</Typography>
          <Button variant="outlined" onClick={() => setOpenChild(true)} sx={{ mt: 2 }}>
            Open Child Modal
          </Button>
          <Modal open={openChild} onClose={() => setOpenChild(false)} aria-labelledby="child-modal-title">
            <Box sx={{ ...style, width: 300 }} role="dialog" aria-modal="true" aria-labelledby="child-modal-title">
              <Typography id="child-modal-title" variant="h6" component="h2">
                Child Modal
              </Typography>
              <Typography sx={{ mt: 2 }}>This is a nested child modal.</Typography>
            </Box>
          </Modal>
        </Box>
      </Modal>
    </div>
  );
}
