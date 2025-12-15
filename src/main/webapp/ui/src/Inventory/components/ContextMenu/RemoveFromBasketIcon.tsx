import React from "react";
import SvgIcon from "@mui/material/SvgIcon";

type RemoveFromBasketIconArgs = {
  // MUI blackmagic: passed className is 'MuiChip-icon'
  className?: string;
};

export default function RemoveFromBasketIcon({
  className,
}: RemoveFromBasketIconArgs): React.ReactNode {
  return (
    <SvgIcon className={className}>
      <path d="M17 19.22H5V7h7V5H5c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-10h-2v7.22z"></path>
      <path d="M 22 5 h -8 v 2 h 8 v -2 zM7 9h8v2H7zM7 12v2h8v-2h-3zM7 15h8v2H7z"></path>
    </SvgIcon>
  );
}
