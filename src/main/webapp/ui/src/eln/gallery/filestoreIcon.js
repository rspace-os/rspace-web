//@flow strict

import React, { type Node } from "react";
import SvgIcon from "@mui/material/SvgIcon";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()(() => ({
  root: {
    fontSize: "1.3rem",
    marginLeft: "-3px",
  },
  line: {
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 5,
    strokeLinecap: "round",
    strokeMiterlimit: 10,
  },
}));

/**
 * A cloud icon for use as a generic icon for filestore.
 */
export default function ChemistryIcon(): Node {
  const { classes } = useStyles();
  return (
    <SvgIcon viewBox="0 0 100 100" className={classes.root}>
      <g>
        <path d="M50,22.1c13.8,0,23.5,11.2,22.2,27.1,7-.2,17.7,3,17.7,14.9s-6.3,14-14,14H24c-7.7,0-14-6.3-14-14,0-11.2,9.9-15.3,17.7-14.9-.7-16.8,8.8-27.1,22.2-27.1ZM50,14.1c-16,0-29,12.5-29.9,28.3-10.3,1.8-18.1,10.8-18.1,21.6s9.8,22,22,22h51.9c12.1,0,22-9.8,22-22s-7.8-19.7-18.1-21.6c-.8-15.8-13.9-28.3-29.9-28.3Z" />
        <line
          className={classes.line}
          x1="61.2"
          y1="48.5"
          x2="38.8"
          y2="48.5"
        />
        <line className={classes.line} x1="61.2" y1="58" x2="38.8" y2="58" />
        <line
          className={classes.line}
          x1="61.2"
          y1="67.4"
          x2="38.8"
          y2="67.4"
        />
      </g>
    </SvgIcon>
  );
}
