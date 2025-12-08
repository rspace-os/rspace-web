import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "@mui/material/Button";
import clsx from "clsx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { makeStyles } from "tss-react/mui";
import { ariaValueMax, ariaValueMin, ariaValueNow, asPercentageString, type Progress } from "../util/progress";

const useStyles = makeStyles<{ progress: Progress }>()((_theme, { progress }) => ({
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
}));

type SubmitSpinnerButtonArgs = {
    onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
    disabled: boolean;
    loading: boolean;
    label?: React.ReactNode;
    progress?: Progress;
    fullWidth?: boolean;
    type?: "button" | "submit";
    size?: "small" | "medium" | "large";
    className?: string;
    color?: "primary" | "callToAction";
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
    className,
    color = "callToAction",
}: SubmitSpinnerButtonArgs): React.ReactNode {
    const { classes } = useStyles({ progress: progress ?? 0 });
    return (
        <Button
            color={color}
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
                <FontAwesomeIcon icon={faSpinner} spin size="lg" style={{ marginRight: "10px" }} />
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
            <div className={clsx(classes.label, loading && classes.hidden)}>{label}</div>
        </Button>
    );
}

/**
 * This is a submit button that shows a spinner when loading.
 */
export default observer(SubmitSpinnerButton);
