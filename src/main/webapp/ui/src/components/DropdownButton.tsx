import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import Box from "@mui/material/Box";
import Button, { buttonClasses } from "@mui/material/Button";
import type { SxProps } from "@mui/system";
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

const DropdownButton = ({ name, children, onClick, disabled, title, sx }: DropdownButtonArgs) => (
  <Box>
    {/** biome-ignore lint/a11y/useValidAriaValues: initial biome migration */}
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
          [`& .${buttonClasses.endIcon}`]: {
            marginLeft: (theme) => theme.spacing(0.5),
          },
          ...sx,
        }}
      >
        {name}
      </Button>
    </CustomTooltip>
    {children}
  </Box>
);

export default DropdownButton;
