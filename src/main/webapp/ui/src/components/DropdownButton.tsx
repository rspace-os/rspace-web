//@flow strict

import React from "react";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import CustomTooltip from "./CustomTooltip";
import { type SxProps } from "@mui/system";

type DropdownButtonArgs = {
  name: React.ReactNode;
  children: React.ReactNode;
  onClick: (event: React.MouseEvent<HTMLElement>) => void;
  disabled?: boolean;
  title?: string;
  sx?: SxProps;
};

const DropdownButton = ({
  name,
  children,
  onClick,
  disabled,
  title,
  sx,
}: DropdownButtonArgs) => (
  <Grid>
    <CustomTooltip title={title ?? ""} aria-label="">
      <Button
        endIcon={<KeyboardArrowDownIcon />}
        size="small"
        onClick={onClick}
        disabled={disabled}
        aria-label={title}
        color="standardIcon"
        sx={{
          padding: (theme) => theme.spacing(0, 0.75),
          minWidth: "unset",
          height: 32,
          textTransform: "none",
          letterSpacing: "0.04em",
          border: "none",
          "& .MuiButton-endIcon": {
            marginLeft: (theme) => theme.spacing(0.5),
          },
          ...sx,
        }}
      >
        {name}
      </Button>
    </CustomTooltip>
    {children}
  </Grid>
);

export default DropdownButton;
