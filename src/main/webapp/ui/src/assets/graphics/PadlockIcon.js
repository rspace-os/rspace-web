//@flow
import React, { type Node } from "react";
import SvgIcon from "@mui/material/SvgIcon";

export default function PadlockIcon({
  color,
  className,
}: {
  color?: string,
  className?: string,
}): Node {
  return (
    <SvgIcon viewBox="0 0 238.1 317.5" className={className}>
      <g>
        <path
          fill={color}
          d="M119.1,40.1c-48.9,0-88.6,39.7-88.6,88.6v8.1v51v75.9c0,7.6,6.2,13.7,13.7,13.7h149.8c7.6,0,13.7-6.2,13.7-13.7v-75.9v-51
	v-8.1C207.7,79.8,168,40.1,119.1,40.1z M127.8,205.8v23.3c0,4.8-3.9,8.8-8.8,8.8l0,0c-4.8,0-8.8-3.9-8.8-8.8v-23.3
	C103,202.5,98,195.2,98,186.7c0-11.6,9.4-21,21-21c11.6,0,21,9.4,21,21C140.1,195.2,135.1,202.5,127.8,205.8z M60.3,123.1
	c2.9-29.9,28.1-53.4,58.8-53.4s55.9,23.5,58.8,53.4H60.3z"
          style={{ transform: "translate(0px, -3px)" }}
        />
      </g>
    </SvgIcon>
  );
}
