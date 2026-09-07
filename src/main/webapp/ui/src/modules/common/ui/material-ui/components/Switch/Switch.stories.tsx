/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Switch/Switch.stories.tsx
 * Upstream project license: MIT
 */

import Switch from "@mui/material/Switch";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useArgs } from "storybook/preview-api";
import { expect, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  createSelectArgType,
  muiCheckedArgType,
  muiDisabledArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/Switch",
  component: Switch,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: createSelectArgType(
      ["primary", "secondary", "success", "error", "info", "warning", "default", "tertiary"] satisfies NonNullable<
        React.ComponentProps<typeof Switch>["color"]
      >[],
      "primary",
      "The color of the component.",
      "Appearance",
    ),
    size: {
      control: "radio",
      options: ["small", "medium"],
      description: "The size of the component.",
      table: {
        defaultValue: { summary: "medium" },
        category: "Appearance",
        type: { summary: '"small" | "medium"' },
      },
    },
    disabled: muiDisabledArgType,
    checked: muiCheckedArgType,
    defaultChecked: createBooleanArgType(
      "The default checked state. Use when the component is not controlled.",
      false,
      "State",
    ),
    disableRipple: createBooleanArgType("If true, the ripple effect is disabled.", false, "Appearance"),
  },
} satisfies Meta<typeof Switch>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: { checked: false, color: "primary", size: "medium" },
  render: function Playground(args) {
    const [, updateArgs] = useArgs();
    return (
      <Switch
        {...args}
        onChange={(_, checked) => updateArgs({ checked })}
        slotProps={{ input: { "aria-label": "Toggle switch" } }}
      />
    );
  },
};

export const Default: Story = {
  args: {} as never,
  render: (args) => <Switch {...args} slotProps={{ input: { "aria-label": "Default switch" } }} />,
};

export const Colors: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "8px" }}>
      <Switch defaultChecked color="primary" slotProps={{ input: { "aria-label": "Primary switch" } }} />
      <Switch defaultChecked color="secondary" slotProps={{ input: { "aria-label": "Secondary switch" } }} />
      <Switch defaultChecked color="success" slotProps={{ input: { "aria-label": "Success switch" } }} />
      <Switch defaultChecked color="error" slotProps={{ input: { "aria-label": "Error switch" } }} />
      <Switch defaultChecked color="info" slotProps={{ input: { "aria-label": "Info switch" } }} />
      <Switch defaultChecked color="warning" slotProps={{ input: { "aria-label": "Warning switch" } }} />
    </div>
  ),
};

export const Sizes: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
      <Switch defaultChecked size="small" slotProps={{ input: { "aria-label": "Small switch" } }} />
      <Switch defaultChecked size="medium" slotProps={{ input: { "aria-label": "Medium switch" } }} />
    </div>
  ),
};

export const Disabled: Story = {
  args: {} as never,
  render: () => <Switch disabled defaultChecked slotProps={{ input: { "aria-label": "Disabled switch" } }} />,
};

export const InteractionDemo: Story = {
  render: () => <Switch slotProps={{ input: { "aria-label": "Enable notifications" } }} />,
  play: async ({ canvasElement }) => {
    const control = within(canvasElement).getByRole("switch", { name: "Enable notifications" });
    await expect(control).not.toBeChecked();
    await userEvent.click(control);
    await expect(control).toBeChecked();
    await userEvent.keyboard(" ");
    await expect(control).not.toBeChecked();
  },
};
