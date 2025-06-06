import React from "react";
import { Observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";

const useStyles = makeStyles()((theme) => ({
  container: {
    width: "100%",
    marginLeft: theme.spacing(-0.25),
    marginBottom: theme.spacing(0),
  },
}));

type BatchGridContainerArgs = {
  children: Array<React.ReactNode>;
  style: object;
};

const BatchGridContainer = React.forwardRef<
  React.ElementRef<typeof Grid>,
  BatchGridContainerArgs
>(({ children, style }, ref) => {
  const { classes } = useStyles();
  return (
    <Observer>
      {() => (
        <Grid
          container
          spacing={2}
          direction="row"
          className={classes.container}
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
