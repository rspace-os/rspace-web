// @flow

import React, { type ComponentType } from "react";
import Skeleton from "@mui/material/Skeleton";
import TreeItem from "@mui/lab/TreeItem";

let nextUniqueId = 0;

const LoadingNode: ComponentType<{}> = () => (
  <TreeItem
    label={<Skeleton variant="text" animation="wave" height={32} />}
    nodeId={`${nextUniqueId++}`}
  />
);

export default LoadingNode;
