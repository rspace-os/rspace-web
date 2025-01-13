// @flow

import { makeStyles } from "tss-react/mui";
import React, { type Node } from "react";
import clsx from "clsx";

const useStyles = makeStyles()((theme) => ({
  main: {
    flexGrow: 1,
    minWidth: 0, // this is for the right hand panel 'title' ellipsis to work as expected
  },
  hiddenSidebar: {
    paddingTop: theme.spacing(7), // this for the header which includes the open sidebar button
  },
}));

type MainArgs = {|
  children: Node,
|};

export default function Main({ children }: MainArgs): Node {
  const { classes } = useStyles();

  return <main className={clsx(classes.main)}>{children}</main>;
}
