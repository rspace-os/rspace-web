import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "@mui/material/Button";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Tooltip from "@mui/material/Tooltip";
import clsx from "clsx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()((theme) => ({
    button: {
        borderRadius: 4,
        marginBottom: 4,
        minWidth: "auto",
        fontSize: "0.8125rem",
        "& .MuiButton-startIcon": {
            "& svg": {
                fontSize: "1rem !important",
            },
        },
    },
    active: {
        backgroundColor: theme.palette.callToAction.main,
        borderColor: theme.palette.callToAction.main,
        color: theme.palette.callToAction.contrastText,
        "&:hover": {
            backgroundColor: theme.palette.callToAction.dark,
            borderColor: theme.palette.callToAction.dark,
        },
    },
    filled: {
        backgroundColor: theme.palette.callToAction.main,
        color: theme.palette.callToAction.contrastText,
        "&:hover": {
            backgroundColor: theme.palette.callToAction.dark,
        },
    },
}));

type ContextMenuButtonArgs = {
    icon?: React.ReactElement;
    loading?: boolean;
    variant?: "filled" | "default";
    disabledHelp?: string;
    active?: boolean;
    label?: string;
} & Omit<
    React.ComponentProps<typeof Button>,
    "icon" | "loading" | "variant" | "disabledHelp" | "color" | "active" | "label"
>;

function ContextMenuButton({
    icon,
    loading,
    variant = "default",
    disabledHelp = "",
    active = false,
    label,
    className,
    ...props
}: ContextMenuButtonArgs): React.ReactNode {
    const { classes } = useStyles();
    const [open, setOpen] = useState(false);

    const handleTooltipClose = () => {
        setOpen(false);
    };

    const inContrast = active || (variant === "filled" && disabledHelp === "");

    const buttonClassName = clsx(
        classes.button,
        {
            [classes.active]: active,
            [classes.filled]: variant === "filled" && disabledHelp === "",
        },
        className,
    );

    return (
        <ClickAwayListener onClickAway={handleTooltipClose}>
            <div onClick={() => disabledHelp !== "" && setOpen(true)}>
                <Tooltip
                    onClose={handleTooltipClose}
                    onOpen={() => disabledHelp !== "" && setOpen(true)}
                    open={open}
                    title={disabledHelp}
                >
                    <div>
                        <Button
                            className={buttonClassName}
                            size="medium"
                            variant={inContrast ? "contained" : "outlined"}
                            startIcon={loading ? <FontAwesomeIcon icon={faSpinner} spin size="sm" /> : icon}
                            disabled={disabledHelp !== ""}
                            aria-disabled={disabledHelp !== ""}
                            {...props}
                        >
                            {label}
                        </Button>
                    </div>
                </Tooltip>
            </div>
        </ClickAwayListener>
    );
}

export default observer(ContextMenuButton);
