// @flow

/*
 * A slightly restyled Material UI button group that allows for toggling
 * between a small set of possible values.
 *
 * So as to be usable throughout the application this component MUST NOT have a
 * dependency on any global state.
 */
import { withStyles } from "Styles";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import { type ComponentType, type ElementProps } from "react";

const CustomToggleButtonGroup: ComponentType<
  ElementProps<typeof ToggleButtonGroup>
> = withStyles<ElementProps<typeof ToggleButtonGroup>, { root: string }>(
  (theme) => ({
    root: {
      border: theme.borders.section,
      backgroundColor: theme.palette.background.main,
      display: "flex !important",
    },
  })
)(ToggleButtonGroup);

CustomToggleButtonGroup.displayName = "CustomToggleButtonGroup";
export default CustomToggleButtonGroup;
