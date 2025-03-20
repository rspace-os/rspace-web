import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { withStyles } from "Styles";
import React from "react";

export default withStyles<{ open: boolean }, { root: string }>(
  (theme, { open }) => ({
    root: {
      transition: [
        window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "initial"
          : theme.transitions.iconTransformations,
      ],
      transform: `rotateZ(${open ? "180" : "0"}deg)`,
    },
  })
)(({ open, classes }) => (
  <ExpandMoreIcon mode={open ? "collapse" : "expand"} classes={classes} />
));
