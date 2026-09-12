/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/TextField/TextField.stories.tsx
 * Upstream project license: MIT
 */

import TextField from "@mui/material/TextField";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  createSelectArgType,
  muiDisabledArgType,
  muiErrorArgType,
  muiFullWidthArgType,
  muiRequiredArgType,
  muiVariantArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/TextField",
  component: TextField,
  tags: ["autodocs"],
  parameters: {
    a11y: {
      test: "error",
      config: {
        rules: [
          { id: "label", enabled: true },
          { id: "color-contrast", enabled: true },
        ],
      },
    },
  },
  argTypes: {
    variant: muiVariantArgType(["outlined", "filled", "standard"], "outlined"),
    color: createSelectArgType(
      ["primary", "secondary", "success", "error", "info", "warning"] satisfies NonNullable<
        React.ComponentProps<typeof TextField>["color"]
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
    required: muiRequiredArgType,
    error: muiErrorArgType,
    fullWidth: muiFullWidthArgType,
    multiline: createBooleanArgType("If true, a textarea element is rendered instead of an input.", false, "Layout"),
    margin: createSelectArgType(
      ["none", "dense", "normal"],
      "none",
      "If dense or normal, will adjust vertical spacing.",
      "Layout",
    ),
    type: createSelectArgType(
      ["text", "password", "email", "number", "tel", "url", "search"],
      "text",
      "Type of the input element.",
      "Content",
    ),
    label: {
      control: "text",
      description: "The label content.",
      table: { category: "Content" },
    },
    placeholder: {
      control: "text",
      description: "The short hint displayed in the input before the user enters a value.",
      table: { category: "Content" },
    },
    helperText: {
      control: "text",
      description: "The helper text content.",
      table: { category: "Content" },
    },
    defaultValue: {
      control: "text",
      description: "The default value. Use when the component is not controlled.",
      table: { category: "Content" },
    },
    rows: {
      control: { type: "number", min: 1, max: 20 },
      description: "Number of rows to display when multiline option is set to true.",
      table: { category: "Layout" },
    },
  },
  args: {
    label: "Label",
  },
} satisfies Meta<typeof TextField>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    label: "Username",
    placeholder: "Enter your username",
    variant: "outlined",
    size: "medium",
    disabled: false,
    required: false,
    error: false,
    fullWidth: false,
    helperText: "This field is optional",
  },
};

export const Default: Story = {
  args: {
    label: "Label",
  },
};

export const Variants: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
      <TextField label="Outlined" variant="outlined" />
      <TextField label="Filled" variant="filled" />
      <TextField label="Standard" variant="standard" />
    </div>
  ),
};

export const Required: Story = {
  args: {
    label: "Required",
    required: true,
  },
};

export const Multiline: Story = {
  args: {
    label: "Multiline",
    multiline: true,
    rows: 4,
  },
};

export const HelperText: Story = {
  args: {
    label: "Label",
    helperText: "This is helper text",
  },
};

export const ErrorState: Story = {
  name: "Error",
  args: {
    label: "Error",
    error: true,
    helperText: "This is an error message",
  },
};

export const TypeInteraction: Story = {
  args: {
    label: "Username",
    placeholder: "Enter username",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const input = canvas.getByRole("textbox", { name: /username/i });

    await expect(input).toBeInTheDocument();
    await expect(input).toHaveValue("");

    await userEvent.type(input, "hello world");
    await expect(input).toHaveValue("hello world");

    await userEvent.clear(input);
    await expect(input).toHaveValue("");
  },
};

export const AccessibilityDemo: Story = {
  args: {
    label: "Email Address",
    type: "email",
    required: true,
    helperText: "We will never share your email",
  },
  parameters: {
    docs: {
      description: {
        story: "Demonstrates accessible form field with proper label, helper text, and required indicator.",
      },
    },
  },
};

export const AccessibleErrorState: Story = {
  args: {
    label: "Password",
    type: "password",
    error: true,
    helperText: "Password must be at least 8 characters",
  },
  parameters: {
    docs: {
      description: {
        story: "Shows accessible error state with clear error message.",
      },
    },
  },
};
