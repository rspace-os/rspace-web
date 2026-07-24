import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { Label } from "./label";
import { RadioGroup, RadioGroupItem } from "./radio-group";

const meta = {
  title: "DesignSystem/RadioGroup",
  component: RadioGroup,
  tags: ["autodocs"],
} satisfies Meta<typeof RadioGroup>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <RadioGroup defaultValue="comfortable">
      <div className="flex items-center gap-2">
        <RadioGroupItem value="default" id="r1" />
        <Label htmlFor="r1">Default</Label>
      </div>
      <div className="flex items-center gap-2">
        <RadioGroupItem value="comfortable" id="r2" />
        <Label htmlFor="r2">Comfortable</Label>
      </div>
      <div className="flex items-center gap-2">
        <RadioGroupItem value="compact" id="r3" />
        <Label htmlFor="r3">Compact</Label>
      </div>
    </RadioGroup>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const checked = canvas.getByRole("radio", { checked: true });
    expect(checked).toHaveAccessibleName(/comfortable/i);
  },
};

export const Disabled: Story = {
  render: () => (
    <RadioGroup defaultValue="default">
      <div className="flex items-center gap-2">
        <RadioGroupItem value="default" id="r1-disabled" />
        <Label htmlFor="r1-disabled">Enabled option</Label>
      </div>
      <div className="flex items-center gap-2">
        <RadioGroupItem value="disabled" id="r2-disabled" disabled />
        <Label htmlFor="r2-disabled">Disabled option</Label>
      </div>
    </RadioGroup>
  ),
};
