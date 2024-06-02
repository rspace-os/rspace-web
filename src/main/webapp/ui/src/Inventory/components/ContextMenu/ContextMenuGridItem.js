// @flow

import { withStyles } from "Styles";
import Grid from "@mui/material/Grid";
import React, { type Node, type ElementProps } from "react";

const GridWithPadding = withStyles<ElementProps<typeof Grid>, { item: string }>(
  () => ({
    item: {
      padding: "0px 2px 0px 2px",
    },
  })
)(Grid);

export default function GridItem(props: {}): Node {
  return <GridWithPadding {...props} item />;
}
