/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/RadioButton/RadioButton.stories.tsx
 * Upstream project license: MIT
 */

import Button from "@mui/material/Button";
import { pink } from "@mui/material/colors";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormHelperText from "@mui/material/FormHelperText";
import FormLabel from "@mui/material/FormLabel";
import type { RadioProps } from "@mui/material/Radio";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { styled } from "@mui/material/styles";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, fn, userEvent, within } from "storybook/test";
import {
  createBooleanArgType,
  muiCheckedArgType,
  muiColorArgType,
  muiDisabledArgType,
  muiSizeArgType,
} from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/RadioButton",
  component: Radio,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    color: muiColorArgType,
    size: muiSizeArgType,
    disabled: muiDisabledArgType,
    checked: muiCheckedArgType,
    disableRipple: createBooleanArgType("If true, the ripple effect is disabled.", false, "Appearance"),
    required: createBooleanArgType("If true, the input element is required.", false, "State"),
  },
} satisfies Meta<typeof Radio>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    color: "primary",
    size: "medium",
    disabled: false,
    checked: false,
    slotProps: { input: { "aria-label": "Enable option" } },
  },
  render: function Playground(args) {
    const [, updateArgs] = useArgs();
    return <Radio {...args} onChange={(_, checked) => updateArgs({ checked })} />;
  },
};

export const Default: Story = {
  args: { slotProps: { input: { "aria-label": "Default option" } } },
};

export function RadioButtonsGroup() {
  return (
    <FormControl>
      <FormLabel id="demo-radio-buttons-group-label">Gender</FormLabel>
      <RadioGroup aria-labelledby="demo-radio-buttons-group-label" defaultValue="female" name="radio-buttons-group">
        <FormControlLabel value="female" control={<Radio />} label="Female" />
        <FormControlLabel value="male" control={<Radio />} label="Male" />
        <FormControlLabel value="other" control={<Radio />} label="Other" />
      </RadioGroup>
    </FormControl>
  );
}

export function RowRadioButtonsGroup() {
  return (
    <FormControl>
      <FormLabel id="demo-row-radio-buttons-group-label">Gender</FormLabel>
      <RadioGroup row aria-labelledby="demo-row-radio-buttons-group-label" name="row-radio-buttons-group">
        <FormControlLabel value="female" control={<Radio />} label="Female" />
        <FormControlLabel value="male" control={<Radio />} label="Male" />
        <FormControlLabel value="other" control={<Radio />} label="Other" />
        <FormControlLabel value="disabled" disabled control={<Radio />} label="other" />
      </RadioGroup>
    </FormControl>
  );
}

export function ControlledRadioButtonsGroup() {
  const [value, setValue] = React.useState("female");

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setValue((event.target as HTMLInputElement).value);
  };

  return (
    <FormControl>
      <FormLabel id="demo-controlled-radio-buttons-group">Gender</FormLabel>
      <RadioGroup
        aria-labelledby="demo-controlled-radio-buttons-group"
        name="controlled-radio-buttons-group"
        value={value}
        onChange={handleChange}
      >
        <FormControlLabel value="female" control={<Radio />} label="Female" />
        <FormControlLabel value="male" control={<Radio />} label="Male" />
      </RadioGroup>
    </FormControl>
  );
}

export function RadioButtons() {
  const [selectedValue, setSelectedValue] = React.useState("a");

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedValue(event.target.value);
  };

  return (
    <div>
      <Radio
        checked={selectedValue === "a"}
        onChange={handleChange}
        value="a"
        name="radio-buttons"
        slotProps={{ input: { "aria-label": "A" } }}
      />
      <Radio
        checked={selectedValue === "b"}
        onChange={handleChange}
        value="b"
        name="radio-buttons"
        slotProps={{ input: { "aria-label": "B" } }}
      />
    </div>
  );
}

export function SizeRadioButtons() {
  const [selectedValue, setSelectedValue] = React.useState("a");
  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedValue(event.target.value);
  };

  const controlProps = (item: string) => ({
    checked: selectedValue === item,
    onChange: handleChange,
    value: item,
    name: "size-radio-button-demo",
    slotProps: { input: { "aria-label": item } },
  });

  return (
    <div>
      <Radio {...controlProps("a")} size="small" />
      <Radio {...controlProps("b")} />
      <Radio
        {...controlProps("c")}
        sx={{
          "& .MuiSvgIcon-root": {
            fontSize: 28,
          },
        }}
      />
    </div>
  );
}

