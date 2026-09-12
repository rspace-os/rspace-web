import BottomNavigation from "@mui/material/BottomNavigation";
import BottomNavigationAction from "@mui/material/BottomNavigationAction";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Navigation/BottomNavigation/Composition",
  component: BottomNavigation,
  parameters: { a11y: { test: "error" } },
  argTypes: { showLabels: { control: "boolean" } },
} satisfies Meta<typeof BottomNavigation>;
export default meta;
type Story = StoryObj<typeof meta>;

/** BottomNavigation owns the selected value; each action supplies a distinct destination. */
export const Composed: Story = {
  args: { showLabels: true },
  render: function Navigation(args) {
    const [value, setValue] = useState("home");
    return (
      <>
        <BottomNavigation
          {...args}
          value={value}
          onChange={(_, next: string) => setValue(next)}
          aria-label="Main navigation"
        >
          <BottomNavigationAction label="Home" value="home" icon={<span aria-hidden="true">⌂</span>} />
          <BottomNavigationAction label="Saved" value="saved" icon={<span aria-hidden="true">★</span>} />
        </BottomNavigation>
        <p role="status">Current destination: {value}</p>
      </>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "Saved" }));
    await expect(canvas.getByRole("status")).toHaveTextContent("Current destination: saved");
  },
};
