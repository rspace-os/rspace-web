import React from "react";
import { Observer } from "mobx-react-lite";
import Grid from "@mui/material/Grid";
import { type SxProps, type Theme } from "@mui/material/styles";
import { mergeSx } from "@/modules/common/utils/styles";

const baseSx: SxProps<Theme> = { width: "100%", ml: -0.25, mb: 0 };

type BatchGridContainerArgs = {
  children: Array<React.ReactNode>;
  sx?: SxProps<Theme>;
};

const BatchGridContainer = React.forwardRef<
  React.ElementRef<typeof Grid>,
  BatchGridContainerArgs
>(({ children, sx }, ref) => {
  const composedSx: SxProps<Theme> = mergeSx(baseSx, sx);

  return (
    <Observer>
      {() => (
        <Grid
          container
          spacing={2}
          direction="row"
          sx={composedSx}
          ref={ref}
        >
          {children}
        </Grid>
      )}
    </Observer>
  );
});

BatchGridContainer.displayName = "BatchGridContainer";
export default BatchGridContainer;
