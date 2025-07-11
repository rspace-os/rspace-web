import Chip from "@mui/material/Chip";
import React, { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
import { library } from "@fortawesome/fontawesome-svg-core";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
library.add(faSpinner);
import clsx from "clsx";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Tooltip from "@mui/material/Tooltip";

const useStyles = makeStyles()((theme) => ({
  chip: {
    borderRadius: 4,
    marginBottom: 4,
    cursor: "default",
  },
}));

type ContextMenuButtonArgs = {
  icon?: React.ReactElement;
  loading?: boolean;
  variant?: "filled" | "default";
  disabledHelp?: string;
  active?: boolean;
} & Omit<
  React.ComponentProps<typeof Chip>,
  "icon" | "loading" | "variant" | "disabledHelp" | "color" | "active"
>;

function ContextMenuButton({
  icon,
  loading,
  variant = "default",
  disabledHelp = "",
  active = false,
  ...props
}: ContextMenuButtonArgs): React.ReactNode {
  const { classes } = useStyles();
  const [open, setOpen] = useState(false);

  const handleTooltipClose = () => {
    setOpen(false);
  };

  const inContrast = active || (variant === "filled" && disabledHelp === "");
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
            <Chip
              className={classes.chip}
              size="medium"
              variant={inContrast ? "filled" : "outlined"}
              role="button"
              icon={
                loading ? (
                  <FontAwesomeIcon icon="spinner" spin size="sm" />
                ) : (
                  icon
                )
              }
              disabled={disabledHelp !== ""}
              clickable={disabledHelp === ""}
              color={inContrast ? "callToAction" : "default"}
              aria-disabled={disabledHelp !== ""}
              {...props}
            />
          </div>
        </Tooltip>
      </div>
    </ClickAwayListener>
  );
}

export default observer(ContextMenuButton);
