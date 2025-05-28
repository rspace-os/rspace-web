import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
library.add(faSpinner);
import { withStyles } from "Styles";
import React from "react";
import { emptyObject } from "../../util/types";

const OverlayLoadingSpinner = withStyles<
  emptyObject,
  { overlay: string; wrapper: string; icon: string }
>((theme) => ({
  overlay: {
    display: "flex",
    justifyContent: "center",
    position: "absolute",
    height: "100%",
    top: 0,
    width: "100%",
    backgroundColor: "rgba(255,255,255,0.7)",
  },
  wrapper: {
    alignSelf: "center",
  },
  icon: { marginRight: "10px", color: theme.palette.standardIcon.main },
}))(({ classes }) => (
  <div className={classes.overlay}>
    <div className={classes.wrapper}>
      <FontAwesomeIcon icon="spinner" spin size="5x" className={classes.icon} />
    </div>
  </div>
));

OverlayLoadingSpinner.displayName = "OverlayLoadingSpinner";
export default OverlayLoadingSpinner;
