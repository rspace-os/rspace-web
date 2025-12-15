import { withStyles } from "Styles";
import Grid from "@mui/material/Grid";
import React from "react";

const GridWithPadding = withStyles<
  React.ComponentProps<typeof Grid>,
  { item: string }
>(() => ({
  item: {
    padding: "0px 2px 0px 2px",
  },
}))(Grid);

export default function GridItem(props: {
  className?: string,
  children: React.ReactNode,
}): React.ReactNode {
  return <GridWithPadding {...props} item />;
}
