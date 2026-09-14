/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Button/Button.stories.tsx
 * Upstream project license: MIT
 */

import Button from "@mui/material/Button";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  createSelectArgType,
  muiDisabledArgType,
  muiFullWidthArgType,
  muiSizeArgType,
  muiVariantArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/Button",
  component: Button,
  tags: ["autodocs"],
  parameters: {
    a11y: {
      test: "error",
      config: {
        rules: [
          { id: "color-contrast", enabled: true },
          { id: "button-name", enabled: true },
        ],
      },
    },
  },
  argTypes: {
    variant: muiVariantArgType(["text", "contained", "outlined"], "text"),
    color: createSelectArgType(
      [
        "primary",
        "secondary",
        "success",
        "error",
        "info",
        "warning",
        "inherit",
        "standardIcon",
        "callToAction",
      ] satisfies NonNullable<React.ComponentProps<typeof Button>["color"]>[],
      "standardIcon",
      "The color of the component.",
      "Appearance",
    ),
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    fullWidth: muiFullWidthArgType,
    disableElevation: createBooleanArgType("If true, no elevation is used.", false, "Appearance"),
    disableRipple: createBooleanArgType("If true, the ripple effect is disabled.", false, "Appearance"),
    children: {
      control: "text",
      description: "The content of the button.",
      table: { category: "Content" },
    },
    href: {
      control: "text",
      description: "The URL to link to when the button is clicked.",
      table: { category: "Navigation" },
    },
    // Disable non-controllable props (icons need JSX)
    startIcon: { control: false, table: { category: "Content" } },
    endIcon: { control: false, table: { category: "Content" } },
  },
  args: {
    children: "Button",
    onClick: fn(),
  },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    children: "Click Me",
    variant: "contained",
    color: "primary",
    size: "medium",
    disabled: false,
    fullWidth: false,
  },
};

export const Default: Story = {
  args: {
    children: "Default Button",
  },
};

export const ClickInteraction: Story = {
  args: {
    children: "Click Me",
    onClick: fn(),
  },
  play: async ({ args, canvasElement }) => {
    const canvas = within(canvasElement);
    const button = canvas.getByRole("button", { name: /click me/i });

    await expect(button).toBeInTheDocument();
    await userEvent.click(button);
    await expect(args.onClick).toHaveBeenCalledTimes(1);
    await userEvent.keyboard("{Enter}");
    await expect(args.onClick).toHaveBeenCalledTimes(2);
  },
};

export const Variants: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "16px" }}>
      <Button variant="text">Text</Button>
      <Button variant="contained">Contained</Button>
      <Button variant="outlined">Outlined</Button>
    </div>
  ),
};

export const Colors: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
      <Button variant="contained" color="primary">
        Primary
      </Button>
      <Button variant="contained" color="secondary">
        Secondary
      </Button>
      <Button variant="contained" color="success">
        Success
      </Button>
      <Button variant="contained" color="error">
        Error
      </Button>
      <Button variant="contained" color="info">
        Info
      </Button>
      <Button variant="contained" color="warning">
        Warning
      </Button>
    </div>
  ),
};

export const Sizes: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
      <Button variant="contained" size="small">
        Small
      </Button>
      <Button variant="contained" size="medium">
        Medium
      </Button>
      <Button variant="contained" size="large">
        Large
      </Button>
    </div>
  ),
};

export const AccessibilityDemo: Story = {
  args: {
    children: "Accessible Button",
    variant: "contained",
  },
  parameters: {
    docs: {
      description: {
        story: "This button demonstrates accessibility best practices with proper color contrast and clear labeling.",
      },
    },
  },
};

export function IconButtonAccessible() {
  return <Button aria-label="Add item">+ Add</Button>;
}

export const Disabled: Story = {
  args: { children: "Unavailable action", disabled: true, onClick: fn() },
  play: async ({ canvasElement, args }) => {
    const button = within(canvasElement).getByRole("button", { name: "Unavailable action" });
    await expect(button).toBeDisabled();
    button.focus();
    await expect(button).not.toHaveFocus();
    await userEvent.keyboard("{Enter}");
    await expect(args.onClick).not.toHaveBeenCalled();
  },
};
