/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/SpeedDial/SpeedDial.stories.tsx
 * Upstream project license: MIT
 */

import FileCopyIcon from "@mui/icons-material/FileCopyOutlined";
import PrintIcon from "@mui/icons-material/Print";
import SaveIcon from "@mui/icons-material/Save";
import ShareIcon from "@mui/icons-material/Share";
import Box from "@mui/material/Box";
import SpeedDial from "@mui/material/SpeedDial";
import SpeedDialAction from "@mui/material/SpeedDialAction";
import SpeedDialIcon from "@mui/material/SpeedDialIcon";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, fn, userEvent, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/SpeedDial",
  component: SpeedDial,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    open: createBooleanArgType("If true, the component is shown.", false, "State"),
    hidden: createBooleanArgType("If true, the SpeedDial is hidden.", false, "State"),
    direction: createSelectArgType(
      ["up", "down", "left", "right"],
      "up",
      "The direction the actions open relative to the floating action button.",
      "Layout",
    ),
    ariaLabel: {
      control: "text",
      description: "The aria-label of the button element.",
      table: { category: "Accessibility" },
    },
    // Disable icon and children as they require JSX
    icon: { control: false },
    children: { control: false },
  },
} satisfies Meta<typeof SpeedDial>;

export default meta;
type Story = StoryObj<typeof meta>;

const actions = [
  { icon: <FileCopyIcon />, name: "Copy" },
  { icon: <SaveIcon />, name: "Save" },
  { icon: <PrintIcon />, name: "Print" },
  { icon: <ShareIcon />, name: "Share" },
];

export const Playground: Story = {
  args: {
    open: false,
    hidden: false,
    direction: "up",
    ariaLabel: "SpeedDial playground",
  },
  render: function Playground(args) {
    const [, updateArgs] = useArgs();
    return (
      <Box sx={{ height: 320, position: "relative" }}>
        <SpeedDial
          {...args}
          onOpen={() => updateArgs({ open: true })}
          onClose={() => updateArgs({ open: false })}
          sx={{ position: "absolute", bottom: 16, right: 16 }}
          icon={<SpeedDialIcon />}
        >
          {actions.map((action) => (
            <SpeedDialAction key={action.name} icon={action.icon} slotProps={{ tooltip: { title: action.name } }} />
          ))}
        </SpeedDial>
      </Box>
    );
  },
};

export const Default: Story = {
  args: {} as never,
  render: () => (
    <Box sx={{ height: 320, position: "relative" }}>
      <SpeedDial
        ariaLabel="SpeedDial example"
        sx={{ position: "absolute", bottom: 16, right: 16 }}
        icon={<SpeedDialIcon />}
      >
        {actions.map((action) => (
          <SpeedDialAction key={action.name} icon={action.icon} slotProps={{ tooltip: { title: action.name } }} />
        ))}
      </SpeedDial>
    </Box>
  ),
};

export function DirectionUp() {
  return (
    <Box sx={{ height: 320, position: "relative" }}>
      <SpeedDial
        ariaLabel="SpeedDial up"
        sx={{ position: "absolute", bottom: 16, right: 16 }}
        icon={<SpeedDialIcon />}
        direction="up"
      >
        {actions.map((action) => (
          <SpeedDialAction key={action.name} icon={action.icon} slotProps={{ tooltip: { title: action.name } }} />
        ))}
      </SpeedDial>
    </Box>
  );
}

export function DirectionLeft() {
  return (
    <Box sx={{ height: 100, position: "relative" }}>
      <SpeedDial
        ariaLabel="SpeedDial left"
        sx={{ position: "absolute", top: 16, right: 16 }}
        icon={<SpeedDialIcon />}
        direction="left"
      >
        {actions.map((action) => (
          <SpeedDialAction key={action.name} icon={action.icon} slotProps={{ tooltip: { title: action.name } }} />
        ))}
      </SpeedDial>
    </Box>
  );
}

export function Controlled() {
  const [open, setOpen] = React.useState(false);

  return (
    <Box sx={{ height: 320, position: "relative" }}>
      <SpeedDial
        ariaLabel="Controlled SpeedDial"
        sx={{ position: "absolute", bottom: 16, right: 16 }}
        icon={<SpeedDialIcon />}
        onClose={() => setOpen(false)}
        onOpen={() => setOpen(true)}
        open={open}
      >
        {actions.map((action) => (
          <SpeedDialAction
            key={action.name}
            icon={action.icon}
            slotProps={{ tooltip: { title: action.name } }}
            onClick={() => setOpen(false)}
          />
        ))}
      </SpeedDial>
    </Box>
  );
}

export function WithTooltips() {
  return (
    <Box sx={{ height: 320, position: "relative" }}>
      <SpeedDial
        ariaLabel="SpeedDial with tooltips"
        sx={{ position: "absolute", bottom: 16, right: 16 }}
        icon={<SpeedDialIcon />}
      >
        {actions.map((action) => (
          <SpeedDialAction
            key={action.name}
            icon={action.icon}
            slotProps={{ tooltip: { title: action.name, open: true } }}
          />
        ))}
      </SpeedDial>
    </Box>
  );
}

export const InteractionTest: Story = {
  args: {} as never,
  render: () => {
    const [open, setOpen] = React.useState(false);
    const handleOpen = fn(() => setOpen(true));
    const handleClose = fn(() => setOpen(false));
    const handleActionClick = fn(() => {
      setOpen(false);
    });

    return (
      <Box sx={{ height: 320, position: "relative" }} data-testid="speed-dial-container">
        <SpeedDial
          ariaLabel="SpeedDial interaction test"
          sx={{ position: "absolute", bottom: 16, right: 16 }}
          icon={<SpeedDialIcon />}
          onClose={handleClose}
          onOpen={handleOpen}
          open={open}
        >
          {actions.map((action) => (
            <SpeedDialAction
              key={action.name}
              icon={action.icon}
              slotProps={{ tooltip: { title: action.name } }}
              onClick={handleActionClick}
            />
          ))}
        </SpeedDial>
      </Box>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify SpeedDial renders with correct initial state
    const speedDialButton = canvas.getByRole("button", {
      name: /speeddial interaction test/i,
    });
    await expect(speedDialButton).toBeInTheDocument();
    await expect(speedDialButton).toHaveAttribute("aria-expanded", "false");
    speedDialButton.focus();
    await userEvent.keyboard("{Enter}");
    await expect(speedDialButton).toHaveAttribute("aria-expanded", "true");
    await userEvent.click(await canvas.findByRole("menuitem", { name: "Save" }));
    await expect(speedDialButton).toHaveAttribute("aria-expanded", "false");
  },
};
