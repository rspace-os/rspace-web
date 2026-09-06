/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Popover/Popover.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Popover from "@mui/material/Popover";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import * as React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, screen, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta: Meta<typeof Popover> = {
  title: "Material UI/Utils/Popover",
  component: Popover,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    anchorReference: createSelectArgType(
      ["anchorEl", "anchorPosition", "none"],
      "anchorEl",
      "This determines which anchor prop to refer to when setting the position of the popover.",
      "Layout",
    ),
    elevation: {
      control: { type: "number", min: 0, max: 24 },
      description: "The elevation of the popover.",
      table: {
        defaultValue: { summary: "8" },
        category: "Appearance",
        type: { summary: "number" },
      },
    },
    marginThreshold: {
      control: { type: "number", min: 0, max: 32 },
      description: "Specifies how close to the edge of the window the popover can appear.",
      table: {
        defaultValue: { summary: "16" },
        category: "Layout",
        type: { summary: "number" },
      },
    },
    disableScrollLock: createBooleanArgType("If true, the scroll lock behavior is disabled.", false, "Behavior"),
    // Disable complex props
    anchorEl: { control: false },
    anchorOrigin: { control: false },
    transformOrigin: { control: false },
    children: { control: false },
  },
};

export default meta;
type Story = StoryObj<typeof Popover>;

function PlaygroundPopover({
  onOpenChange,
  ...args
}: React.ComponentProps<typeof Popover> & { onOpenChange: (open: boolean) => void }) {
  const [anchorEl, setAnchorEl] = React.useState<HTMLButtonElement | null>(null);
  const [open, setOpen] = React.useState(args.open);
  React.useEffect(() => setOpen(args.open), [args.open]);
  const changeOpen = (next: boolean) => {
    setOpen(next);
    onOpenChange(next);
  };

  return (
    <div>
      <Button
        variant="contained"
        onClick={(e) => {
          setAnchorEl(e.currentTarget);
          changeOpen(true);
        }}
      >
        Open Popover
      </Button>
      <Popover
        {...args}
        anchorReference={anchorEl ? args.anchorReference : "anchorPosition"}
        anchorPosition={{ top: 100, left: 100 }}
        open={open}
        anchorEl={anchorEl}
        onClose={() => changeOpen(false)}
        elevation={args.elevation}
        marginThreshold={args.marginThreshold}
        disableScrollLock={args.disableScrollLock}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
      >
        <Typography sx={{ p: 2 }}>The content of the Popover.</Typography>
      </Popover>
    </div>
  );
}

export const Playground: Story = {
  args: {
    open: false,
    elevation: 8,
    marginThreshold: 16,
    disableScrollLock: false,
  },
  render: (args) => {
    const [, updateArgs] = useArgs();
    return <PlaygroundPopover {...args} onOpenChange={(open) => updateArgs({ open })} />;
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const page = within(canvasElement.ownerDocument.body);
    const trigger = canvas.getByRole("button", { name: "Open Popover" });
    await userEvent.click(trigger);
    await waitFor(() => expect(page.getByText("The content of the Popover.")).toBeVisible());
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(page.queryByText("The content of the Popover.")).not.toBeInTheDocument());
    await expect(trigger).toHaveFocus();
    // Leave the overlay visible for the automatic accessibility scan.
    await userEvent.click(trigger);
    await waitFor(() => expect(page.getByText("The content of the Popover.")).toBeVisible());
  },
};

export const Default: Story = {
  ...Playground,
};

/**
 * Basic popover that opens when clicking a button.
 * Click the button to open the popover, click outside to close it.
 */
export function Basic() {
  const [anchorEl, setAnchorEl] = React.useState<HTMLButtonElement | null>(null);

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const open = Boolean(anchorEl);
  const id = open ? "simple-popover" : undefined;

  return (
    <div>
      <Button aria-describedby={id} variant="contained" onClick={handleClick}>
        Open Popover
      </Button>
      <Popover
        id={id}
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
      >
        <Typography sx={{ p: 2 }}>The content of the Popover.</Typography>
      </Popover>
    </div>
  );
}

/**
 * Demonstrates different anchor positions.
 * Each button shows the popover at a different position relative to the anchor.
 */
