//@flow strict

import React, { type ComponentType } from "react";
import SvgIcon from "@mui/material/SvgIcon";
import { withStyles } from "Styles";

type TemplateIconArgs = {|
  style?: { ... },
  color?: string,
|};

const TemplateIcon: ComponentType<TemplateIconArgs> = withStyles<
  TemplateIconArgs,
  {| root: string |}
>((theme, { color }) => ({
  root: {
    color,
    fontSize: "inherit",
    verticalAlign: "-0.2em",
    width: 14,
    height: 14,
  },
}))(({ classes, style }) => (
  <SvgIcon className={classes.root} viewBox="0 0 16 16" style={style}>
    <path d="M13 0H2c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V2c0-1.1-.9-2-2-2zm-1 2v2h-4v8h-2v-8h-4v-2h10z" />
  </SvgIcon>
));

TemplateIcon.displayName = "TemplateIcon";
export default TemplateIcon;
