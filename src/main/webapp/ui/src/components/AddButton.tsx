import React from "react";
import CustomTooltip from "./CustomTooltip";
import IconButton from "@mui/material/IconButton";
import AddIcon from "@mui/icons-material/Add";

type AddButtonArgs = {
  onClick?: (event: React.MouseEvent) => void;
  title?: string;
  disabled?: boolean;
  datatestid?: string;
  id?: string;
};

const AddButton = ({
  onClick,
  title = "Add",
  disabled = false,
  datatestid,
  id,
}: AddButtonArgs): React.ReactNode => {
  return (
    <CustomTooltip title={title} aria-label="">
      <IconButton
        color="primary"
        component="span" // why not leaving it a button, who knows ?
        disabled={disabled}
        onClick={onClick}
        data-testid={datatestid}
        aria-label={title}
        id={id}
      >
        <AddIcon />
      </IconButton>
    </CustomTooltip>
  );
};

export default AddButton;
