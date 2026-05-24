import React from "react";
import { Observer } from "mobx-react-lite";
import Grid from "@mui/material/Grid";

type BatchGridContainerArgs = {
  children: Array<React.ReactNode>;
  style: object;
};

const BatchGridContainer = React.forwardRef<
  React.ElementRef<typeof Grid>,
  BatchGridContainerArgs
>(({ children, style }, ref) => {
  return (
    <Observer>
      {() => (
        <Grid
          container
          spacing={2}
          direction="row"
          sx={{ width: "100%", ml: -0.25, mb: 0 }}
          ref={ref}
          style={style}
        >
          {children}
        </Grid>
      )}
    </Observer>
  );
});

BatchGridContainer.displayName = "BatchGridContainer";
export default BatchGridContainer;
