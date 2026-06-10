import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useTheme } from "@mui/material/styles";
import React from "react";

function ExpandCollapseIcon({ open }: { open: boolean }): React.ReactNode {
  const theme = useTheme();
  const transition = window.matchMedia("(prefers-reduced-motion: reduce)")
    .matches
    ? "initial"
    : theme.transitions.iconTransformations;
  return (
    <ExpandMoreIcon
      mode={open ? "collapse" : "expand"}
      sx={{
        transition: [transition],
        transform: `rotateZ(${open ? "180" : "0"}deg)`,
      }}
    />
  );
}

export default ExpandCollapseIcon;
