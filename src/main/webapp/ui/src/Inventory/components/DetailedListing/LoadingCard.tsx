import React from "react";
import { observer } from "mobx-react-lite";
import Skeleton from "@mui/material/Skeleton";
import CardStructure from "./CardStructure";

function LoadingCard(): React.ReactNode {
  return (
    <CardStructure
      image={<Skeleton variant="rectangular" width="100%" height="100%" />}
      title={<Skeleton variant="text" animation="wave" />}
      subheader={<Skeleton variant="text" animation="wave" />}
      content={
        <>
          <Skeleton variant="text" animation="wave" />
          <Skeleton variant="text" animation="wave" />
          <Skeleton variant="text" animation="wave" />
        </>
      }
    />
  );
}

export default observer(LoadingCard);