export function ColorRadioButtons() {
  const [selectedValue, setSelectedValue] = React.useState("a");

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedValue(event.target.value);
  };

  const controlProps = (item: string) => ({
    checked: selectedValue === item,
    onChange: handleChange,
    value: item,
    name: "color-radio-button-demo",
    slotProps: { input: { "aria-label": item } },
  });

  return (
    <div>
      <Radio {...controlProps("a")} />
      <Radio {...controlProps("b")} color="secondary" />
      <Radio {...controlProps("c")} color="success" />
      <Radio {...controlProps("d")} color="default" />
      <Radio
        {...controlProps("e")}
        sx={{
          color: pink[800],
          "&.Mui-checked": {
            color: pink[600],
          },
        }}
      />
    </div>
  );
}

export function FormControlLabelPlacement() {
  return (
    <FormControl>
      <FormLabel id="demo-form-control-label-placement">labelPlacement</FormLabel>
      <RadioGroup row aria-labelledby="demo-form-control-label-placement" name="position" defaultValue="top">
        <FormControlLabel value="top" control={<Radio />} label="Top" labelPlacement="top" />
        <FormControlLabel value="start" control={<Radio />} label="Start" labelPlacement="start" />
        <FormControlLabel value="bottom" control={<Radio />} label="Bottom" labelPlacement="bottom" />
        <FormControlLabel value="end" control={<Radio />} label="End" />
      </RadioGroup>
    </FormControl>
  );
}

export function LabelPlacement() {
  return (
    <FormControl>
      <FormLabel id="demo-form-control-label-placement">labelPlacement</FormLabel>
      <RadioGroup row aria-labelledby="demo-form-control-label-placement" name="position" defaultValue="top">
        <FormControlLabel value="top" control={<Radio />} label="Top" labelPlacement="top" />
        <FormControlLabel value="start" control={<Radio />} label="Start" labelPlacement="start" />
        <FormControlLabel value="bottom" control={<Radio />} label="Bottom" labelPlacement="bottom" />
        <FormControlLabel value="end" control={<Radio />} label="End" />
      </RadioGroup>
    </FormControl>
  );
}

export function ErrorRadios() {
  const [value, setValue] = React.useState("");
  const [error, setError] = React.useState(false);
  const [helperText, setHelperText] = React.useState("Choose wisely");

  const handleRadioChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setValue((event.target as HTMLInputElement).value);
    setHelperText(" ");
    setError(false);
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (value === "best") {
      setHelperText("You got it!");
      setError(false);
    } else if (value === "worst") {
      setHelperText("Sorry, wrong answer!");
      setError(true);
    } else {
      setHelperText("Please select an option.");
      setError(true);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <FormControl sx={{ m: 3 }} error={error} variant="standard">
        <FormLabel id="demo-error-radios">Pop quiz: MUI is...</FormLabel>
        <RadioGroup aria-labelledby="demo-error-radios" name="quiz" value={value} onChange={handleRadioChange}>
          <FormControlLabel value="best" control={<Radio />} label="The best!" />
          <FormControlLabel value="worst" control={<Radio />} label="The worst." />
        </RadioGroup>
        <FormHelperText>{helperText}</FormHelperText>
        <Button sx={{ mt: 1, mr: 1 }} type="submit" variant="outlined">
          Check Answer
        </Button>
      </FormControl>
    </form>
  );
}

const BpIcon = styled("span")(({ theme }) => ({
  borderRadius: "50%",
  width: 16,
  height: 16,
  boxShadow:
    theme.palette.mode === "dark"
      ? "0 0 0 1px rgb(16 22 26 / 40%)"
      : "inset 0 0 0 1px rgba(16,22,26,.2), inset 0 -1px 0 rgba(16,22,26,.1)",
  backgroundColor: theme.palette.mode === "dark" ? "#394b59" : "#f5f8fa",
  backgroundImage:
    theme.palette.mode === "dark"
      ? "linear-gradient(180deg,hsla(0,0%,100%,.05),hsla(0,0%,100%,0))"
      : "linear-gradient(180deg,hsla(0,0%,100%,.8),hsla(0,0%,100%,0))",
  ".Mui-focusVisible &": {
    outline: "2px auto rgba(19,124,189,.6)",
    outlineOffset: 2,
  },
  "input:hover ~ &": {
    backgroundColor: theme.palette.mode === "dark" ? "#30404d" : "#ebf1f5",
  },
  "input:disabled ~ &": {
    boxShadow: "none",
    background: theme.palette.mode === "dark" ? "rgba(57,75,89,.5)" : "rgba(206,217,224,.5)",
  },
}));

