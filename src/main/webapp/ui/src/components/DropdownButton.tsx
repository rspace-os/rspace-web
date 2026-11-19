import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import Box from "@mui/material/Box";
import Button, { buttonClasses } from "@mui/material/Button";
import type { SxProps } from "@mui/system";
import visuallyHidden from "@mui/utils/visuallyHidden";
import type React from "react";
import { useId } from "react";
import CustomTooltip from "./CustomTooltip";

type DropdownButtonArgs = {
  name: React.ReactNode;
  children: React.ReactNode;
  onClick: (event: React.MouseEvent<HTMLElement>) => void;
  disabled?: boolean;
  title?: string;
  /**
   * Why the button is disabled. The button then stays focusable, ignores
   * activation, and shows the reason as its tooltip and accessible
   * description. Unlike `title`, it does not replace the accessible name.
   */
  disabledReason?: string;
  sx?: SxProps;
};

const DropdownButton = ({ name, children, onClick, disabled, title, disabledReason, sx }: DropdownButtonArgs) => {
  const reasonId = useId();
  // natively disabled buttons cannot be focused, so keyboard and screen-reader users would never learn the reason
  const softDisabled = Boolean(disabled && disabledReason);
  return (
    <Box>
      {/** biome-ignore lint/a11y/useValidAriaValues: initial biome migration */}
      <CustomTooltip title={(softDisabled && disabledReason) || title || ""} aria-label="">
        <Button
          endIcon={<KeyboardArrowDownIcon />}
          size="small"
          onClick={softDisabled ? undefined : onClick}
          disabled={disabled && !softDisabled}
          aria-disabled={softDisabled || undefined}
          aria-describedby={softDisabled ? reasonId : undefined}
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
            ...(softDisabled && {
              color: "action.disabled",
              cursor: "default",
              "&:hover": { backgroundColor: "transparent" },
            }),
            ...sx,
          }}
        >
          {name}
        </Button>
      </CustomTooltip>
      {softDisabled && (
        <Box component="span" id={reasonId} sx={visuallyHidden}>
          {disabledReason}
        </Box>
      )}
      {children}
    </Box>
  );
};

export default DropdownButton;
