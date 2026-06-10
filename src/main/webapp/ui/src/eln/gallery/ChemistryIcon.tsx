import React from "react";
import SvgIcon from "@mui/material/SvgIcon";

/**
 * A simple chemical bond for use as a generic icon for chemistry files.
 */
export default function ChemistryIcon(): React.ReactNode {
  return (
    <SvgIcon
      viewBox="0 0 40 40"
      sx={{ fontSize: "--var(--fa-width, 1.25em)", ml: "2px" }}
    >
      <g>
        <polygon
          fill="none"
          stroke="currentColor"
          strokeWidth={3}
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeMiterlimit={10}
          points="28.2,5.7 11.8,5.7 3.5,20 11.8,34.3 28.2,34.3 36.5,20"
        />
        <g>
          <line
            fill="none"
            stroke="currentColor"
            strokeWidth={3}
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeMiterlimit={10}
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
