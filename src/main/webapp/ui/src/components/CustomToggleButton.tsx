/*
 * A slightly restyled Material UI toggle button, for use with the
 * CustomToggleButtonGroup, that together allows for toggling between a small
 * set of possible values.
 *
 * So as to be usable throughout the application this component MUST NOT have a
 * dependency on any global state.
 */
import React from "react";
import { useTheme } from "@mui/material/styles";
import ToggleButton from "@mui/material/ToggleButton";
import Tooltip from "@mui/material/Tooltip";

type CustomToggleButtonProps = { title?: string; disabled?: boolean } & Omit<
  React.ComponentProps<typeof ToggleButton>,
  "title" | "disabled"
>;

function CustomToggleButton({
  title = "",
  disabled = false,
  ...rest
}: CustomToggleButtonProps): React.ReactNode {
  const theme = useTheme();
  const button = (
    <ToggleButton
      disabled={disabled}
      sx={{
        margin: `${theme.spacing(0.5)} !important`,
        border: `${theme.borders.section} !important`,
        flexGrow: 1,
        borderRadius: "4px !important",
        textTransform: "none",
        "&.Mui-selected": {
          backgroundColor: `${theme.palette.primary.main} !important`,
          color: `${theme.palette.primary.contrastText} !important`,
          "&:hover": {
            backgroundColor: `${theme.palette.primary.saturated} !important`,
            color: `${theme.palette.primary.contrastText} !important`,
          },
        },
      }}
      {...rest}
    />
  );
  return disabled ? button : <Tooltip title={title}>{button}</Tooltip>;
}

CustomToggleButton.displayName = "CustomToggleButton";
export default CustomToggleButton;
