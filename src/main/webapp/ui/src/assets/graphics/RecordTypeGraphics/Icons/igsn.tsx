import React from "react";
import SvgIcon from "@mui/material/SvgIcon";
import { withStyles } from "Styles";

type IgsnIconArgs = {
  style?: object;
  color?: string;
};

const IgsnIcon = withStyles<IgsnIconArgs, { root: string }>(
  (theme, { color }) => ({
    root: {
      color,
      fontSize: "inherit",
      verticalAlign: "-0.2em",
      width: 14,
      height: 14,
    },
  })
)(({ classes, style }) => (
  <SvgIcon className={classes.root} viewBox="0 0 16 16" style={style}>
    <path d="M 13 0 H 2 c -1.1 0 -2 0.9 -2 2 v 10 c 0 1.1 0.9 2 2 2 h 10 c 1.1 0 2 -0.9 2 -2 V 2 c 0 -1.1 -0.9 -2 -2 -2 z m -8.5 3 v 8 h -1.3 v -8 h 1.3 z m 1.5 0 h 0.7 a 1.28 1 180 0 1 0 8 h -0.7 z m 1.1 7 l 0 -6 a 1.15 1 0 0 1 0 6" />
  </SvgIcon>
));

IgsnIcon.displayName = "IgsnIcon";
export default IgsnIcon;
