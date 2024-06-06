// @flow

import React, { type ComponentType } from "react";
import Skeleton from "@mui/material/Skeleton";
import { TreeItem } from "@mui/x-tree-view/TreeItem";

let nextUniqueId = 0;

const LoadingNode: ComponentType<{}> = () => (
  <TreeItem
    label={<Skeleton variant="text" animation="wave" height={32} />}
    itemId={`${nextUniqueId++}`}
  />
);

export default LoadingNode;
