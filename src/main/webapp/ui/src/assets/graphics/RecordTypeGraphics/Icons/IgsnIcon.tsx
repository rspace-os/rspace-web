import React from "react";
import SvgIcon from "@mui/material/SvgIcon";
import { type SxProps, type Theme } from "@mui/material/styles";
import { mergeSx } from "../../../../modules/common/utils/styles";

function IgsnIcon({ sx }: { sx?: SxProps<Theme> }): React.ReactNode {
  return (
    <SvgIcon
      viewBox="0 0 16 16"
      sx={mergeSx(
        { fontSize: "inherit", verticalAlign: "-0.2em", width: 14, height: 14 },
        sx,
      )}
    >
      <path d="M 13 0 H 2 c -1.1 0 -2 0.9 -2 2 v 10 c 0 1.1 0.9 2 2 2 h 10 c 1.1 0 2 -0.9 2 -2 V 2 c 0 -1.1 -0.9 -2 -2 -2 z m -8.5 3 v 8 h -1.3 v -8 h 1.3 z m 1.5 0 h 0.7 a 1.28 1 180 0 1 0 8 h -0.7 z m 1.1 7 l 0 -6 a 1.15 1 0 0 1 0 6" />
    </SvgIcon>
  );
}

export default IgsnIcon;
