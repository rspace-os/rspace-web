import Box from "@mui/material/Box";
import type { SxProps, Theme } from "@mui/material/styles";
import * as React from "react";
import { mergeSx } from "@/modules/common/utils/styles";

type MainArgs = {
  children: React.ReactNode;
  sx?: SxProps<Theme>;
} & React.HTMLAttributes<HTMLElement>;

export default React.forwardRef<HTMLElement, MainArgs>(function Main({ children, sx, ...htmlAttributes }, ref) {
  return (
    <Box component="main" ref={ref} sx={mergeSx({ flexGrow: 1, minWidth: 0, height: "100%" }, sx)} {...htmlAttributes}>
      {children}
    </Box>
  );
});
