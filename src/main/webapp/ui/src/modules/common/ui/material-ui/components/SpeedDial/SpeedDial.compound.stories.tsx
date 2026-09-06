import Box from "@mui/material/Box";
import SpeedDial from "@mui/material/SpeedDial";
import SpeedDialAction from "@mui/material/SpeedDialAction";
import SpeedDialIcon from "@mui/material/SpeedDialIcon";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Navigation/SpeedDial/Composition",
  component: SpeedDial,
  parameters: { a11y: { test: "error" } },
  argTypes: { direction: { control: "select", options: ["up", "down", "left", "right"] } },
} satisfies Meta<typeof SpeedDial>;
export default meta;
type Story = StoryObj<typeof meta>;

/** SpeedDial supplies open state and keyboard navigation; each action needs a tooltip label. */
export const Composed: Story = {
  args: { ariaLabel: "Sample actions", direction: "up" },
  render: function SampleActions(args) {
    const [open, setOpen] = useState(false);
    const [message, setMessage] = useState("No action selected");
    return (
      <Box sx={{ height: 360, width: 500, position: "relative" }}>
        <p role="status">{message}</p>
        <SpeedDial
          {...args}
          sx={{ position: "absolute", top: 160, left: 220 }}
          icon={<SpeedDialIcon />}
          open={open}
          onOpen={() => setOpen(true)}
          onClose={() => setOpen(false)}
        >
          <SpeedDialAction
            icon={<span aria-hidden="true">+</span>}
            slotProps={{ tooltip: { title: "Add sample" } }}
            onClick={() => {
              setMessage("Sample added");
              setOpen(false);
            }}
          />
        </SpeedDial>
      </Box>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const trigger = canvas.getByRole("button", { name: "Sample actions" });
    trigger.focus();
    await userEvent.keyboard("{Enter}");
    await expect(trigger).toHaveAttribute("aria-expanded", "true");
    await userEvent.click(await canvas.findByRole("menuitem", { name: "Add sample" }));
    await expect(canvas.getByRole("status")).toHaveTextContent("Sample added");
    await expect(trigger).toHaveAttribute("aria-expanded", "false");
  },
};
