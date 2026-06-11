import React from "react";
import SvgIcon from "@mui/material/SvgIcon";
import { type SxProps, type Theme } from "@mui/material/styles";
import { mergeSx } from "../../../../modules/common/utils/styles";

function TemplateIcon({ sx }: { sx?: SxProps<Theme> }): React.ReactNode {
  return (
    <SvgIcon
      viewBox="0 0 16 16"
      sx={mergeSx(
        { fontSize: "inherit", verticalAlign: "-0.2em", width: 14, height: 14 },
        sx,
      )}
    >
      <path d="M13 0H2c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V2c0-1.1-.9-2-2-2zm-1 2v2h-4v8h-2v-8h-4v-2h10z" />
    </SvgIcon>
  );
}

export default TemplateIcon;
