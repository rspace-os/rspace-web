//@flow

import React, { type Node } from "react";
import Box from "@mui/material/Box";
import { type Sx } from "../../util/styles";

type ScrollOptions =
  | "auto"
  | "clip"
  | "hidden"
  | "inherit"
  | "initial"
  | "overlay"
  | "revert"
  | "scroll"
  | "unset"
  | "visible";

type ScrollBoxArgs = {|
  children: Node,
  sx?: Sx,
  overflowY?: ScrollOptions,
  overflowX?: ScrollOptions,
|};

export default function ScrollBox({
  children,
  sx,
  overflowY,
  overflowX,
}: ScrollBoxArgs): Node {
  return (
    <Box
      sx={sx}
      style={{
        overflowY: overflowY ?? "auto",
        overflowX: overflowX ?? "initial",
      }}
    >
      {children}
    </Box>
  );
}