const BpCheckedIcon = styled(BpIcon)({
  backgroundColor: "#137cbd",
  backgroundImage: "linear-gradient(180deg,hsla(0,0%,100%,.1),hsla(0,0%,100%,0))",
  "&:before": {
    display: "block",
    width: 16,
    height: 16,
    backgroundImage: "radial-gradient(#fff,#fff 28%,transparent 32%)",
    content: '""',
  },
  "input:hover ~ &": {
    backgroundColor: "#106ba3",
  },
});

// Inspired by blueprintjs
function BpRadio(props: RadioProps) {
  return (
    <Radio
      sx={{
        "&:hover": {
          bgcolor: "transparent",
        },
      }}
      disableRipple
      color="default"
      checkedIcon={<BpCheckedIcon />}
      icon={<BpIcon />}
      {...props}
    />
  );
}

export function CustomizedRadios() {
  return (
    <FormControl>
      <FormLabel id="demo-customized-radios">Gender</FormLabel>
      <RadioGroup defaultValue="female" aria-labelledby="demo-customized-radios" name="customized-radios">
        <FormControlLabel value="female" control={<BpRadio />} label="Female" />
        <FormControlLabel value="male" control={<BpRadio />} label="Male" />
        <FormControlLabel value="other" control={<BpRadio />} label="Other" />
        <FormControlLabel value="disabled" disabled control={<BpRadio />} label="(Disabled option)" />
      </RadioGroup>
    </FormControl>
  );
}

export const InteractionTest: Story = {
  args: {} as never,
  render: () => {
    const [value, setValue] = React.useState("female");
    const handleChange = fn((event: React.ChangeEvent<HTMLInputElement>) => {
      setValue(event.target.value);
    });

    return (
      <FormControl data-testid="radio-form">
        <FormLabel id="test-radio-group-label">Gender</FormLabel>
        <RadioGroup
          aria-labelledby="test-radio-group-label"
          name="test-radio-group"
          value={value}
          onChange={handleChange}
        >
          <FormControlLabel value="female" control={<Radio />} label="Female" />
          <FormControlLabel value="male" control={<Radio />} label="Male" />
          <FormControlLabel value="other" control={<Radio />} label="Other" />
        </RadioGroup>
      </FormControl>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify initial render with female selected", async () => {
      // Use exact text to avoid /male/i matching both "Female" and "Male"
      const femaleRadio = canvas.getByRole("radio", { name: "Female" });
      const maleRadio = canvas.getByRole("radio", { name: "Male" });
      const otherRadio = canvas.getByRole("radio", { name: "Other" });

      await expect(femaleRadio).toBeInTheDocument();
      await expect(maleRadio).toBeInTheDocument();
      await expect(otherRadio).toBeInTheDocument();
      await expect(femaleRadio).toBeChecked();
      await expect(maleRadio).not.toBeChecked();
    });

    await step("Select male radio button", async () => {
      const maleRadio = canvas.getByRole("radio", { name: "Male" });
      await userEvent.click(maleRadio);

      await expect(maleRadio).toBeChecked();
      await expect(canvas.getByRole("radio", { name: "Female" })).not.toBeChecked();
    });

    await step("Select other radio button", async () => {
      const otherRadio = canvas.getByRole("radio", { name: "Other" });
      await userEvent.click(otherRadio);

      await expect(otherRadio).toBeChecked();
      await expect(canvas.getByRole("radio", { name: "Male" })).not.toBeChecked();
    });

    await step("Click on label to select female", async () => {
      const femaleLabel = canvas.getByText("Female");
      await userEvent.click(femaleLabel);

      await expect(canvas.getByRole("radio", { name: "Female" })).toBeChecked();
      await expect(canvas.getByRole("radio", { name: "Other" })).not.toBeChecked();
    });
  },
};
