/*
 * Applied all over the UI, this component allows us to add tooltips to all
 * sorts of buttons, widgets, and links.
 *
 * Due to its ubiquity throughout the application, its dependencies SHOULD be
 * kept to a minimum, and MUST NOT include any global state.
 */

import Box from "@mui/material/Box";
import Tooltip from "@mui/material/Tooltip";
import type React from "react";

type CustomTooltipArgs = {
  title: string;
  enterDelay?: number;
  children: React.ReactNode;
  block?: boolean;
  "aria-hidden"?: boolean;
  "aria-label"?: string;
};

export default function CustomTooltip({
  title,
  enterDelay = 200,
  children,
  block = false,
  "aria-hidden": ariaHidden,
  "aria-label": ariaLabel,
}: CustomTooltipArgs): React.ReactNode {
  return (
    <Tooltip
      aria-hidden={ariaHidden}
      aria-label={ariaLabel ?? title}
      title={title}
      enterDelay={enterDelay}
      slotProps={{
        tooltip: {
          sx: (theme) => ({
            fontSize: theme.typography.pxToRem(12),
            zIndex: 3000,
          }),
        },
      }}
      role="tooltip"
    >
      <Box component="span" sx={block ? { display: "block", height: 24 } : undefined}>
        {children}
      </Box>
    </Tooltip>
  );
}
