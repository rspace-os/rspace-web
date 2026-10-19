import Box from "@mui/material/Box";
import Tab from "@mui/material/Tab";
import TabScrollButton, { type TabScrollButtonProps } from "@mui/material/TabScrollButton";
import Tabs from "@mui/material/Tabs";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, waitFor, within } from "storybook/test";

// TabScrollButton defaults to a div; expose named, keyboard-operable controls.
function ScrollButton(props: TabScrollButtonProps) {
  return (
    <TabScrollButton
      {...props}
      component="button"
      aria-label={props.direction === "left" ? "Scroll sections left" : "Scroll sections right"}
      aria-disabled={props.disabled}
      tabIndex={props.disabled ? -1 : 0}
      onClick={props.disabled ? undefined : props.onClick}
    />
  );
}

const meta = {
  title: "Material UI/Navigation/Tabs/Composition",
  component: Tabs,
  parameters: { a11y: { test: "error" } },
  argTypes: {
    textColor: { control: "select", options: ["primary", "secondary", "inherit"] },
    indicatorColor: { control: "select", options: ["primary", "secondary"] },
  },
} satisfies Meta<typeof Tabs>;
export default meta;
type Story = StoryObj<typeof meta>;

/** Tabs owns selection and scrolling; each Tab labels its corresponding panel. */
export const Composed: Story = {
  args: { textColor: "primary", indicatorColor: "primary" },
  render: function SampleTabs(args) {
    const [value, setValue] = useState(0);
    const labels = ["Overview", "Storage", "History", "Attachments", "Sharing"];
    return (
      <Box sx={{ width: 360 }}>
        <Tabs
          {...args}
          value={value}
          onChange={(_, next: number) => setValue(next)}
          variant="scrollable"
          scrollButtons
          allowScrollButtonsMobile
          aria-label="Sample sections"
          slots={{ scrollButtons: ScrollButton }}
        >
          {labels.map((label, index) => (
            <Tab key={label} label={label} id={`sample-tab-${index}`} aria-controls={`sample-panel-${index}`} />
          ))}
        </Tabs>
        {labels.map((label, index) => (
          <div
            key={label}
            role="tabpanel"
            id={`sample-panel-${index}`}
            aria-labelledby={`sample-tab-${index}`}
            hidden={value !== index}
          >
            {label} content
          </div>
        ))}
      </Box>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const left = canvas.getByRole("button", { name: "Scroll sections left" });
    const right = canvas.getByRole("button", { name: "Scroll sections right" });
    await expect(left).toHaveAttribute("aria-disabled", "true");
    await expect(left).toHaveAttribute("tabindex", "-1");
    await waitFor(() => expect(right).toHaveAttribute("aria-disabled", "false"));
    right.focus();
    await userEvent.keyboard("{Enter}");
    await waitFor(() => expect(left).toHaveAttribute("aria-disabled", "false"));
    await userEvent.click(canvas.getByRole("tab", { name: "Storage" }));
    await expect(canvas.getByRole("tab", { name: "Storage" })).toHaveAttribute("aria-selected", "true");
    await expect(canvas.getByRole("tabpanel", { name: "Storage" })).toHaveTextContent("Storage content");
    await userEvent.keyboard("{ArrowRight}");
    await expect(canvas.getByRole("tab", { name: "History" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    await expect(canvas.getByRole("tabpanel", { name: "History" })).toBeVisible();
  },
};
