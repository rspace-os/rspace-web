import AddIcon from "@mui/icons-material/Add";
import IconButton from "@mui/material/IconButton";
import type React from "react";
import CustomTooltip from "./CustomTooltip";

type AddButtonArgs = {
  onClick?: (event: React.MouseEvent) => void;
  title?: string;
  disabled?: boolean;
  "data-test-id"?: string;
  id?: string;
};

const AddButton = ({
  onClick,
  title = "Add",
  disabled = false,
  "data-test-id": dataTestId,
  id,
}: AddButtonArgs): React.ReactNode => {
  return (
    // biome-ignore lint/a11y/useValidAriaValues: initial biome migration
<CustomTooltip title={title} aria-label="">
      <IconButton
        color="primary"
        component="span" // why not leaving it a button, who knows ?
        disabled={disabled}
        onClick={onClick}
        data-test-id={dataTestId}
        aria-label={title}
        id={id}
      >
        <AddIcon />
      </IconButton>
    </CustomTooltip>
  );
};

export default AddButton;