export function AnchorPlayground() {
  const [anchorEl, setAnchorEl] = React.useState<HTMLButtonElement | null>(null);
  const [anchorOrigin, setAnchorOrigin] = React.useState<{
    vertical: "top" | "center" | "bottom";
    horizontal: "left" | "center" | "right";
  }>({
    vertical: "bottom",
    horizontal: "left",
  });

  const handleClick =
    (vertical: "top" | "center" | "bottom", horizontal: "left" | "center" | "right") =>
    (event: React.MouseEvent<HTMLButtonElement>) => {
      setAnchorOrigin({ vertical, horizontal });
      setAnchorEl(event.currentTarget);
    };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const open = Boolean(anchorEl);
  const id = open ? "anchor-playground-popover" : undefined;

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 2,
        alignItems: "flex-start",
      }}
    >
      <Box sx={{ display: "flex", gap: 1 }}>
        <Button variant="outlined" onClick={handleClick("top", "left")}>
          Top Left
        </Button>
        <Button variant="outlined" onClick={handleClick("top", "center")}>
          Top Center
        </Button>
        <Button variant="outlined" onClick={handleClick("top", "right")}>
          Top Right
        </Button>
      </Box>
      <Box sx={{ display: "flex", gap: 1 }}>
        <Button variant="outlined" onClick={handleClick("center", "left")}>
          Center Left
        </Button>
        <Button variant="outlined" onClick={handleClick("center", "center")}>
          Center Center
        </Button>
        <Button variant="outlined" onClick={handleClick("center", "right")}>
          Center Right
        </Button>
      </Box>
      <Box sx={{ display: "flex", gap: 1 }}>
        <Button variant="outlined" onClick={handleClick("bottom", "left")}>
          Bottom Left
        </Button>
        <Button variant="outlined" onClick={handleClick("bottom", "center")}>
          Bottom Center
        </Button>
        <Button variant="outlined" onClick={handleClick("bottom", "right")}>
          Bottom Right
        </Button>
      </Box>
      <Popover id={id} open={open} anchorEl={anchorEl} onClose={handleClose} anchorOrigin={anchorOrigin}>
        <Typography sx={{ p: 2 }}>
          Anchor: {anchorOrigin.vertical} {anchorOrigin.horizontal}
        </Typography>
      </Popover>
    </Box>
  );
}

/**
 * Popover that opens on mouse hover.
 * Hover over the text to display the popover.
 */
export const MouseOverPopover: Story = {
  render: function HoverPopover() {
    const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);

    const handlePopoverOpen = (event: React.MouseEvent<HTMLElement> | React.FocusEvent<HTMLElement>) => {
      setAnchorEl(event.currentTarget);
    };

    const handlePopoverClose = () => {
      setAnchorEl(null);
    };

    const open = Boolean(anchorEl);

    return (
      <div>
        <Typography
          component="div"
          aria-describedby={open ? "mouse-over-popover-description" : undefined}
          tabIndex={0}
          onFocus={handlePopoverOpen}
          onBlur={handlePopoverClose}
          onMouseEnter={handlePopoverOpen}
          onMouseLeave={(event) => {
            if (event.currentTarget !== event.currentTarget.ownerDocument.activeElement) handlePopoverClose();
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") handlePopoverClose();
          }}
          sx={{ cursor: "pointer", display: "inline-block" }}
        >
          <span>Hover with a Popover.</span>
        </Typography>
        <Popover
          id="mouse-over-popover"
          sx={{
            pointerEvents: "none",
          }}
          open={open}
          anchorEl={anchorEl}
          anchorOrigin={{
            vertical: "bottom",
            horizontal: "left",
          }}
          transformOrigin={{
            vertical: "top",
            horizontal: "left",
          }}
          onClose={handlePopoverClose}
          // Keep the trigger outside the modal manager's hidden siblings and retain keyboard focus.
          container={anchorEl}
          disableAutoFocus
          disableEnforceFocus
          disableScrollLock
          disableRestoreFocus
        >
          <Typography id="mouse-over-popover-description" sx={{ p: 1 }}>
            Hover or focus to show this information.
          </Typography>
        </Popover>
      </div>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const trigger = canvas.getByText("Hover with a Popover.", { exact: true }).closest("[tabindex]");
    if (!trigger) throw new Error("Popover trigger missing");
    await userEvent.tab();
    await expect(trigger).toHaveFocus();
    await waitFor(() => expect(canvas.getByText("Hover or focus to show this information.")).toBeVisible());
    await expect(trigger).toHaveAccessibleDescription("Hover or focus to show this information.");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(canvas.queryByText("Hover or focus to show this information.")).not.toBeInTheDocument());
    await expect(trigger).toHaveFocus();
    await userEvent.tab();
    await userEvent.hover(trigger);
    await waitFor(() => expect(canvas.getByText("Hover or focus to show this information.")).toBeVisible());
    await userEvent.unhover(trigger);
    await waitFor(() => expect(canvas.queryByText("Hover or focus to show this information.")).not.toBeInTheDocument());
  },
};

