//@flow strict

import React, { type Node } from "react";
import SvgIcon from "@mui/material/SvgIcon";

/**
 * A magnifying glass icon with a '1' inside to indicate that the zoom level
 * will be reset to the initial zoom level when tapped.
 */
export default function ResetZoomIcon(): Node {
  return (
    <SvgIcon>
      <svg
        xmlns="http://www.w3.org/2000/svg"
        version="1.1"
        viewBox="0 0 100 100"
      >
        <g>
          <g id="Layer_2">
            <g>
              <circle
                style={{
                  fill: "none",
                  stroke: "currentColor",
                  strokeMiterlimit: "10",
                  strokeWidth: "7px",
                }}
                cx="39.5"
                cy="39.5"
                r="23"
              />
              <polygon
                style={{ fill: "currentColor" }}
                points="84.9 78.9 78.6 85.2 57.8 64.4 59 61 57 59 59 57 61.3 59.3 61 59 64.1 58.1 84.9 78.9"
              />
              <polyline
                style={{
                  fill: "none",
                  stroke: "currentColor",
                  strokeLinecap: "round",
                  strokeLinejoin: "round",
                  strokeWidth: "5px",
                }}
                points="33.5 35.3 41 30.1 41 48.3"
              />
            </g>
          </g>
        </g>
      </svg>
    </SvgIcon>
  );
}
