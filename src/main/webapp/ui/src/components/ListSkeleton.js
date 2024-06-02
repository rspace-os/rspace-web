//@flow

import React, { type Node } from "react";
import Skeleton from "@mui/material/Skeleton";

export default function ListSkeleton(): Node {
  return (
    <>
      <Skeleton variant="rectangular" height={60} width="100%" />
      <Skeleton />
      <Skeleton />
    </>
  );
}
