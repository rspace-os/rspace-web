//@flow

import React, { type Node } from "react";
import SvgIcon from "@mui/material/SvgIcon";

type CreateTemplateIconArgs = {|
  // MUI blackmagic: passed className is 'MuiChip-icon'
  className?: string,
|};

export default function CreateTemplateIcon({
  className,
}: CreateTemplateIconArgs): Node {
  return (
    <SvgIcon className={className}>
      <path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 3v2h-4v8h-2v-8h-4v-2h10z" />
    </SvgIcon>
  );
}
