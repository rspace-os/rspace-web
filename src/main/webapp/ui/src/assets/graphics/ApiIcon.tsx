import React from "react";
import SvgIcon, { type SvgIconProps } from "@mui/material/SvgIcon";

export default function ApiIcon(props: SvgIconProps): React.ReactNode {
  return (
    <SvgIcon {...props} viewBox="0 0 24 24">
      <path d="M22 9V7h-2V5a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-2h2v-2h-2v-2h2v-2h-2V9h2zm-4 10H4V5h14v14zM6 13h5v4H6v-4zm6-6h4v3h-4V7zM6 7h5v5H6V7zm6 4h4v6h-4v-6z" />
    </SvgIcon>
  );
}
