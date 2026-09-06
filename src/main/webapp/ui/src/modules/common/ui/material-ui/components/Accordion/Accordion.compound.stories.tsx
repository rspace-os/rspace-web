import Accordion from "@mui/material/Accordion";
import AccordionActions from "@mui/material/AccordionActions";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import Button from "@mui/material/Button";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { type ComponentProps, useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Surfaces/Accordion/Composition",
  component: Accordion,
  parameters: { a11y: { test: "error" } },
  argTypes: { disabled: { control: "boolean" }, disableGutters: { control: "boolean" }, children: { control: false } },
} satisfies Meta<typeof Accordion>;
export default meta;
type Story = StoryObj<Omit<ComponentProps<typeof Accordion>, "children">>;

/** Summary, details, and actions share the expansion state supplied by Accordion. */
export const Composed: Story = {
  args: { disabled: false, disableGutters: false },
  render: function SampleAccordion(args) {
    const [saved, setSaved] = useState(false);
    return (
      <Accordion {...args}>
        <AccordionSummary id="compound-summary" aria-controls="compound-details">
          Sample details
        </AccordionSummary>
        <AccordionDetails id="compound-details">
          Storage temperature: 4°C.<p role="status">{saved ? "Sample saved" : "Unsaved sample"}</p>
        </AccordionDetails>
        <AccordionActions>
          <Button onClick={() => setSaved(true)}>Save</Button>
        </AccordionActions>
      </Accordion>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const summary = canvas.getByRole("button", { name: "Sample details" });
    await expect(summary).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(summary);
    await expect(summary).toHaveAttribute("aria-expanded", "true");
    await expect(canvas.getByText("Storage temperature: 4°C.")).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Save" }));
    await expect(canvas.getByRole("status")).toHaveTextContent("Sample saved");
    await userEvent.click(summary);
    await expect(summary).toHaveAttribute("aria-expanded", "false");
  },
};
