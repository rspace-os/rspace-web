import type { Meta, StoryObj } from "@storybook/react-vite";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "./collapsible";

const meta = {
  title: "DesignSystem/Collapsible",
  component: Collapsible,
  tags: ["autodocs"],
} satisfies Meta<typeof Collapsible>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <Collapsible defaultOpen style={{ width: "240px" }}>
      <CollapsibleTrigger>Administration</CollapsibleTrigger>
      <CollapsibleContent>
        <p style={{ padding: "8px 0" }}>Settings</p>
        <p style={{ padding: "8px 0" }}>Bookable Items</p>
      </CollapsibleContent>
    </Collapsible>
  ),
};
