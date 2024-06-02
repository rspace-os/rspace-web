//@flow

import React, { type Node } from "react";
import ListIcon from "@mui/icons-material/List";
import CloseIcon from "@mui/icons-material/Close";

export default function FieldTypeMenuItemOpenIcon({
  open,
}: {
  open: boolean,
}): Node {
  return open ? <CloseIcon /> : <ListIcon />;
}
