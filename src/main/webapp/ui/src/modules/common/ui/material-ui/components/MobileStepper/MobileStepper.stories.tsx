/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/MobileStepper/MobileStepper.stories.tsx
 * Upstream project license: MIT
 */
import KeyboardArrowLeft from "@mui/icons-material/KeyboardArrowLeft";
import KeyboardArrowRight from "@mui/icons-material/KeyboardArrowRight";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import MobileStepper from "@mui/material/MobileStepper";
import Paper from "@mui/material/Paper";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useArgs } from "storybook/preview-api";
import { expect, userEvent, within } from "storybook/test";
import { createNumberArgType, muiPositionArgType, muiVariantArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Navigation/MobileStepper",
  component: MobileStepper,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: muiVariantArgType(["dots", "progress", "text"], "dots"),
    position: muiPositionArgType(["bottom", "top", "static"], "bottom"),
    steps: createNumberArgType("Total number of steps.", 3, 1, 20),
    activeStep: createNumberArgType("Current active step (0-indexed).", 0, 0, 19),
    // Button props require JSX
    nextButton: { control: false },
    backButton: { control: false },
    slotProps: { control: false },
  },
} satisfies Meta<typeof MobileStepper>;

export default meta;
type Story = StoryObj<typeof meta>;

const steps = [
  {
    label: "Select campaign settings",
    description: `For each ad campaign that you create, you can control how much
              you're willing to spend on clicks and conversions, which networks
              and geographical locations you want your ads to show on, and more.`,
  },
  {
    label: "Create an ad group",
    description: "An ad group contains one or more ads which target a shared set of keywords.",
  },
  {
    label: "Create an ad",
    description: `Try out different ad text to see what brings in the most customers,
              and learn how to enhance your ads using features like ad extensions.
              If you run into any problems with your ads, find out how to tell if
              they're running and how to resolve approval issues.`,
  },
];

/** Change step, variant, and placement using Controls or the navigation buttons. */
export const Playground: Story = {
  args: { steps: 3, activeStep: 0, variant: "dots", position: "static", nextButton: null, backButton: null },
  render: function PlaygroundStepper(args) {
    const [, updateArgs] = useArgs();
    const activeStep = Math.min(args.activeStep ?? 0, args.steps - 1);
    return (
      <MobileStepper
        {...args}
        activeStep={activeStep}
        nextButton={
          <Button disabled={activeStep >= args.steps - 1} onClick={() => updateArgs({ activeStep: activeStep + 1 })}>
            Next
          </Button>
        }
        backButton={
          <Button disabled={activeStep === 0} onClick={() => updateArgs({ activeStep: activeStep - 1 })}>
            Back
          </Button>
        }
        slotProps={{ progress: { "aria-label": "Steps completed" } }}
      />
    );
  },
};

export const Dots: Story = {
  args: {} as never,
  render: function DotsStory() {
    const theme = useTheme();
    const [activeStep, setActiveStep] = React.useState(0);
    const maxSteps = steps.length;

    const handleNext = () => {
      setActiveStep((prevActiveStep) => prevActiveStep + 1);
    };

    const handleBack = () => {
      setActiveStep((prevActiveStep) => prevActiveStep - 1);
    };

    return (
      <Box sx={{ maxWidth: 400, flexGrow: 1 }}>
        <Paper
          square
          elevation={0}
          sx={{
            display: "flex",
            alignItems: "center",
            height: 50,
            pl: 2,
            bgcolor: "background.default",
          }}
        >
          <Typography>{steps[activeStep].label}</Typography>
        </Paper>
        <Box sx={{ height: 100, maxWidth: 400, width: "100%", p: 2 }}>{steps[activeStep].description}</Box>
        <MobileStepper
          variant="dots"
          steps={maxSteps}
          position="static"
          activeStep={activeStep}
          nextButton={
            <Button size="small" onClick={handleNext} disabled={activeStep === maxSteps - 1}>
              Next
              {theme.direction === "rtl" ? <KeyboardArrowLeft /> : <KeyboardArrowRight />}
            </Button>
          }
          backButton={
            <Button size="small" onClick={handleBack} disabled={activeStep === 0}>
              {theme.direction === "rtl" ? <KeyboardArrowRight /> : <KeyboardArrowLeft />}
              Back
            </Button>
          }
        />
      </Box>
    );
  },
};

