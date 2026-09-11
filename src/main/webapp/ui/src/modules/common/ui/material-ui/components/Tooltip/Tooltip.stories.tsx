/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Tooltip/Tooltip.stories.tsx
 * Upstream project license: MIT
 */
import DeleteIcon from "@mui/icons-material/Delete";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType, createNumberArgType, muiPlacementArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Data Display/Tooltip",
  component: Tooltip,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    placement: muiPlacementArgType,
    arrow: createBooleanArgType("If true, adds an arrow to the tooltip.", false, "Appearance"),
    disableHoverListener: createBooleanArgType("Do not respond to hover events.", false, "Behavior"),
    disableFocusListener: createBooleanArgType("Do not respond to focus-visible events.", false, "Behavior"),
    disableTouchListener: createBooleanArgType("Do not respond to long press touch events.", false, "Behavior"),
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    enterDelay: createNumberArgType(
      "The number of milliseconds to wait before showing the tooltip.",
      100,
      0,
      2000,
      "Timing",
    ),
    leaveDelay: createNumberArgType(
      "The number of milliseconds to wait before hiding the tooltip.",
      0,
      0,
      2000,
      "Timing",
    ),
    title: {
      control: "text",
      description: "Tooltip title. Zero-length title strings are never displayed.",
      table: { category: "Content" },
    },
    children: { control: false },
  },
} satisfies Meta<typeof Tooltip>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    title: "Tooltip Text",
    placement: "bottom",
    arrow: false,
    enterDelay: 100,
    leaveDelay: 0,
    children: <Button>Hover me</Button>,
  },
};

export const Default: Story = {
  args: {
    title: "Default Tooltip",
    children: <Button>Hover me</Button>,
  },
};

export function Positions() {
  return (
    <Stack direction="row" spacing={2} sx={{ p: 4 }}>
      <Tooltip title="Top" placement="top">
        <Button>Top</Button>
      </Tooltip>
      <Tooltip title="Right" placement="right">
        <Button>Right</Button>
      </Tooltip>
      <Tooltip title="Bottom" placement="bottom">
        <Button>Bottom</Button>
      </Tooltip>
      <Tooltip title="Left" placement="left">
        <Button>Left</Button>
      </Tooltip>
    </Stack>
  );
}

export function Arrow() {
  return (
    <Stack direction="row" spacing={2}>
      <Tooltip title="With Arrow" arrow>
        <Button>Arrow</Button>
      </Tooltip>
      <Tooltip title="Without Arrow">
        <Button>No Arrow</Button>
      </Tooltip>
    </Stack>
  );
}

export function WithIconButton() {
  return (
    <Tooltip title="Delete">
      <IconButton>
        <DeleteIcon />
      </IconButton>
    </Tooltip>
  );
}

export function Delays() {
  return (
    <Stack direction="row" spacing={2}>
      <Tooltip title="Instant" enterDelay={0}>
        <Button>Instant</Button>
      </Tooltip>
      <Tooltip title="Delayed" enterDelay={500}>
        <Button>Delayed</Button>
      </Tooltip>
    </Stack>
  );
}

export const InteractionTest: Story = {
  args: {
    title: "Test Tooltip",
    children: <Button>Test</Button>,
  },
  render: () => (
    <Stack direction="row" spacing={2} sx={{ p: 4 }}>
      <Tooltip title="Hover tooltip text" arrow>
        <Button>Hover Me</Button>
      </Tooltip>
      <Tooltip title="Delete item" placement="top">
        <IconButton>
          <DeleteIcon />
        </IconButton>
      </Tooltip>
    </Stack>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    const trigger = canvas.getByRole("button", { name: "Hover tooltip text" });
    await userEvent.hover(trigger);
    const page = within(canvasElement.ownerDocument.body);
    await expect(await page.findByRole("tooltip")).toHaveTextContent("Hover tooltip text");
    await userEvent.unhover(trigger);
    await waitFor(() => expect(page.queryByRole("tooltip")).not.toBeInTheDocument());
    trigger.focus();
    await userEvent.keyboard("{Tab}");
    await expect(await page.findByRole("tooltip")).toHaveTextContent("Delete item");
  },
};

/** Disabled controls need a wrapper; describeChild adds help without replacing the visible label. */
export const DisabledTrigger: Story = {
  args: {
    title: "You need permission to delete",
    children: (
      <span>
        <Button disabled>Delete</Button>
      </span>
    ),
  },
};
export const Description: Story = {
  args: { title: "Permanently removes this sample", describeChild: true, children: <Button>Delete sample</Button> },
};
