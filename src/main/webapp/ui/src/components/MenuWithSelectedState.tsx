import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import Box from "@mui/material/Box";
import Button, { buttonClasses } from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import { paperClasses } from "@mui/material/Paper";
import React from "react";
import type AccentMenuItem from "./AccentMenuItem";

/**
 * This component renders a button that shows a regular label and a label for
 * the current state, and when tapped a menu from which the user can choose a
 * different state.
 */
export default function MenuWithSelectedState({
  label,
  currentState,
  children,
  defaultState,
}: {
  label: string;
  currentState: string;
  children: ReadonlyArray<React.ReactElement<typeof AccentMenuItem>>;
  defaultState: string;
}) {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  return (
    <>
      <Button
        endIcon={<KeyboardArrowDownIcon />}
        size="small"
        color="primary"
        onClick={(event) => {
          setAnchorEl(event.currentTarget);
        }}
        variant={currentState !== defaultState ? "contained" : "outlined"}
        sx={(theme) => ({
          padding: theme.spacing(0, 1.5, 0, 0),
          minWidth: "unset",
          height: theme.spacing(4),
          textTransform: "uppercase",
          letterSpacing: "0.04em",
          [`& .${buttonClasses.endIcon}`]: {
            ml: 0.5,
          },
        })}
      >
        <Box component="span" sx={{ pl: 1.5, pt: 0.5, pb: 0.25, maxHeight: "100%" }}>
          {label}
          {":"}
        </Box>
        <Box
          component="span"
          sx={{
            textTransform: "capitalize",
            fontWeight: 400,
            pl: 1,
            pt: 1,
            pb: 0.625,
          }}
        >
          {currentState}
        </Box>
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => {
          setAnchorEl(null);
        }}
        onClick={() => {
          setAnchorEl(null);
        }}
        slotProps={{
          list: {
            disablePadding: true,
          },
        }}
        sx={{
          [`& .${paperClasses.root}`]: anchorEl ? { transform: "translate(0px, 4px) !important" } : {},
        }}
      >
        {children}
      </Menu>
    </>
  );
}
