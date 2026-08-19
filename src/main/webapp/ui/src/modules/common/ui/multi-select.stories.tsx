import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { MultiSelect } from "./multi-select";

const meta = {
  title: "DesignSystem/MultiSelect",
  component: MultiSelect,
  tags: ["autodocs"],
  args: {
    options: [],
    value: [],
    onValueChange: () => undefined,
    ariaLabel: "Values",
    placeholder: "Select values",
    emptyMessage: "No matching values found",
    removeLabel: (label) => `Remove ${label}`,
  },
} satisfies Meta<typeof MultiSelect>;

export default meta;

type Story = StoryObj<typeof meta>;

const timezones = ["America/New_York", "Asia/Tokyo", "Europe/Berlin", "Europe/London"];

function Example({ custom }: { custom: boolean }) {
  const [value, setValue] = useState<readonly string[]>([]);
  return (
    <MultiSelect
      options={custom ? [] : timezones}
      value={value}
      onValueChange={setValue}
      allowCustomValues={custom}
      ariaLabel={custom ? "Values" : "Time zones"}
      placeholder={custom ? "Type a value and press Enter" : "Select values"}
      emptyMessage={custom ? "Enter a value" : "No matching values found"}
      removeLabel={(label) => `Remove ${label}`}
      className="max-w-md rounded-sm"
    />
  );
}

export const ConstantOptions: Story = {
  render: () => <Example custom={false} />,
};

export const ArbitraryText: Story = {
  render: () => <Example custom />,
};
