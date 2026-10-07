/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Select/Select.stories.tsx
 * Upstream project license: MIT
 */
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import type { SelectProps } from "@mui/material/Select";
import Select from "@mui/material/Select";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  muiDisabledArgType,
  muiErrorArgType,
  muiRequiredArgType,
  muiVariantArgType,
} from "../../argTypeTemplates";

function Example({ id = "age-select", label = "Age", multiple = false, native = false, ...args }: SelectProps) {
  const [value, setValue] = useState<unknown>(multiple ? [10] : 10);
  return (
    <FormControl
      sx={{ minWidth: 160 }}
      variant={args.variant}
      size={args.size}
      error={args.error}
      disabled={args.disabled}
      required={args.required}
    >
      <InputLabel id={`${id}-label`} htmlFor={native ? id : undefined}>
        {label}
      </InputLabel>
      <Select
        {...args}
        id={id}
        labelId={`${id}-label`}
        label={label}
        multiple={multiple}
        native={native}
        value={value}
        onChange={(event) =>
          setValue(
            native && multiple && event.target instanceof HTMLSelectElement
              ? Array.from(event.target.selectedOptions, (option) => Number(option.value))
              : event.target.value,
          )
        }
      >
        {native
          ? [10, 20, 30].map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))
          : [10, 20, 30].map((value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ))}
      </Select>
    </FormControl>
  );
}

const meta = {
  title: "Material UI/Inputs/Select",
  component: Select,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: muiVariantArgType(["outlined", "filled", "standard"], "outlined"),
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
    multiple: createBooleanArgType(
      "If true, value must be an array and the menu will support multiple selections.",
      false,
      "State",
    ),
    native: createBooleanArgType("If true, the component uses a native select element.", false, "Appearance"),
    autoWidth: createBooleanArgType(
      "If true, the width of the popover will automatically be set according to the items inside the menu.",
      false,
      "Layout",
    ),
    displayEmpty: createBooleanArgType(
      "If true, a value is displayed even if no items are selected.",
      false,
      "Content",
    ),
    label: {
      control: "text",
      description: "The label content.",
      table: { category: "Content" },
    },
    children: { control: false },
  },
} satisfies Meta<typeof Select>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    label: "Age",
    variant: "outlined",
    size: "medium",
    disabled: false,
    required: false,
    error: false,
    multiple: false,
  },
  render: (args) => <Example key={`${args.multiple}-${args.native}`} {...args} />,
};

export const Default: Story = { render: (args) => <Example key={`${args.multiple}-${args.native}`} {...args} /> };
export const Multiple: Story = {
  args: { multiple: true },
  play: async ({ canvasElement }) => {
    const select = within(canvasElement).getByRole("combobox", { name: "Age" });
    await userEvent.click(select);
    const page = within(canvasElement.ownerDocument.body);
    await userEvent.click(await page.findByRole("option", { name: "20" }));
    await userEvent.keyboard("{Escape}");
    await expect(select).toHaveTextContent("10, 20");
  },
  render: (args) => <Example key={`${args.multiple}-${args.native}`} {...args} />,
};
export const Native: Story = {
  args: { native: true },
  play: async ({ canvasElement }) => {
    const select = within(canvasElement).getByRole("combobox", { name: "Age" });
    await userEvent.selectOptions(select, "20");
    await expect(select).toHaveValue("20");
  },
  render: (args) => <Example key={`${args.multiple}-${args.native}`} {...args} />,
};

export const Variants: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "16px" }}>
      <Example id="outlined" label="Outlined" variant="outlined" />
      <Example id="filled" label="Filled" variant="filled" />
      <Example id="standard" label="Standard" variant="standard" />
    </div>
  ),
};

export const Sizes: Story = {
  args: {} as never,
  render: () => (
    <div style={{ display: "flex", gap: "16px", alignItems: "center" }}>
      <Example id="small" label="Small" size="small" />
      <Example id="medium" label="Medium" size="medium" />
    </div>
  ),
};

export const InteractionTest: Story = {
  args: {} as never,
  render: () => <Example id="select-test" label="Age" />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const select = canvas.getByRole("combobox");

    await expect(select).toBeInTheDocument();
    await userEvent.click(select);

    const listbox = await within(document.body).findByRole("listbox");
    await expect(listbox).toBeInTheDocument();

    const option = within(listbox).getByRole("option", { name: "20" });
    await userEvent.click(option);
    await expect(select).toHaveTextContent("20");
  },
};
