//@flow

import React, { type Node } from "react";
import { makeStyles } from "tss-react/mui";
import Fade from "@mui/material/Fade";
import { useScrollPosition } from "../../../util/useScrollPosition";

const useStyles = makeStyles()((theme, { top }) => ({
  visible: {
    backgroundColor: "white",
    position: "sticky",
    top: top,
    marginTop: "-5px",
    zIndex: 100,
    border: "1px solid rgb(235,235,235)",
    borderTop: "none",
    borderBottomLeftRadius: "5px",
    borderBottomRightRadius: "5px",
  },
  invisible: {
    position: "sticky",
    top: "0px",
    border: "1px solid rgb(235,235,235)",
    backgroundColor: "white",
  },
}));

type HidableMenuArgs = {|
  children: Node,
  top: number,
  visible: boolean,
|};

export default function HidableMenu({
  top,
  children,
  visible = false,
}: HidableMenuArgs): Node {
  const { classes } = useStyles({ top });
  const [isVisible, setIsVisible] = React.useState(true);

  useScrollPosition(
    ({ previousPosition, currentPosition }) => {
      const isShow =
        visible &&
        (currentPosition.y > previousPosition.y || currentPosition.y > -10);
      if (isShow !== isVisible) setIsVisible(isShow);
    },
    [isVisible, visible]
  );

  return (
    <Fade in={isVisible}>
      <div className={isVisible ? classes.visible : classes.invisible}>
        {children}
      </div>
    </Fade>
  );
}
