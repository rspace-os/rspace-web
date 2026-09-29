import Step from "@mui/material/Step";
import StepButton from "@mui/material/StepButton";
import StepConnector from "@mui/material/StepConnector";
import StepContent from "@mui/material/StepContent";
import StepIcon from "@mui/material/StepIcon";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, userEvent, within } from "storybook/test";

const meta = {
  title: "Material UI/Navigation/Stepper/Composition",
  component: Stepper,
  parameters: { a11y: { test: "error" } },
  argTypes: { nonLinear: { control: "boolean" }, orientation: { control: false } },
} satisfies Meta<typeof Stepper>;
export default meta;
type Story = StoryObj<typeof meta>;

/** StepContent requires a vertical Stepper. StepButton changes the active step; StepLabel owns its icon. */
export const Composed: Story = {
  args: { nonLinear: true },
  render: function SampleSteps(args) {
    const [activeStep, setActiveStep] = useState(0);
    return (
      <Stepper
        aria-label="Sample preparation"
        {...args}
        orientation="vertical"
        activeStep={activeStep}
        connector={<StepConnector />}
      >
        {["Describe sample", "Review sample"].map((label, index) => (
          <Step key={label}>
            <StepButton onClick={() => setActiveStep(index)}>
              <StepLabel slots={{ stepIcon: StepIcon }}>{label}</StepLabel>
            </StepButton>
            <StepContent>{index === 0 ? "Enter the sample details." : "Check the sample before saving."}</StepContent>
          </Step>
        ))}
      </Stepper>
    );
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("tab", { name: /Review sample/ }));
    await expect(canvas.getByRole("tab", { name: /Review sample/ })).toHaveAttribute("aria-selected", "true");
    await expect(await canvas.findByText("Check the sample before saving.")).toBeVisible();
    await userEvent.click(canvas.getByRole("tab", { name: /Describe sample/ }));
    await expect(await canvas.findByText("Enter the sample details.")).toBeVisible();
  },
};