/**
 * Popover positioned relative to a virtual element.
 * Click anywhere in the box to open the popover at that position.
 */
export function VirtualElement() {
  const [anchorPosition, setAnchorPosition] = React.useState<{
    top: number;
    left: number;
  } | null>(null);

  const handleClick = (event: React.MouseEvent<HTMLDivElement>) => {
    setAnchorPosition({
      top: event.clientY,
      left: event.clientX,
    });
  };

  const handleClose = () => {
    setAnchorPosition(null);
  };

  const open = Boolean(anchorPosition);
  const id = open ? "virtual-element-popover" : undefined;

  return (
    <Box
      sx={{
        width: "100%",
        height: 300,
        border: "2px dashed",
        borderColor: "grey.400",
        borderRadius: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        cursor: "pointer",
        "&:hover": {
          bgcolor: "action.hover",
        },
      }}
      role="button"
      tabIndex={0}
      aria-label="Open popover at a position"
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          const rect = event.currentTarget.getBoundingClientRect();
          setAnchorPosition({ top: rect.top + rect.height / 2, left: rect.left + rect.width / 2 });
        }
      }}
      onClick={handleClick}
    >
      <Typography variant="h6" color="text.secondary">
        Click anywhere in this box
      </Typography>
      <Popover
        id={id}
        open={open}
        onClose={handleClose}
        anchorReference="anchorPosition"
        anchorPosition={anchorPosition ?? undefined}
      >
        <Typography sx={{ p: 2 }}>Positioned at click coordinates</Typography>
      </Popover>
    </Box>
  );
}

export const InteractionTest: Story = {
  render: () => {
    const [anchorEl, setAnchorEl] = React.useState<HTMLButtonElement | null>(null);

    const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
      setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
      setAnchorEl(null);
    };

    const open = Boolean(anchorEl);
    const id = open ? "test-popover" : undefined;

    return (
      <div>
        <Button aria-describedby={id} variant="contained" onClick={handleClick}>
          Open Test Popover
        </Button>
        <Popover
          id={id}
          open={open}
          anchorEl={anchorEl}
          onClose={handleClose}
          anchorOrigin={{
            vertical: "bottom",
            horizontal: "left",
          }}
        >
          <Typography sx={{ p: 2 }}>Test Popover Content</Typography>
        </Popover>
      </div>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify trigger button renders", async () => {
      const button = canvas.getByRole("button", { name: /open test popover/i });
      expect(button).toBeInTheDocument();
    });

    await step("Popover is initially closed", async () => {
      const popoverContent = screen.queryByText("Test Popover Content");
      expect(popoverContent).not.toBeInTheDocument();
    });

    await step("Open popover by clicking button", async () => {
      const button = canvas.getByRole("button", { name: /open test popover/i });
      await userEvent.click(button);

      // Popover renders in portal, use screen
      const popoverContent = await screen.findByText("Test Popover Content");
      expect(popoverContent).toBeInTheDocument();
    });

    await step("Close popover by pressing Escape", async () => {
      await userEvent.keyboard("{Escape}");

      // Wait for popover to close
      await waitFor(() => expect(screen.queryByText("Test Popover Content")).not.toBeInTheDocument());
    });
  },
};
