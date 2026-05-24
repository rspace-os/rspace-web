import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { useTheme } from "@mui/material/styles";
import React from "react";

function OverlayLoadingSpinner(): React.ReactNode {
  const theme = useTheme();
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        position: "absolute",
        height: "100%",
        top: 0,
        width: "100%",
        backgroundColor: "rgba(255,255,255,0.7)",
      }}
    >
      <div style={{ alignSelf: "center" }}>
        <FontAwesomeIcon
          icon={faSpinner}
          spin
          size="5x"
          style={{ marginRight: "10px", color: theme.palette.standardIcon.main }}
        />
      </div>
    </div>
  );
}

OverlayLoadingSpinner.displayName = "OverlayLoadingSpinner";
export default OverlayLoadingSpinner;
