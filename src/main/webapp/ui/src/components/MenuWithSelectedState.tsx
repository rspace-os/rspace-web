import React from "react";
import AccentMenuItem from "./AccentMenuItem";
import Button from "@mui/material/Button";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import Menu from "@mui/material/Menu";

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
          "& .MuiButton-endIcon": {
            marginLeft: theme.spacing(0.5),
          },
          "& span.label": {
            paddingLeft: theme.spacing(1.5),
            maxHeight: "100%",
            paddingTop: theme.spacing(0.5),
            paddingBottom: theme.spacing(0.25),
          },
          "& span.state": {
            textTransform: "capitalize",
            fontWeight: 400,
            paddingLeft: theme.spacing(1),
            paddingTop: theme.spacing(1),
            paddingBottom: theme.spacing(0.625),
          },
        })}
      >
        <span className="label">{label}:</span>
        <span className="state">{currentState}</span>
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
          "& .MuiPaper-root": {
            ...(anchorEl
              ? {
                  transform: "translate(0px, 4px) !important",
                }
              : {}),
          },
        }}
      >
        {children}
      </Menu>
    </>
  );
}
