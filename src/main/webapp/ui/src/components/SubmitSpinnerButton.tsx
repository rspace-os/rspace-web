import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import type { SxProps, Theme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { mergeSx } from "@/modules/common/utils/styles";
import { ariaValueMax, ariaValueMin, ariaValueNow, asPercentageString, type Progress } from "@/util/progress";

type SubmitSpinnerButtonArgs = {
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  disabled: boolean;
  loading: boolean;
  label?: React.ReactNode;
  progress?: Progress;
  fullWidth?: boolean;
  type?: "button" | "submit";
  size?: "small" | "medium" | "large";
  color?: "primary" | "callToAction";
  sx?: SxProps<Theme>;
};

function SubmitSpinnerButton({
  onClick,
  disabled,
  loading,
  label = "Submit",
  progress,
  fullWidth = false,
  type = "button",
  size = "medium",
  color = "callToAction",
  sx,
}: SubmitSpinnerButtonArgs): React.ReactNode {
  return (
    <Button
      color={color}
      onClick={onClick}
      variant="contained"
      disabled={disabled}
      disableElevation
      fullWidth={fullWidth}
      data-test-id="SubmitButton"
      type={type}
      size={size}
      sx={mergeSx({ position: "relative", overflow: "hidden" }, sx)}
    >
      <Box
        sx={{
          position: "absolute",
          marginLeft: 10,
          opacity: loading ? 1 : 0,
        }}
      >
        <FontAwesomeIcon icon={faSpinner} spin size="lg" style={{ marginRight: "10px" }} />
      </Box>
      {progress !== null && typeof progress !== "undefined" ? (
        <Box
          sx={{
            height: 36,
            width: asPercentageString(progress),
            backgroundColor: "rgba(0,0,0,0.2)",
            position: "absolute",
            top: 0,
            left: 0,
            transition: "width 0.3s ease-in-out",
          }}
          role="progressbar"
          aria-valuenow={ariaValueNow(progress)}
          aria-valuemin={ariaValueMin()}
          aria-valuemax={ariaValueMax()}
        />
      ) : null}
      <Box sx={{ display: "flex", opacity: loading ? 0 : 1 }}>{label}</Box>
    </Button>
  );
}

/**
 * This is a submit button that shows a spinner when loading.
 */
export default observer(SubmitSpinnerButton);
