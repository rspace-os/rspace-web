import React from "react";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";

type LoaderCircularArgs = {
  message?: string;
};

export default function LoaderCircular(
  props: LoaderCircularArgs
): React.ReactNode {
  return (
    <Box sx={{ width: "100%" }}>
      <CircularProgress
        sx={{
          width: "100px",
          height: "100px",
          position: "absolute",
          top: "calc(50% - 50px)",
          left: "calc(50% - 20px)",
        }}
      />
      <Box
        sx={{
          width: "200px",
          position: "absolute",
          top: "calc(50% + 20px)",
          left: "calc(50% - 100px)",
          textAlign: "center",
          fontSize: "15px",
        }}
      >
        {props.message}
      </Box>
    </Box>
  );
}
