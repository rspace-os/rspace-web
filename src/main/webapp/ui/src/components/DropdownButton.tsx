//@flow strict

import React from "react";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import CustomTooltip from "./CustomTooltip";
import { styled, type SxProps } from "@mui/system";

type DropdownButtonArgs = {
  name: React.ReactNode;
  children: React.ReactNode;
  onClick: (event: React.MouseEvent<HTMLElement>) => void;
  disabled?: boolean;
  title?: string;
  sx?: SxProps;
};

const StyledButton = styled(Button)(({ theme }) => ({
  padding: theme.spacing(0, 0.75),
  minWidth: "unset",
  height: 32,
  textTransform: "none",
  letterSpacing: "0.04em",
  "& .MuiButton-endIcon": {
    marginLeft: theme.spacing(0.5),
  },
}));

const DropdownButton = ({
  name,
  children,
  onClick,
  disabled,
  title,
  sx,
}: DropdownButtonArgs) => (
  <Grid item>
    <CustomTooltip title={title ?? ""} aria-label="">
      <StyledButton
        endIcon={<KeyboardArrowDownIcon />}
        size="small"
        onClick={onClick}
        disabled={disabled}
        aria-label={title}
        color="standardIcon"
        sx={sx}
      >
        {name}
      </StyledButton>
    </CustomTooltip>
    {children}
  </Grid>
);

export default DropdownButton;
