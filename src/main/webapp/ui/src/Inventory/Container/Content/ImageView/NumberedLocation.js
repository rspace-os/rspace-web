// @flow

import Badge from "@mui/material/Badge";
import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import SvgIcon from "@mui/material/SvgIcon";
import clsx from "clsx";

const useStyles = makeStyles()((theme, { selected, shadow }) => ({
  largeIcon: {
    fontSize: "3rem",
    filter: shadow ? "drop-shadow(3px 5px 1px rgba(0,0,0,0.2))" : "",
    zIndex: selected ? 3 : 1,
  },
  inlineIcon: {
    opacity: 0.0,
    width: "0.7em",
    height: "0.5em",
  },
  path: {
    stroke: selected
      ? theme.palette.primary.contrastText
      : theme.palette.primary.main,
    fill: selected
      ? theme.palette.primary.main
      : theme.palette.primary.contrastText,
  },
}));

const StyledBadge = withStyles<
  {|
    inline: boolean,
    selected: boolean,
    ...typeof Badge,
  |},
  { badge: string, root: string }
>((theme, { inline, selected }) => ({
  badge: {
    right: inline ? 17 : 24,
    top: inline ? 11 : 18,
    padding: "0 4px",
    height: inline ? 30 : 25,
    width: inline ? 30 : 25,
    fontSize: "1rem",
    borderRadius: "50%",
    zIndex: selected ? 4 : 2,
    border: inline ? `2px solid${theme.palette.primary.main}` : "none",
    backgroundColor: selected
      ? theme.palette.primary.main
      : theme.palette.primary.contrastText,
    color: selected
      ? theme.palette.primary.contrastText
      : theme.palette.primary.main,
  },
  root: {
    marginRight: inline ? 6 : 0,
    cursor: "inherit",
    userSelect: "none",
  },
}))(
  ({
    inline: _inline,
    selected: _selected,
    ...rest
  }: {
    inline: boolean,
    selected: boolean,
  }) => <Badge {...rest} />
);

type NumberedLocationArgs = {|
  number: number,
  inline?: boolean,
  shadow?: boolean,
  selected?: boolean,
|};

function NumberedLocation({
  number,
  inline = false,
  shadow = false,
  selected = false,
}: NumberedLocationArgs): Node {
  const { classes } = useStyles({ shadow, selected });

  return (
    <StyledBadge
      badgeContent={number}
      showZero
      className={inline ? classes.inlineBadge : ""}
      inline={inline}
      selected={selected}
    >
      <SvgIcon
        className={clsx(classes.largeIcon, inline && classes.inlineIcon)}
      >
        <path
          d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"
          className={classes.path}
        />
      </SvgIcon>
    </StyledBadge>
  );
}

export default (observer(
  NumberedLocation
): ComponentType<NumberedLocationArgs>);
