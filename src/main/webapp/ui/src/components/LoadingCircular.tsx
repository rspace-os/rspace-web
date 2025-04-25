import React from "react";
import CircularProgress from "@mui/material/CircularProgress";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()(() => ({
  loaderWrapper: {
    width: "100%",
  },
  loader: {
    width: "100px",
    height: "100px",
    position: "absolute",
    top: "calc(50% - 50px)",
    left: "calc(50% - 20px)",
  },
  message: {
    width: "200px",
    position: "absolute",
    top: "calc(50% + 20px)",
    left: "calc(50% - 100px)",
    textAlign: "center",
    fontSize: "15px",
  },
}));

type LoaderCircularArgs = {
  message?: string;
};

export default function LoaderCircular(
  props: LoaderCircularArgs
): React.ReactNode {
  const { classes } = useStyles();

  return (
    <div className={classes.loaderWrapper}>
      <CircularProgress className={classes.loader} />
      <div className={classes.message}>{props.message}</div>
    </div>
  );
}
