// @flow

import { makeStyles } from "tss-react/mui";
import React, { type Node } from "react";
import clsx from "clsx";
import styled from "@mui/system/styled";

const useStyles = makeStyles()((theme) => ({
  main: {
    flexGrow: 1,
    minWidth: 0, // this is for the right hand panel 'title' ellipsis to work as expected
    height: "100%",
  },
  hiddenSidebar: {
    paddingTop: theme.spacing(7), // this for the header which includes the open sidebar button
  },
}));

type MainArgs = {|
  children: Node,
  sx?: { ... },
|};

const StyledMain = styled('main')``;

export default function Main({ children, sx }: MainArgs): Node {
  const { classes } = useStyles();

  return <StyledMain className={clsx(classes.main)} sx={sx}>{children}</StyledMain>;
}
