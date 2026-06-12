import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";

function OverlayLoadingSpinner(): React.ReactNode {
  const theme = useTheme();
  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "center",
        position: "absolute",
        height: "100%",
        top: 0,
        width: "100%",
        backgroundColor: "rgba(255,255,255,0.7)",
      }}
    >
      <Box sx={{ alignSelf: "center" }}>
        <FontAwesomeIcon
          icon={faSpinner}
          spin
          size="5x"
          style={{ marginRight: "10px", color: theme.palette.standardIcon.main }}
        />
      </Box>
    </Box>
  );
}

export default OverlayLoadingSpinner;
