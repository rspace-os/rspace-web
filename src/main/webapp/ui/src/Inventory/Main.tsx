import * as React from "react";
import Box from "@mui/material/Box";
import { type SxProps, type Theme } from "@mui/material/styles";

type MainArgs = {
  children: React.ReactNode;
  sx?: SxProps<Theme>;
} & React.HTMLAttributes<HTMLElement>;

const baseSx: SxProps<Theme> = {
  flexGrow: 1,
  minWidth: 0,
  height: "100%",
};

const isSxArray = (value: SxProps<Theme>): value is Extract<SxProps<Theme>, readonly unknown[]> =>
  Array.isArray(value);

export default React.forwardRef<HTMLElement, MainArgs>(function Main(
  { children, sx, ...htmlAttributes },
  ref,
) {
  const composedSx: SxProps<Theme> = sx
    ? isSxArray(sx)
      ? [baseSx, ...sx]
      : [baseSx, sx]
    : baseSx;

  return (
    <Box
      component="main"
      ref={ref}
      sx={composedSx}
      {...htmlAttributes}
    >
      {children}
    </Box>
  );
});
