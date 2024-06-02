//@flow
import React, { type Node, type ComponentType } from "react";
import { withStyles } from "Styles";

type Props = {| label: string |};

// classes are not included in Props type as they are created here by withStyles
function NoValue(props: { ...Props, classes: { root: string } }): Node {
  return <span className={props.classes.root}>{props.label}</span>;
}

export default (withStyles<Props, { root: string }>((theme) => ({
  root: {
    color: theme.palette.lightestGrey,
    fontStyle: "italic",
  },
}))(NoValue): ComponentType<Props>);
