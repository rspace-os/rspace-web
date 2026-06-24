import KeyboardArrowLeft from "@mui/icons-material/KeyboardArrowLeft";
import KeyboardArrowRight from "@mui/icons-material/KeyboardArrowRight";
import Button from "@mui/material/Button";
import MobileStepper from "@mui/material/MobileStepper";
import type React from "react";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation(["inventory", "common"]);
  const prevButton = () =>
    activeStep === 0 ? (
      <Button onClick={handleCancel}>{t("actions.cancel", { ns: "common" })}</Button>
    ) : (
      <Button size="medium" onClick={handleBack} disabled={!activeStep}>
        <KeyboardArrowLeft /> {t("actions.back", { ns: "common" })}
      </Button>
    );

  const nextButton = () =>
    activeStep === stepsCount - 1 ? (
      <SubmitSpinner
        onClick={onMove}
        disabled={disabled || loading}
        loading={loading}
        label={t("contextMenu.actions.move")}
      />
    ) : (
      <Button size="medium" onClick={handleNext}>
        {t("actions.next", { ns: "common" })} <KeyboardArrowRight />
      </Button>
    );

  return (
    <MobileStepper
      sx={{ width: "100%" }}
      variant="dots"
      steps={stepsCount}
      position="static"
      activeStep={activeStep}
      nextButton={nextButton()}
      backButton={prevButton()}
    />
  );
}
