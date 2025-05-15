/*
 * A slightly restyled Material UI button group that allows for toggling
 * between a small set of possible values.
 *
 * So as to be usable throughout the application this component MUST NOT have a
 * dependency on any global state.
 */
import { withStyles } from "Styles";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";

const CustomToggleButtonGroup = withStyles<
  Record<string, never>,
  { root: string }
>((theme) => ({
  root: {
    border: theme.borders.section,
    backgroundColor: theme.palette.background.default,
    display: "flex !important",
  },
}))(ToggleButtonGroup);

CustomToggleButtonGroup.displayName = "CustomToggleButtonGroup";
export default CustomToggleButtonGroup;
