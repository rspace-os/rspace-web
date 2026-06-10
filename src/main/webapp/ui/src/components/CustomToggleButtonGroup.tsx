/*
 * A slightly restyled Material UI button group that allows for toggling
 * between a small set of possible values.
 *
 * So as to be usable throughout the application this component MUST NOT have a
 * dependency on any global state.
 */
import { useTheme } from "@mui/material/styles";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import React from "react";

function CustomToggleButtonGroup(
  props: React.ComponentProps<typeof ToggleButtonGroup>
): React.ReactNode {
  const theme = useTheme();
  return (
    <ToggleButtonGroup
      {...props}
      sx={{
        border: theme.borders.section,
        backgroundColor: theme.palette.background.default,
        display: "flex !important",
        ...((props.sx) ?? {}),
      }}
    />
  );
}

export default CustomToggleButtonGroup;
