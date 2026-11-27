import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type React from "react";
import BackToListButton from "./BackToListButton";

/**
 * A message filling the Requests detail pane when there is no request to show.
 */
export default function DetailPlaceholder({ message }: { message: string }): React.ReactNode {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", flexGrow: 1, width: "100%", height: "100%" }}>
      <BackToListButton sx={{ m: 1, alignSelf: "flex-start" }} />
      <Box sx={{ display: "flex", flexGrow: 1, alignItems: "center", justifyContent: "center", p: 2 }}>
        <Typography variant="body1" color="text.secondary" sx={{ textAlign: "center" }}>
          {message}
        </Typography>
      </Box>
    </Box>
  );
}
