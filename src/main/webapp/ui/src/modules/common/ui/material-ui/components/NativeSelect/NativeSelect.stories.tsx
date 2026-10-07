/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/NativeSelect/NativeSelect.stories.tsx
 * Upstream project license: MIT
 */

import FilledInput from "@mui/material/FilledInput";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import InputLabel from "@mui/material/InputLabel";
import NativeSelect from "@mui/material/NativeSelect";
import OutlinedInput from "@mui/material/OutlinedInput";
import Stack from "@mui/material/Stack";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, userEvent, within } from "storybook/test";
import { muiDisabledArgType, muiErrorArgType, muiVariantArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/NativeSelect",
  component: NativeSelect,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: muiVariantArgType(["standard", "outlined", "filled"], "standard"),
    disabled: muiDisabledArgType,
    error: muiErrorArgType,
    IconComponent: { control: false },
    input: { control: false },
    inputProps: { control: false },
    // Children requires JSX (option elements)
    children: { control: false },
  },
} satisfies Meta<typeof NativeSelect>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    defaultValue: 30,
    inputProps: {
      name: "age",
      id: "uncontrolled-native",
    },
    children: (
      <>
        <option value={10}>Ten</option>
        <option value={20}>Twenty</option>
        <option value={30}>Thirty</option>
      </>
    ),
  },
  render: (args) => (
    <FormControl variant={args.variant ?? "standard"} disabled={args.disabled} error={args.error}>
      <InputLabel shrink htmlFor="uncontrolled-native">
        Age
      </InputLabel>
      <NativeSelect
        {...args}
        input={
          args.variant === "outlined" ? (
            <OutlinedInput label="Age" notched />
          ) : args.variant === "filled" ? (
            <FilledInput />
          ) : undefined
        }
      />
    </FormControl>
  ),
};

export const Variants: Story = {
  args: {},
  render: () => (
    <Stack spacing={3} sx={{ width: 200 }}>
      <FormControl variant="standard">
        <InputLabel htmlFor="standard-native">Standard</InputLabel>
        <NativeSelect
          inputProps={{
            name: "standard",
            id: "standard-native",
          }}
        >
          <option value={10}>Ten</option>
          <option value={20}>Twenty</option>
          <option value={30}>Thirty</option>
        </NativeSelect>
      </FormControl>
      <FormControl variant="outlined">
        <InputLabel shrink htmlFor="outlined-native">
          Outlined
        </InputLabel>
        <NativeSelect
          input={<OutlinedInput label="Outlined" notched />}
          inputProps={{
            name: "outlined",
            id: "outlined-native",
          }}
        >
          <option value={10}>Ten</option>
          <option value={20}>Twenty</option>
          <option value={30}>Thirty</option>
        </NativeSelect>
      </FormControl>
      <FormControl variant="filled">
        <InputLabel shrink htmlFor="filled-native">
          Filled
        </InputLabel>
        <NativeSelect
          input={<FilledInput />}
          inputProps={{
            name: "filled",
            id: "filled-native",
          }}
        >
          <option value={10}>Ten</option>
          <option value={20}>Twenty</option>
          <option value={30}>Thirty</option>
        </NativeSelect>
      </FormControl>
    </Stack>
  ),
};

export const WithPlaceholder: Story = {
  args: {},
  render: () => (
    <FormControl sx={{ width: 200 }}>
      <InputLabel htmlFor="placeholder-native">Country</InputLabel>
      <NativeSelect
        defaultValue=""
        inputProps={{
          name: "country",
          id: "placeholder-native",
        }}
      >
        <option value="" disabled>
          Select a country
        </option>
        <option value="us">United States</option>
        <option value="uk">United Kingdom</option>
        <option value="jp">Japan</option>
        <option value="de">Germany</option>
      </NativeSelect>
    </FormControl>
  ),
};

