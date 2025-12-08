import Fade from "@mui/material/Fade";
import LinearProgress from "@mui/material/LinearProgress";
import type React from "react";

type LoadingFadeArgs = {
    loading: boolean;
};

const LoadingFade = (props: LoadingFadeArgs): React.ReactNode => {
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
