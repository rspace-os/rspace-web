// @flow

import Chip from "@mui/material/Chip";
import React, {
  useState,
  type Node,
  type ComponentType,
  type ElementProps,
} from "react";
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
  filled: {
    backgroundColor: `${theme.palette.primary.saturated} !important`,
    color: theme.palette.primary.contrastText,
  },
}));

type ContextMenuButtonArgs = {|
  icon: Node,
  loading?: boolean,
  variant?: "filled" | "default",
  disabledHelp?: string,
  color?: string,
  active?: boolean,
  ...$Rest<
    ElementProps<typeof Chip>,
    {
      icon: mixed,
      loading: mixed,
      variant: mixed,
      disabledHelp: mixed,
      color: mixed,
      active: mixed,
    }
  >,
|};

function ContextMenuButton({
  icon,
  loading,
  variant = "default",
  disabledHelp = "",
  color = "default",
  active = false,
  ...props
}: ContextMenuButtonArgs): Node {
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
              className={clsx(classes.chip, inContrast && classes.filled)}
              size="medium"
              variant={"outlined"}
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
              color={inContrast ? "primary" : color}
              aria-disabled={disabledHelp !== ""}
              {...props}
            />
          </div>
        </Tooltip>
      </div>
    </ClickAwayListener>
  );
}

export default (observer(
  ContextMenuButton
): ComponentType<ContextMenuButtonArgs>);
