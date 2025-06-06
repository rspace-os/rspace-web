import React from "react";
import MobileStepper from "@mui/material/MobileStepper";
import Button from "@mui/material/Button";
import KeyboardArrowLeft from "@mui/icons-material/KeyboardArrowLeft";
import KeyboardArrowRight from "@mui/icons-material/KeyboardArrowRight";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";

type StepperArgs = {
  handleBack: () => void;
  handleNext: () => void;
  handleCancel: () => void;
  activeStep: number;
  onMove: () => void;
  stepsCount: number;
  disabled: boolean;
  loading: boolean;
};

export default function Stepper({
  handleBack,
  handleNext,
  handleCancel,
  activeStep,
  onMove,
  stepsCount,
  disabled,
  loading,
}: StepperArgs): React.ReactNode {
  const prevButton = () =>
    activeStep === 0 ? (
      <Button onClick={handleCancel}>Cancel</Button>
    ) : (
      <Button size="medium" onClick={handleBack} disabled={!activeStep}>
        <KeyboardArrowLeft /> Back
      </Button>
    );

  const nextButton = () =>
    activeStep === stepsCount - 1 ? (
      <SubmitSpinner
        onClick={onMove}
        disabled={disabled || loading}
        loading={loading}
        label="Move"
      />
    ) : (
      <Button size="medium" onClick={handleNext}>
        Next <KeyboardArrowRight />
      </Button>
    );

  return (
    <MobileStepper
      style={{ width: "100%" }}
      variant="dots"
      steps={stepsCount}
      position="static"
      activeStep={activeStep}
      nextButton={nextButton()}
      backButton={prevButton()}
    />
  );
}
