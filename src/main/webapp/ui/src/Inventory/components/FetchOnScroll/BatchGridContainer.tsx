import Grid from "@mui/material/Grid";
import type { SxProps, Theme } from "@mui/material/styles";
import { Observer } from "mobx-react-lite";
import React from "react";
import { mergeSx } from "@/modules/common/utils/styles";

type BatchGridContainerArgs = {
  children: Array<React.ReactNode>;
  sx?: SxProps<Theme>;
};

const BatchGridContainer = React.forwardRef<React.ElementRef<typeof Grid>, BatchGridContainerArgs>(
  ({ children, sx }, ref) => {
    return (
      <Observer>
        {() => (
          <Grid container spacing={2} direction="row" sx={mergeSx({ width: "100%", ml: -0.25, mb: 0 }, sx)} ref={ref}>
            {children}
          </Grid>
        )}
      </Observer>
    );
  },
);

BatchGridContainer.displayName = "BatchGridContainer";
export default BatchGridContainer;
