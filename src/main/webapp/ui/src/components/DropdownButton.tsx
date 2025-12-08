//@flow strict

import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { type SxProps, styled } from "@mui/system";
import type React from "react";
import CustomTooltip from "./CustomTooltip";

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
    border: "none",
    "& .MuiButton-endIcon": {
        marginLeft: theme.spacing(0.5),
    },
}));

const DropdownButton = ({ name, children, onClick, disabled, title, sx }: DropdownButtonArgs) => (
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
