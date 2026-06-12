import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button, { buttonClasses } from "@mui/material/Button";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Tooltip from "@mui/material/Tooltip";
import { observer } from "mobx-react-lite";
// biome-ignore lint/style/useImportType: initial biome migration
import React, { useState } from "react";

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
  const [open, setOpen] = useState(false);

  const handleTooltipClose = () => {
    setOpen(false);
  };

  const inContrast = active || (variant === "filled" && disabledHelp === "");

  return (
    <ClickAwayListener onClickAway={handleTooltipClose}>
      {/** biome-ignore lint/a11y/noStaticElementInteractions: initial biome migration */}
      {/** biome-ignore lint/a11y/useKeyWithClickEvents: initial biome migration */}
      <div onClick={() => disabledHelp !== "" && setOpen(true)}>
        <Tooltip
          onClose={handleTooltipClose}
          onOpen={() => disabledHelp !== "" && setOpen(true)}
          open={open}
          title={disabledHelp}
        >
          <Button
            className={className}
            sx={(theme) => ({
              borderRadius: 4,
              minWidth: "auto",
              fontSize: "0.8125rem",
              [`& .${buttonClasses.startIcon} svg`]: {
                fontSize: "1rem !important",
              },
              ...(active
                ? {
                    backgroundColor: theme.palette.callToAction.main,
                    borderColor: theme.palette.callToAction.main,
                    color: theme.palette.callToAction.contrastText,
                    "&:hover": {
                      backgroundColor: theme.palette.callToAction.dark,
                      borderColor: theme.palette.callToAction.dark,
                    },
                  }
                : variant === "filled" && disabledHelp === ""
                  ? {
                      backgroundColor: theme.palette.callToAction.main,
                      color: theme.palette.callToAction.contrastText,
                      "&:hover": {
                        backgroundColor: theme.palette.callToAction.dark,
                      },
                    }
                  : {}),
            })}
            size="medium"
            variant={inContrast ? "contained" : "outlined"}
            startIcon={loading ? <FontAwesomeIcon icon={faSpinner} spin size="sm" /> : icon}
            disabled={disabledHelp !== ""}
            aria-disabled={disabledHelp !== ""}
            aria-label={label}
            {...props}
          >
            {label}
          </Button>
        </Tooltip>
      </div>
    </ClickAwayListener>
  );
}

export default observer(ContextMenuButton);