export const Progress: Story = {
  args: {} as never,
  render: function ProgressStory() {
    const theme = useTheme();
    const [activeStep, setActiveStep] = React.useState(0);
    const maxSteps = steps.length;

    const handleNext = () => {
      setActiveStep((prevActiveStep) => prevActiveStep + 1);
    };

    const handleBack = () => {
      setActiveStep((prevActiveStep) => prevActiveStep - 1);
    };

    return (
      <Box sx={{ maxWidth: 400, flexGrow: 1 }}>
        <Paper
          square
          elevation={0}
          sx={{
            display: "flex",
            alignItems: "center",
            height: 50,
            pl: 2,
            bgcolor: "background.default",
          }}
        >
          <Typography>{steps[activeStep].label}</Typography>
        </Paper>
        <MobileStepper
          variant="progress"
          slotProps={{ progress: { "aria-label": "Steps completed" } }}
          steps={maxSteps}
          position="static"
          activeStep={activeStep}
          nextButton={
            <Button size="small" onClick={handleNext} disabled={activeStep === maxSteps - 1}>
              Next
              {theme.direction === "rtl" ? <KeyboardArrowLeft /> : <KeyboardArrowRight />}
            </Button>
          }
          backButton={
            <Button size="small" onClick={handleBack} disabled={activeStep === 0}>
              {theme.direction === "rtl" ? <KeyboardArrowRight /> : <KeyboardArrowLeft />}
              Back
            </Button>
          }
        />
      </Box>
    );
  },
};

export const Text: Story = {
  args: {} as never,
  render: function TextStory() {
    const theme = useTheme();
    const [activeStep, setActiveStep] = React.useState(0);
    const maxSteps = steps.length;

    const handleNext = () => {
      setActiveStep((prevActiveStep) => prevActiveStep + 1);
    };

    const handleBack = () => {
      setActiveStep((prevActiveStep) => prevActiveStep - 1);
    };

    return (
      <Box sx={{ maxWidth: 400, flexGrow: 1 }}>
        <Paper
          square
          elevation={0}
          sx={{
            display: "flex",
            alignItems: "center",
            height: 50,
            pl: 2,
            bgcolor: "background.default",
          }}
        >
          <Typography>{steps[activeStep].label}</Typography>
        </Paper>
        <MobileStepper
          variant="text"
          steps={maxSteps}
          position="static"
          activeStep={activeStep}
          nextButton={
            <Button size="small" onClick={handleNext} disabled={activeStep === maxSteps - 1}>
              Next
              {theme.direction === "rtl" ? <KeyboardArrowLeft /> : <KeyboardArrowRight />}
            </Button>
          }
          backButton={
            <Button size="small" onClick={handleBack} disabled={activeStep === 0}>
              {theme.direction === "rtl" ? <KeyboardArrowRight /> : <KeyboardArrowLeft />}
              Back
            </Button>
          }
        />
      </Box>
    );
  },
};

export const InteractionTest: Story = {
  args: {} as never,
  render: function InteractionStory() {
    const [activeStep, setActiveStep] = React.useState(0);

    return (
      <Box sx={{ maxWidth: 400, flexGrow: 1 }}>
        <MobileStepper
          variant="dots"
          steps={3}
          position="static"
          activeStep={activeStep}
          data-testid="mobile-stepper"
          nextButton={
            <Button size="small" onClick={() => setActiveStep((prev) => prev + 1)} disabled={activeStep === 2}>
              Next
              <KeyboardArrowRight />
            </Button>
          }
          backButton={
            <Button size="small" onClick={() => setActiveStep((prev) => prev - 1)} disabled={activeStep === 0}>
              <KeyboardArrowLeft />
              Back
            </Button>
          }
        />
      </Box>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify initial render", async () => {
      const backButton = canvas.getByRole("button", { name: /back/i });
      const nextButton = canvas.getByRole("button", { name: /next/i });

      await expect(backButton).toBeInTheDocument();
      await expect(nextButton).toBeInTheDocument();
      await expect(backButton).toBeDisabled();
    });

    await step("Click next button", async () => {
      const nextButton = canvas.getByRole("button", { name: /next/i });
      await userEvent.click(nextButton);

      const backButton = canvas.getByRole("button", { name: /back/i });
      await expect(backButton).not.toBeDisabled();
    });

    await step("Navigate to last step", async () => {
      const nextButton = canvas.getByRole("button", { name: /next/i });
      await userEvent.click(nextButton);

      await expect(nextButton).toBeDisabled();
    });
  },
};
