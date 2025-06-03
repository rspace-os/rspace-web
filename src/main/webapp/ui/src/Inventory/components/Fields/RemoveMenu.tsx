import React, { useState } from "react";
import ListItemText from "@mui/material/ListItemText";
import { StyledMenu, StyledMenuItem } from "../../../components/StyledMenu";
import RemoveButton from "../../../components/RemoveButton";

export type DeleteOption = {
  value: boolean;
  label: string;
};

type RemoveMenuArgs = {
  deleteOptions: Array<DeleteOption>;
  onClick: (optionalAction: boolean) => void;
  tooltipTitle?: string;
};

const RemoveMenu = ({
  deleteOptions,
  onClick,
  tooltipTitle,
}: RemoveMenuArgs): React.ReactNode => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <div>
      <RemoveButton
        onClick={(event) => {
          setAnchorEl(event.currentTarget);
        }}
        title={tooltipTitle}
      />
      <StyledMenu
        anchorEl={anchorEl}
        keepMounted
        open={Boolean(anchorEl)}
        onClose={handleClose}
      >
        {deleteOptions.map((option) => (
          <StyledMenuItem
            key={String(option.value)}
            onClick={() => {
              onClick(option.value);
              setAnchorEl(null);
            }}
          >
            <ListItemText primary={option.label} />
          </StyledMenuItem>
        ))}
      </StyledMenu>
    </div>
  );
};

export default RemoveMenu;
