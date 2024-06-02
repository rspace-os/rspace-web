//@flow

import React, { type Node } from "react";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";

export default function PageNotFoundScreen(): Node {
  return (
    <Grid container>
      <Grid item>
        <Box p={1}>
          <h1>Page not found.</h1>
          <p>
            The page was not found. You can try again, or use your ‘back’ button
            to return to your previous location and navigate from there, or use
            the sidebar to navigate to your samples and containers. If you can’t
            resolve the problem, please contact an administrator.
          </p>
        </Box>
      </Grid>
    </Grid>
  );
}
