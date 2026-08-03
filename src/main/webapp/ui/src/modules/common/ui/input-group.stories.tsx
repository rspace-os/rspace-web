import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { SearchIcon, CreditCardIcon, InfoIcon } from "lucide-react";
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
  InputGroupText,
  InputGroupTextarea,
} from "./input-group";

const meta = {
  title: "DesignSystem/InputGroup",
  component: InputGroup,
  tags: ["autodocs"],
} satisfies Meta<typeof InputGroup>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <div style={{ width: "300px" }}>
      <InputGroup>
        <InputGroupAddon>
          <SearchIcon />
        </InputGroupAddon>
        <InputGroupInput placeholder="Search..." />
      </InputGroup>
    </div>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const input = canvas.getByPlaceholderText("Search...");
    expect(input).toBeInTheDocument();
  },
};

export const AddonAlignments: Story = {
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "16px", width: "300px" }}>
      <InputGroup>
        <InputGroupAddon align="inline-start">
          <CreditCardIcon />
        </InputGroupAddon>
        <InputGroupInput placeholder="Inline start icon" />
      </InputGroup>
      <InputGroup>
        <InputGroupInput placeholder="Inline end button" />
        <InputGroupAddon align="inline-end">
          <InputGroupButton>Send</InputGroupButton>
        </InputGroupAddon>
      </InputGroup>
      <InputGroup>
        <InputGroupAddon align="block-start">
          <InputGroupText>Card number</InputGroupText>
        </InputGroupAddon>
        <InputGroupInput placeholder="4242 4242 4242 4242" />
        <InputGroupAddon align="block-end">
          <InfoIcon />
          <InputGroupText>Visa, Mastercard, and Amex accepted.</InputGroupText>
        </InputGroupAddon>
      </InputGroup>
    </div>
  ),
};

export const WithTextarea: Story = {
  render: () => (
    <div style={{ width: "300px" }}>
      <InputGroup>
        <InputGroupTextarea placeholder="Write a message..." />
        <InputGroupAddon align="block-end">
          <InputGroupButton>Send</InputGroupButton>
        </InputGroupAddon>
      </InputGroup>
    </div>
  ),
};
