import React from "react";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import { useTheme, type SxProps, type Theme } from "@mui/material/styles";
import { asSxArray } from "@/modules/common/utils/styles";

export function StyledMenu({
  sx,
  slotProps,
  ...rest
}: React.ComponentProps<typeof Menu>): React.ReactNode {
  const theme = useTheme();
  const paperSlot =
    slotProps?.paper && typeof slotProps.paper !== "function"
      ? (slotProps.paper as { sx?: SxProps<Theme> })
      : undefined;
  return (
    <Menu
      elevation={0}
      anchorOrigin={{
        vertical: "bottom",
        horizontal: "center",
      }}
      transformOrigin={{
        vertical: "top",
        horizontal: "center",
      }}
      keepMounted={false}
      {...rest}
      slotProps={{
        ...slotProps,
        paper: {
          ...paperSlot,
          sx: [
            { border: theme.borders.menu },
            ...asSxArray(paperSlot?.sx),
            ...asSxArray(sx),
          ],
        },
      }}
    />
  );
}

export const StyledMenuItem = MenuItem;
