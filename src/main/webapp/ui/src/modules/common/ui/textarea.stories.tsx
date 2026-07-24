import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { Textarea } from "./textarea";

const meta = {
  title: "DesignSystem/Textarea",
  component: Textarea,
  tags: ["autodocs"],
} satisfies Meta<typeof Textarea>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    placeholder: "Type your message here.",
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const textarea = canvas.getByPlaceholderText("Type your message here.");
    expect(textarea.tagName).toBe("TEXTAREA");
  },
};

export const Disabled: Story = {
  args: {
    placeholder: "Can't type here",
    disabled: true,
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const textarea = canvas.getByPlaceholderText("Can't type here");
    expect(textarea).toBeDisabled();
  },
};

export const WithValue: Story = {
  args: {
    defaultValue: "Some pre-filled content.",
    "aria-label": "Message",
  },
};
