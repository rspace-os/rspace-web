import Fade from "@mui/material/Fade";
import LinearProgress from "@mui/material/LinearProgress";
import type React from "react";

type LoadingFadeArgs = {
  loading: boolean;
};

const LoadingFade = (props: LoadingFadeArgs): React.ReactNode => {
  return (
    <Fade in={props.loading}>
      <LinearProgress color="primary" sx={{ width: "100%", minWidth: "50px" }} />
    </Fade>
  );
};

export default LoadingFade;
