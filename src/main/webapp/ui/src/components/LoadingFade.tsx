import React from "react";
import LinearProgress from "@mui/material/LinearProgress";
import Fade from "@mui/material/Fade";

type LoadingFadeArgs = {
  loading: boolean;
};

const LoadingFade = (props: LoadingFadeArgs): React.ReactNode => {
  return (
    <Fade in={props.loading}>
      <LinearProgress
        color="primary"
        sx={{ width: "100%", minWidth: "50px" }}
      />
    </Fade>
  );
};

export default LoadingFade;
