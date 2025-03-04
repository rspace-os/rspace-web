//@flow strict

import React, { type Node, type ComponentType } from "react";
import Button from "@mui/material/Button";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
library.add(faSpinner);
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";
import {
  type Progress,
  asPercentageString,
  ariaValueNow,
  ariaValueMin,
  ariaValueMax,
} from "../util/progress";

const useStyles = makeStyles()(
  (theme, { progress }: {| progress: Progress |}) => ({
    hidden: {
      opacity: 0,
    },
    spinner: {
      position: "absolute",
      marginLeft: 10,
    },
    label: {
      display: "flex",
    },
    progress: {
      height: 36, //height of buttons
      width: asPercentageString(progress),
      backgroundColor: "rgba(0,0,0,0.2)",
      position: "absolute",
      top: 0,
      left: 0,
      transition: "width 0.3s ease-in-out",
    },
    button: {
      position: "relative",
      overflow: "hidden",
    },
  })
);

type SubmitSpinnerButtonArgs = {|
  onClick?: (Event & { currentTarget: EventTarget, ... }) => void,
  disabled: boolean,
  loading: boolean,
  label?: Node,
  progress?: Progress,
  fullWidth?: boolean,
  type?: "button" | "submit",
  size?: "small" | "medium" | "large",
  className?: string,
|};

function SubmitSpinnerButton({
  onClick,
  disabled,
  loading,
  label = "Submit",
  progress,
  fullWidth = false,
  type = "button",
  size = "medium",
  className,
}: SubmitSpinnerButtonArgs): Node {
  const { classes } = useStyles({ progress });
  return (
    <Button
      color="callToAction"
      onClick={onClick}
      variant="contained"
      disabled={disabled}
      disableElevation
      className={classes.button}
      fullWidth={fullWidth}
      data-test-id="SubmitButton"
      type={type}
      size={size}
      classes={{ root: className }}
    >
      <div className={clsx(classes.spinner, !loading && classes.hidden)}>
        <FontAwesomeIcon
          icon="spinner"
          spin
          size="lg"
          style={{ marginRight: "10px" }}
        />
      </div>
      {progress !== null && typeof progress !== "undefined" ? (
        <div
          className={classes.progress}
          role="progressbar"
          aria-valuenow={ariaValueNow(progress)}
          aria-valuemin={ariaValueMin()}
          aria-valuemax={ariaValueMax()}
        />
      ) : null}
      <div className={clsx(classes.label, loading && classes.hidden)}>
        {label}
      </div>
    </Button>
  );
}

/**
 * This is a submit button that shows a spinner when loading.
 */
export default (observer(
  SubmitSpinnerButton
): ComponentType<SubmitSpinnerButtonArgs>);
