/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/InputAdornment/InputAdornment.stories.tsx
 * Upstream project license: MIT
 */
import AccountCircle from "@mui/icons-material/AccountCircle";
import AttachMoneyIcon from "@mui/icons-material/AttachMoney";
import SearchIcon from "@mui/icons-material/Search";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import FormControl from "@mui/material/FormControl";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import InputLabel from "@mui/material/InputLabel";
import OutlinedInput from "@mui/material/OutlinedInput";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, userEvent, within } from "storybook/test";
import { createBooleanArgType, createSelectArgType, muiVariantArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/InputAdornment",
  component: InputAdornment,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    position: createSelectArgType(
      ["start", "end"],
      "start",
      "The position this adornment should appear relative to the Input.",
      "Layout",
    ),
    variant: muiVariantArgType(["standard", "outlined", "filled"], "standard"),
    disablePointerEvents: createBooleanArgType("If true, disable pointer events on the root.", false, "State"),
    disableTypography: createBooleanArgType(
      "If true, the adornment will not wrap in Typography component.",
      false,
      "Appearance",
    ),
    // Children can be text or JSX
    children: {
      control: "text",
      description: "The content of the component.",
      table: { category: "Content" },
    },
  },
} satisfies Meta<typeof InputAdornment>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    position: "start",
    children: "$",
  },
  decorators: [
    (Story, context) => (
      <TextField
        label="Amount"
        variant={context.args.variant}
        slotProps={{
          input: {
            [context.args.position === "end" ? "endAdornment" : "startAdornment"]: <Story />,
          },
        }}
      />
    ),
  ],
};

export const StartAdornment: Story = {
  args: {} as never,
  render: () => (
    <Stack spacing={2} sx={{ width: 300 }}>
      <TextField
        label="Amount"
        slotProps={{
          input: {
            startAdornment: <InputAdornment position="start">$</InputAdornment>,
          },
        }}
      />
      <TextField
        label="Weight"
        slotProps={{
          input: {
            startAdornment: <InputAdornment position="start">kg</InputAdornment>,
          },
        }}
      />
      <TextField
        label="Username"
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <AccountCircle />
              </InputAdornment>
            ),
          },
        }}
      />
    </Stack>
  ),
};

export const EndAdornment: Story = {
  args: {} as never,
  render: () => (
    <Stack spacing={2} sx={{ width: 300 }}>
      <TextField
        label="Price"
        slotProps={{
          input: {
            endAdornment: <InputAdornment position="end">.00</InputAdornment>,
          },
        }}
      />
      <TextField
        label="Weight"
        slotProps={{
          input: {
            endAdornment: <InputAdornment position="end">kg</InputAdornment>,
          },
        }}
      />
      <TextField
        label="Search"
        slotProps={{
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <SearchIcon />
              </InputAdornment>
            ),
          },
        }}
      />
    </Stack>
  ),
};

export const BothAdornments: Story = {
  args: {} as never,
  render: () => (
    <TextField
      label="Price"
      sx={{ width: 300 }}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <AttachMoneyIcon />
            </InputAdornment>
          ),
          endAdornment: <InputAdornment position="end">.00</InputAdornment>,
        },
      }}
    />
  ),
};

export const PasswordField: Story = {
  args: {} as never,
  render: function PasswordStory() {
    const [showPassword, setShowPassword] = React.useState(false);

    const handleClickShowPassword = () => setShowPassword((show) => !show);

    return (
      <FormControl sx={{ width: 300 }} variant="outlined">
        <InputLabel htmlFor="outlined-adornment-password">Password</InputLabel>
        <OutlinedInput
          id="outlined-adornment-password"
          type={showPassword ? "text" : "password"}
          endAdornment={
            <InputAdornment position="end">
              <IconButton
                aria-label={showPassword ? "hide password" : "show password"}
                onClick={handleClickShowPassword}
                edge="end"
              >
                {showPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          }
          label="Password"
        />
      </FormControl>
    );
  },
};

export const DisabledAdornment: Story = {
  args: {} as never,
  render: () => (
    <TextField
      label="Disabled"
      disabled
      sx={{ width: 300 }}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start" disablePointerEvents>
              $
            </InputAdornment>
          ),
        },
      }}
    />
  ),
};

export const InteractionTest: Story = {
  args: {} as never,
  render: function InteractionStory() {
    const [showPassword, setShowPassword] = React.useState(false);

    return (
      <FormControl sx={{ width: 300 }} variant="outlined">
        <InputLabel htmlFor="test-password">Password</InputLabel>
        <OutlinedInput
          id="test-password"
          type={showPassword ? "text" : "password"}
          defaultValue="secret123"
          endAdornment={
            <InputAdornment position="end">
              <IconButton
                aria-label={showPassword ? "hide password" : "show password"}
                onClick={() => setShowPassword((show) => !show)}
                edge="end"
              >
                {showPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          }
          label="Password"
        />
      </FormControl>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // Verify InputAdornment renders with password field using specific ID
    const input = canvas.getByLabelText("Password");
    await expect(input).toBeInTheDocument();
    await expect(input).toHaveAttribute("type", "password");

    // Verify toggle button exists
    const toggleButton = canvas.getByRole("button");
    await expect(toggleButton).toBeInTheDocument();
    await userEvent.click(toggleButton);
    await expect(input).toHaveAttribute("type", "text");
    await expect(toggleButton).toHaveAccessibleName("hide password");
    await userEvent.click(toggleButton);
    await expect(input).toHaveAttribute("type", "password");
  },
};
