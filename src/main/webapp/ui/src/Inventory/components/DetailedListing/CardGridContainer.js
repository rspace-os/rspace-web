// @flow

import React, { type Node, type ComponentType } from "react";
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

type CardGridContainerArgs = {|
  children: Array<Node>,
  style: { ... },
|};

const CardGridContainer: ComponentType<CardGridContainerArgs> =
  React.forwardRef(({ children, style }: CardGridContainerArgs, ref) => {
    const { classes } = useStyles();
    return (
      <Observer>
        {() => (
          <Grid
            container
            spacing={2}
            direction="column"
            wrap="nowrap"
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

CardGridContainer.displayName = "CardGridContainer";
export default CardGridContainer;
