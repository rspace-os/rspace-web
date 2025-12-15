import React from "react";
import ListIcon from "@mui/icons-material/List";
import CloseIcon from "@mui/icons-material/Close";

export default function FieldTypeMenuItemOpenIcon({
  open,
}: {
  open: boolean;
}): React.ReactNode {
  return open ? <CloseIcon /> : <ListIcon />;
}
