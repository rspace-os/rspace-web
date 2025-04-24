import React from "react";
import SvgIcon from "@mui/material/SvgIcon";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()(() => ({
  root: {
    fontSize: "1.3rem",
    marginLeft: "-3px",
  },
  element: {
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 3,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeMiterlimit: 10,
  },
}));

/**
 * A simple chemical bond for use as a generic icon for chemistry files.
 */
export default function ChemistryIcon(): React.ReactNode {
  const { classes } = useStyles();
  return (
    <SvgIcon viewBox="0 0 40 40" className={classes.root}>
      <g>
        <polygon
          className={classes.element}
          points="28.2,5.7 11.8,5.7 3.5,20 11.8,34.3 28.2,34.3 36.5,20"
        />
        <g>
          <line
            className={classes.element}
            x1="25.2"
            y1="11.2"
            x2="30.3"
            y2="20.1"
          />
        </g>
      </g>
    </SvgIcon>
  );
}
