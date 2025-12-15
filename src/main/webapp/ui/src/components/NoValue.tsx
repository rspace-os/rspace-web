import React from "react";
import { withStyles } from "Styles";

type Props = { label: string };

// classes are not included in Props type as they are created here by withStyles
function NoValue(
  props: Props & { classes: { root: string } }
): React.ReactNode {
  return <span className={props.classes.root}>{props.label}</span>;
}

/**
 * Label that is shown when a field is both disabled and empty. Generally
 * avoid using, instead preferring to show the empty field in a disabled state.
 */
export default withStyles<Props, { root: string }>((theme) => ({
  root: {
    color: theme.palette.lightestGrey,
    fontStyle: "italic",
  },
}))(NoValue);
