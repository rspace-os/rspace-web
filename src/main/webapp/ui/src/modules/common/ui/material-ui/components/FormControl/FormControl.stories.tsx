/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/FormControl/FormControl.stories.tsx
 * Upstream project license: MIT
 */

import FilledInput from "@mui/material/FilledInput";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import FormLabel from "@mui/material/FormLabel";
import Input from "@mui/material/Input";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import OutlinedInput from "@mui/material/OutlinedInput";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import {
  createSelectArgType,
  muiDisabledArgType,
  muiErrorArgType,
  muiFullWidthArgType,
  muiRequiredArgType,
  muiSizeArgType,
  muiVariantArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/FormControl",
  component: FormControl,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: muiVariantArgType(["standard", "outlined", "filled"], "outlined"),
    size: muiSizeArgType,
    color: createSelectArgType(
      ["primary", "secondary", "success", "error", "info", "warning"] satisfies NonNullable<
        React.ComponentProps<typeof FormControl>["color"]
      >[],
      "primary",
      "The color of the component.",
      "Appearance",
    ),
    disabled: muiDisabledArgType,
    required: muiRequiredArgType,
    error: muiErrorArgType,
    fullWidth: muiFullWidthArgType,
    focused: {
      control: "boolean",
      description: "If true, the component is displayed in focused state.",
      table: {
        defaultValue: { summary: "false" },
        category: "State",
        type: { summary: "boolean" },
      },
    },
    hiddenLabel: {
      control: "boolean",
      description: "If true, the label is hidden.",
      table: {
        defaultValue: { summary: "false" },
        category: "Appearance",
        type: { summary: "boolean" },
      },
    },
    margin: {
      control: "select",
      options: ["none", "dense", "normal"],
      description: "If dense or normal, will adjust vertical spacing.",
      table: {
        defaultValue: { summary: "none" },
        category: "Layout",
        type: { summary: '"none" | "dense" | "normal"' },
      },
    },
    children: { control: false },
  },
} satisfies Meta<typeof FormControl>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: { variant: "outlined" },
  render: (args) => (
    <FormControl {...args}>
      <InputLabel htmlFor="my-input">Email address</InputLabel>
      {args.variant === "standard" ? (
        <Input id="my-input" aria-describedby="my-helper-text" />
      ) : args.variant === "filled" ? (
        <FilledInput id="my-input" aria-describedby="my-helper-text" />
      ) : (
        <OutlinedInput id="my-input" label="Email address" aria-describedby="my-helper-text" />
      )}
      <FormHelperText id="my-helper-text">We&apos;ll never share your email.</FormHelperText>
    </FormControl>
  ),
};

export const Variants: Story = {
  args: {},
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <FormControl variant="standard">
        <InputLabel htmlFor="standard-input">Standard</InputLabel>
        <Input id="standard-input" />
      </FormControl>
      <FormControl variant="outlined">
        <InputLabel htmlFor="outlined-input">Outlined</InputLabel>
        <OutlinedInput id="outlined-input" label="Outlined" />
      </FormControl>
      <FormControl variant="filled">
        <InputLabel htmlFor="filled-input">Filled</InputLabel>
        <FilledInput id="filled-input" />
      </FormControl>
    </Stack>
  ),
};

export const Sizes: Story = {
  args: {},
  render: () => (
    <Stack spacing={2} sx={{ width: 300 }}>
      <FormControl size="small">
        <InputLabel htmlFor="small-input">Small</InputLabel>
        <Input id="small-input" />
      </FormControl>
      <FormControl size="medium">
        <InputLabel htmlFor="medium-input">Medium</InputLabel>
        <Input id="medium-input" />
      </FormControl>
    </Stack>
  ),
};

export const Required: Story = {
  args: {
    required: true,
    children: (
      <>
        <InputLabel htmlFor="required-input">Required field</InputLabel>
        <Input id="required-input" />
        <FormHelperText>This field is required</FormHelperText>
      </>
    ),
  },
};

export const ErrorState: Story = {
  name: "Error",
  args: {
    error: true,
    children: (
      <>
        <InputLabel htmlFor="error-input">Error field</InputLabel>
        <Input id="error-input" aria-describedby="error-helper" />
        <FormHelperText id="error-helper">Error message</FormHelperText>
      </>
    ),
  },
};

export const Disabled: Story = {
  args: {
    disabled: true,
    children: (
      <>
        <InputLabel htmlFor="disabled-input">Disabled field</InputLabel>
        <Input id="disabled-input" />
        <FormHelperText>This field is disabled</FormHelperText>
      </>
    ),
  },
};

export const FullWidth: Story = {
  args: {
    fullWidth: true,
    children: (
      <>
        <InputLabel htmlFor="fullwidth-input">Full Width</InputLabel>
        <Input id="fullwidth-input" />
      </>
    ),
  },
};

export const WithSelect: Story = {
  args: {},
  render: () => (
    <FormControl sx={{ minWidth: 200 }}>
      <InputLabel id="select-label">Age</InputLabel>
      <Select labelId="select-label" id="select" label="Age" defaultValue="">
        <MenuItem value="">
          <em>None</em>
        </MenuItem>
        <MenuItem value={10}>Ten</MenuItem>
        <MenuItem value={20}>Twenty</MenuItem>
        <MenuItem value={30}>Thirty</MenuItem>
      </Select>
      <FormHelperText>Select your age</FormHelperText>
    </FormControl>
  ),
};

export const ComposedLayout: Story = {
  args: {},
  render: () => (
    <Stack spacing={2} sx={{ width: 400 }}>
      <FormControl component="fieldset">
        <FormLabel component="legend">Personal Information</FormLabel>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="First Name" size="small" />
          <TextField label="Last Name" size="small" />
          <TextField label="Email" type="email" size="small" />
        </Stack>
      </FormControl>
    </Stack>
  ),
};

export const InteractionTest: Story = {
  args: {
    children: (
      <>
        <InputLabel htmlFor="test-input">Test Input</InputLabel>
        <Input id="test-input" aria-describedby="test-helper" />
        <FormHelperText id="test-helper">Helper text</FormHelperText>
      </>
    ),
  },
  play: async ({ canvasElement }) => {
    const input = within(canvasElement).getByRole("textbox", { name: "Test Input" });
    await userEvent.click(input);
    await expect(input).toHaveFocus();
    await userEvent.type(input, "sample@example.org");
    await expect(input).toHaveValue("sample@example.org");
    await expect(input).toHaveAccessibleDescription("Helper text");
  },
};
