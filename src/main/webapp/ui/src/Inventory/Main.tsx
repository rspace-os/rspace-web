import * as React from "react";
import Box from "@mui/material/Box";
import { type SxProps, type Theme } from "@mui/material/styles";
import { mergeSx } from "@/modules/common/utils/styles";

type MainArgs = {
  children: React.ReactNode;
  sx?: SxProps<Theme>;
} & React.HTMLAttributes<HTMLElement>;

const baseSx: SxProps<Theme> = {
  flexGrow: 1,
  minWidth: 0,
  height: "100%",
};

export default React.forwardRef<HTMLElement, MainArgs>(function Main(
  { children, sx, ...htmlAttributes },
  ref,
) {
  const composedSx: SxProps<Theme> = mergeSx(baseSx, sx);

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
