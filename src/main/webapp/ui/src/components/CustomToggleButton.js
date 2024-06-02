//@flow

/*
 * A slightly restyled Material UI toggle button, for use with the
 * CustomToggleButtonGroup, that together allows for toggling between a small
 * set of possible values.
 *
 * So as to be usable throughout the application this component MUST NOT have a
 * dependency on any global state.
 */
import React, { type ComponentType, type ElementProps } from "react";
import { withStyles } from "Styles";
import ToggleButton from "@mui/material/ToggleButton";
import Tooltip from "@mui/material/Tooltip";

const CustomToggleButton: ComponentType<{}> = withStyles<
  {| title: string, disabled: boolean, ...ElementProps<typeof ToggleButton> |},
  { root: string, selected: string }
>((theme) => ({
  root: {
    margin: `${theme.spacing(0.5)} !important`,
    border: `${theme.borders.section} !important`,
    flexGrow: 1,
    borderRadius: "4px !important",
    textTransform: "none",
  },
  selected: {
    backgroundColor: `${theme.palette.primary.main} !important`,
    color: `${theme.palette.primary.contrastText} !important`,
    "&:hover": {
      backgroundColor: `${theme.palette.primary.saturated} !important`,
      color: `${theme.palette.primary.contrastText} !important`,
    },
  },
}))(({ title = "", disabled = false, ...rest }) =>
  disabled ? (
    <ToggleButton disabled={true} {...rest} />
  ) : (
    <Tooltip title={title}>
      <ToggleButton disabled={false} {...rest} />
    </Tooltip>
  )
);

CustomToggleButton.displayName = "CustomToggleButton";
export default CustomToggleButton;
