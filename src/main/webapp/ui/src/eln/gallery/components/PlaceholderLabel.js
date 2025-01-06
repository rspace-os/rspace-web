//@flow

import React, { type Node, type ComponentType } from "react";
import Grid from "@mui/material/Grid";
import { styled } from "@mui/material/styles";

export default (styled(({ children, className }) => (
  <Grid container className={className}>
    <Grid
      item
      sx={{
        p: 1,
        pt: 2,
        pr: 5,
      }}
      role="status"
    >
      {children}
    </Grid>
  </Grid>
))(() => ({
  justifyContent: "stretch",
  alignItems: "stretch",
  height: "100%",
  "& > *": {
    fontSize: "2rem",
    fontWeight: 700,
    color: window.matchMedia("(prefers-contrast: more)").matches
      ? "black"
      : "hsl(190deg, 20%, 29%, 37%)",
    flexGrow: 1,
    textAlign: "center",

    overflowWrap: "anywhere",
    overflow: "hidden",
  },
})): ComponentType<{| children: Node |}>);
