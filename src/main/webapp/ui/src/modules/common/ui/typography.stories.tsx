import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { Blockquote, Heading, InlineCode, Link, List, P, Text } from "./typography";

const meta = {
  title: "DesignSystem/Typography",
  component: Heading,
  tags: ["autodocs"],
} satisfies Meta<typeof Heading>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Headings: Story = {
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
      <Heading level={1}>Heading Level 1</Heading>
      <Heading level={2}>Heading Level 2</Heading>
      <Heading level={3}>Heading Level 3</Heading>
      <Heading level={4}>Heading Level 4</Heading>
      <Heading level={5}>Heading Level 5</Heading>
      <Heading level={6}>Heading Level 6</Heading>
    </div>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const heading = canvas.getByRole("heading", { level: 1, name: "Heading Level 1" });
    expect(heading.tagName).toBe("H1");
  },
};

export const Paragraph: Story = {
  render: () => (
    <P>
      The quick brown fox jumps over the lazy dog. This paragraph demonstrates the
      default leading and spacing applied to body text.
    </P>
  ),
};

export const TextVariants: Story = {
  render: () => (
    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
      <Text variant="lead">Lead text</Text>
      <Text variant="large">Large text</Text>
      <Text variant="default">Default text</Text>
      <Text variant="small">Small text</Text>
      <Text variant="muted">Muted text</Text>
    </div>
  ),
};

export const BlockquoteExample: Story = {
  render: () => <Blockquote>&quot;After all,&quot; Bilbo said, &quot;why should not I keep it?&quot;</Blockquote>,
};

export const ListExample: Story = {
  render: () => (
    <List>
      <li>First item</li>
      <li>Second item</li>
      <li>Third item</li>
    </List>
  ),
};

export const InlineCodeExample: Story = {
  render: () => (
    <P>
      Use the <InlineCode>useState</InlineCode> hook to manage local component state.
    </P>
  ),
};

export const LinkExample: Story = {
  render: () => <Link href="#">This is a link</Link>,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const link = canvas.getByRole("link", { name: "This is a link" });
    expect(link).toHaveAttribute("href", "#");
  },
};
