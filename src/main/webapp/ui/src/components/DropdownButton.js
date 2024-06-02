//@flow strict

import React, { type ComponentType, type Node } from "react";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import { withStyles } from "Styles";
import CustomTooltip from "./CustomTooltip";

type DropdownButtonArgs = {|
  name: Node,
  children: Node,
  onClick: ({| target: HTMLElement, currentTarget: HTMLElement |}) => void,
  disabled?: boolean,
  title?: string,
|};

const DropdownButton: ComponentType<DropdownButtonArgs> = withStyles<
  DropdownButtonArgs,
  {| root: string, endIcon: string |}
>((theme) => ({
  root: {
    padding: theme.spacing(0, 0.75),
    minWidth: "unset",
    height: 32,
    textTransform: "none",
    letterSpacing: "0.04em",
  },
  endIcon: {
    marginLeft: 0.5,
  },
}))(({ name, classes, children, onClick, disabled, title }) => (
  <Grid item>
    <CustomTooltip title={title ?? ""} aria-label="">
      <Button
        className={classes.root}
        classes={classes}
        endIcon={<KeyboardArrowDownIcon />}
        size="small"
        onClick={onClick}
        disabled={disabled}
        aria-label={title}
        color="standardIcon"
      >
        {name}
      </Button>
    </CustomTooltip>
    {children}
  </Grid>
));

export default DropdownButton;
