import React from "react";
import SvgIcon from "@mui/material/SvgIcon";

type TemplateIconArgs = {
  style?: object;
  color?: string;
};

function TemplateIcon({ color, style }: TemplateIconArgs): React.ReactNode {
  return (
    <SvgIcon
      viewBox="0 0 16 16"
      style={style}
      sx={{ color, fontSize: "inherit", verticalAlign: "-0.2em", width: 14, height: 14 }}
    >
      <path d="M13 0H2c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V2c0-1.1-.9-2-2-2zm-1 2v2h-4v8h-2v-8h-4v-2h10z" />
    </SvgIcon>
  );
}

TemplateIcon.displayName = "TemplateIcon";
export default TemplateIcon;
