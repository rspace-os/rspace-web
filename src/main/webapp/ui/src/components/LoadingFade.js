//@flow

import React, { type Node } from "react";
import LinearProgress from "@mui/material/LinearProgress";
import Fade from "@mui/material/Fade";

type LoadingFadeArgs = {|
  loading: boolean,
|};

const LoadingFade = (props: LoadingFadeArgs): Node => {
  const styles = {
    loadingBar: {
      width: "100%",
      minWidth: "50px",
    },
  };

  return (
    <Fade in={props.loading}>
      <LinearProgress color="primary" style={styles.loadingBar} />
    </Fade>
  );
};

export default LoadingFade;
