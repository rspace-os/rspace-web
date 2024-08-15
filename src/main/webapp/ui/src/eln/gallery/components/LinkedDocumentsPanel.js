//@flow

import React, { type Node, type ComponentType } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

export const LinkedDocumentsPanel: ComponentType<{||}> = (): Node => {
  return (
    <Box component="section" sx={{ mt: 0.5 }}>
      <Typography variant="h6" component="h4">
        Linked Documents
      </Typography>
    </Box>
  );
};
