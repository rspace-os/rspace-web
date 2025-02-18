/*
 * Applied all over the UI, this component allows us to add tooltips to all
 * sorts of buttons, widgets, and links.
 *
 * Due to its ubiquity throughout the application, its dependencies SHOULD be
 * kept to a minimum, and MUST NOT include any global state.
 */
import React from "react";
import Tooltip from "@mui/material/Tooltip";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";

const useStyles = makeStyles()((theme) => ({
  tooltip: {
    fontSize: theme.typography.pxToRem(12),
    zIndex: 3000,
  },
  block: {
    display: "block",
    height: 24,
  },
}));

type CustomTooltipArgs = {
  title: string,
  enterDelay?: number,
  children: React.ReactNode,
  block?: boolean,
  "aria-hidden"?: boolean,
  "aria-label"?: string,
};

export default function CustomTooltip({
  title,
  enterDelay = 200,
  children,
  block = false,
  ["aria-hidden"]: ariaHidden,
  ["aria-label"]: ariaLabel,
}: CustomTooltipArgs): React.ReactNode {
  const { classes } = useStyles();

  return (
    <Tooltip
      aria-hidden={ariaHidden}
      aria-label={ariaLabel ?? title}
      title={title}
      enterDelay={enterDelay}
      classes={{ tooltip: classes.tooltip }}
      className={clsx(block && classes.block)}
      role="tooltip"
    >
      <span>{children}</span>
    </Tooltip>
  );
}