export const WithHelperText: Story = {
  args: {},
  render: () => (
    <FormControl sx={{ width: 200 }}>
      <InputLabel htmlFor="helper-native">Age</InputLabel>
      <NativeSelect
        inputProps={{
          name: "age",
          id: "helper-native",
          "aria-describedby": "helper-text",
        }}
      >
        <option value={10}>Ten</option>
        <option value={20}>Twenty</option>
        <option value={30}>Thirty</option>
      </NativeSelect>
      <FormHelperText id="helper-text">Select your age</FormHelperText>
    </FormControl>
  ),
};

export const ErrorState: Story = {
  name: "Error",
  args: {},
  render: () => (
    <FormControl error sx={{ width: 200 }}>
      <InputLabel htmlFor="error-native">Age</InputLabel>
      <NativeSelect
        inputProps={{
          name: "age",
          id: "error-native",
          "aria-describedby": "error-text",
        }}
      >
        <option value="">None</option>
        <option value={10}>Ten</option>
        <option value={20}>Twenty</option>
        <option value={30}>Thirty</option>
      </NativeSelect>
      <FormHelperText id="error-text">Please select an age</FormHelperText>
    </FormControl>
  ),
};

export const Disabled: Story = {
  args: {},
  render: () => (
    <FormControl disabled sx={{ width: 200 }}>
      <InputLabel htmlFor="disabled-native">Age</InputLabel>
      <NativeSelect
        defaultValue={20}
        inputProps={{
          name: "age",
          id: "disabled-native",
        }}
      >
        <option value={10}>Ten</option>
        <option value={20}>Twenty</option>
        <option value={30}>Thirty</option>
      </NativeSelect>
    </FormControl>
  ),
};

export const FullWidth: Story = {
  args: {},
  render: () => (
    <FormControl fullWidth>
      <InputLabel htmlFor="fullwidth-native">Category</InputLabel>
      <NativeSelect
        inputProps={{
          name: "category",
          id: "fullwidth-native",
        }}
      >
        <option value="electronics">Electronics</option>
        <option value="clothing">Clothing</option>
        <option value="books">Books</option>
        <option value="home">Home & Garden</option>
      </NativeSelect>
    </FormControl>
  ),
};

export const Controlled: Story = {
  args: {},
  render: function ControlledStory() {
    const [age, setAge] = React.useState("20");

    const handleChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
      setAge(event.target.value);
    };

    return (
      <FormControl sx={{ width: 200 }}>
        <InputLabel htmlFor="controlled-native">Age</InputLabel>
        <NativeSelect
          value={age}
          onChange={handleChange}
          inputProps={{
            name: "age",
            id: "controlled-native",
          }}
        >
          <option value={10}>Ten</option>
          <option value={20}>Twenty</option>
          <option value={30}>Thirty</option>
        </NativeSelect>
        <FormHelperText>Selected: {age}</FormHelperText>
      </FormControl>
    );
  },
};

export const InteractionTest: Story = {
  args: {} as never,
  render: function InteractionStory() {
    const [value, setValue] = React.useState("20");

    return (
      <FormControl sx={{ width: 200 }}>
        <InputLabel htmlFor="test-native">Age</InputLabel>
        <NativeSelect
          value={value}
          onChange={(e) => setValue(e.target.value)}
          inputProps={{
            name: "age",
            id: "test-native",
          }}
        >
          <option value={10}>Ten</option>
          <option value={20}>Twenty</option>
          <option value={30}>Thirty</option>
        </NativeSelect>
      </FormControl>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify initial value", async () => {
      const select = canvas.getByRole("combobox");
      await expect(select).toHaveValue("20");
    });

    await step("Change selection to Ten", async () => {
      const select = canvas.getByRole("combobox");
      await userEvent.selectOptions(select, "10");
      await expect(select).toHaveValue("10");
    });

    await step("Change selection to Thirty", async () => {
      const select = canvas.getByRole("combobox");
      await userEvent.selectOptions(select, "30");
      await expect(select).toHaveValue("30");
    });
  },
};
