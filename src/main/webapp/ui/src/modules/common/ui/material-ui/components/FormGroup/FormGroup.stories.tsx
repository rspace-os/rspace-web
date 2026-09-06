/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/FormGroup/FormGroup.stories.tsx
 * Upstream project license: MIT
 */
import Checkbox from "@mui/material/Checkbox";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import FormHelperText from "@mui/material/FormHelperText";
import FormLabel from "@mui/material/FormLabel";
import Switch from "@mui/material/Switch";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { expect, userEvent, within } from "storybook/test";
import { createBooleanArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Inputs/FormGroup",
  component: FormGroup,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    row: createBooleanArgType("If true, the form group will display its children in a row.", false, "Layout"),
    children: { control: false },
  },
} satisfies Meta<typeof FormGroup>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    children: (
      <>
        <FormControlLabel control={<Checkbox defaultChecked />} label="Option 1" />
        <FormControlLabel control={<Checkbox />} label="Option 2" />
        <FormControlLabel control={<Checkbox />} label="Option 3" />
      </>
    ),
  },
};

export const WithCheckboxes: Story = {
  args: {},
  render: () => (
    <FormControl component="fieldset">
      <FormLabel component="legend">Select your interests</FormLabel>
      <FormGroup>
        <FormControlLabel control={<Checkbox />} label="Technology" />
        <FormControlLabel control={<Checkbox />} label="Science" />
        <FormControlLabel control={<Checkbox />} label="Art" />
        <FormControlLabel control={<Checkbox />} label="Music" />
      </FormGroup>
      <FormHelperText>Select at least one option</FormHelperText>
    </FormControl>
  ),
};

export const WithSwitches: Story = {
  args: {},
  render: () => (
    <FormControl component="fieldset">
      <FormLabel component="legend">Settings</FormLabel>
      <FormGroup>
        <FormControlLabel control={<Switch />} label="Enable notifications" />
        <FormControlLabel control={<Switch defaultChecked />} label="Dark mode" />
        <FormControlLabel control={<Switch />} label="Auto-save" />
      </FormGroup>
    </FormControl>
  ),
};

export const Row: Story = {
  args: {
    row: true,
    children: (
      <>
        <FormControlLabel control={<Checkbox />} label="Small" />
        <FormControlLabel control={<Checkbox />} label="Medium" />
        <FormControlLabel control={<Checkbox />} label="Large" />
      </>
    ),
  },
};

export const WithError: Story = {
  args: {},
  render: function ErrorStory() {
    const [state, setState] = React.useState({
      gilad: true,
      jason: false,
      antoine: false,
    });

    const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
      setState({
        ...state,
        [event.target.name]: event.target.checked,
      });
    };

    const { gilad, jason, antoine } = state;
    const error = [gilad, jason, antoine].filter((v) => v).length !== 2;

    return (
      <FormControl error={error} component="fieldset" variant="standard">
        <FormLabel component="legend">Pick two</FormLabel>
        <FormGroup>
          <FormControlLabel
            control={<Checkbox checked={gilad} onChange={handleChange} name="gilad" />}
            label="Gilad Gray"
          />
          <FormControlLabel
            control={<Checkbox checked={jason} onChange={handleChange} name="jason" />}
            label="Jason Killian"
          />
          <FormControlLabel
            control={<Checkbox checked={antoine} onChange={handleChange} name="antoine" />}
            label="Antoine Llorca"
          />
        </FormGroup>
        <FormHelperText>You must pick exactly two</FormHelperText>
      </FormControl>
    );
  },
};

export const ControlledCheckboxes: Story = {
  args: {},
  render: function ControlledStory() {
    const [selected, setSelected] = React.useState<string[]>(["option1"]);

    const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = event.target.name;
      setSelected((prev) => (event.target.checked ? [...prev, value] : prev.filter((item) => item !== value)));
    };

    return (
      <FormControl component="fieldset">
        <FormLabel component="legend">Selected: {selected.join(", ") || "None"}</FormLabel>
        <FormGroup>
          <FormControlLabel
            control={<Checkbox checked={selected.includes("option1")} onChange={handleChange} name="option1" />}
            label="Option 1"
          />
          <FormControlLabel
            control={<Checkbox checked={selected.includes("option2")} onChange={handleChange} name="option2" />}
            label="Option 2"
          />
          <FormControlLabel
            control={<Checkbox checked={selected.includes("option3")} onChange={handleChange} name="option3" />}
            label="Option 3"
          />
        </FormGroup>
      </FormControl>
    );
  },
};

export const InteractionTest: Story = {
  args: {} as never,
  render: function InteractionStory() {
    const [checked, setChecked] = React.useState([false, false, false]);

    return (
      <FormGroup data-testid="form-group">
        <FormControlLabel
          control={
            <Checkbox checked={checked[0]} onChange={(e) => setChecked([e.target.checked, checked[1], checked[2]])} />
          }
          label="First"
        />
        <FormControlLabel
          control={
            <Checkbox checked={checked[1]} onChange={(e) => setChecked([checked[0], e.target.checked, checked[2]])} />
          }
          label="Second"
        />
        <FormControlLabel
          control={
            <Checkbox checked={checked[2]} onChange={(e) => setChecked([checked[0], checked[1], e.target.checked])} />
          }
          label="Third"
        />
      </FormGroup>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify all checkboxes are unchecked initially", async () => {
      const checkboxes = canvas.getAllByRole("checkbox");
      await expect(checkboxes).toHaveLength(3);
      for (const checkbox of checkboxes) {
        await expect(checkbox).not.toBeChecked();
      }
    });

    await step("Click first checkbox", async () => {
      const firstCheckbox = canvas.getAllByRole("checkbox")[0];
      await userEvent.click(firstCheckbox);
      await expect(firstCheckbox).toBeChecked();
    });

    await step("Click label to toggle second checkbox", async () => {
      const secondLabel = canvas.getByText("Second");
      await userEvent.click(secondLabel);

      const secondCheckbox = canvas.getAllByRole("checkbox")[1];
      await expect(secondCheckbox).toBeChecked();
    });
  },
};
