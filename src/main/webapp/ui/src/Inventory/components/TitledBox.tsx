import React from "react";
import { observer } from "mobx-react-lite";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import Divider from "@mui/material/Divider";
import clsx from "clsx";

const useStyles = makeStyles<{ allowOverflow: boolean }>()(
  (theme, { allowOverflow }) => ({
    title: {
      wordBreak: "break-word",
    },
    container: {
      flexWrap: "nowrap",
      maxHeight: "100%",
      backgroundColor: "white !important",
    },
    body: {
      padding: theme.spacing(2),
      overflow: "auto",
      overflowX: allowOverflow ? "auto" : "hidden",
    },
    titleContainer: {
      padding: theme.spacing(1, 2),
    },
    border: {
      border: theme.borders.section,
      margin: theme.spacing(1, 0),
      borderRadius: theme.spacing(0.5),
    },
  })
);

type TitledBoxArgs = {
  title?: React.ReactNode;
  children?: React.ReactNode;
  allowOverflow?: boolean;
  border?: boolean;
};

function TitledBox({
  title,
  children,
  allowOverflow = true,
  border = false,
}: TitledBoxArgs): React.ReactNode {
  const { classes } = useStyles({
    allowOverflow,
  });

  return (
    <Grid
      container
      direction="column"
      className={clsx(classes.container, border && classes.border)}
    >
      {title !== null && typeof title !== "undefined" && (
        <>
          <Grid item className={classes.titleContainer}>
            <Grid container direction="row" alignItems="center">
              <Grid item style={{ flexGrow: 1 }}>
                <Typography
                  variant="h5"
                  component="h5"
                  className={classes.title}
                >
                  {title}
                </Typography>
              </Grid>
            </Grid>
          </Grid>
          <Grid item>
            <Divider orientation="horizontal" />
          </Grid>
        </>
      )}
      <Grid item className={classes.body}>
        {children}
      </Grid>
    </Grid>
  );
}

/**
 * This component defines a box with an optional title and a body.  It is
 * useful in laying out pages that have multiple sections that the user should
 * browse sequentially.
 */
export default observer(TitledBox);
