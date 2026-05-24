import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import FieldTypeMenuItemOpenIcon from "./FieldTypeMenuItemOpenIcon";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import React, { useState, forwardRef } from "react";
import Typography from "@mui/material/Typography";
import { useTheme } from "@mui/material/styles";
import useStores from "../../../stores/use-stores";
import {
  FIELD_DATA,
  hasOptions,
  type FieldType,
} from "../../../stores/models/FieldTypes";
import { preventEventBubbling } from "../../../util/Util";
import { Observer } from "mobx-react-lite";

type FieldTypeMenuItemArgs = {
  field: FieldType;
  onClick: (event: React.MouseEvent<HTMLLIElement>) => void;
  inMenu?: boolean;
  options?: string[] | null;
};

const FieldTypeMenuItem = forwardRef<HTMLLIElement, FieldTypeMenuItemArgs>(
  ({ field, onClick, inMenu = false, options = null }, ref) => {
    const _fieldData = FIELD_DATA[field];
    const [open, setOpen] = useState(false);
    const { uiStore } = useStores();
    const theme = useTheme();
    const menuItem = (
      <MenuItem
        ref={ref}
        onClick={onClick}
        sx={{
          p: theme.spacing(1, 2),
          backgroundColor: "initial !important",
          opacity: "1 !important",
          overflow: open ? "initial" : "hidden",
        }}
        disabled={open}
      >
        <Grid container sx={{ flexDirection: "column" }}>
          <Grid>
            <Grid container direction="row" sx={{ alignItems: "center" }}>
              <Grid>
                <ListItemAvatar>
                  <Avatar>{_fieldData.icon}</Avatar>
                </ListItemAvatar>
              </Grid>
              <Grid sx={{ flexGrow: 1 }}>
                <ListItemText
                  primary={_fieldData.label}
                  secondary={_fieldData.help}
                />
              </Grid>
              {!inMenu && hasOptions(field) && (
                <Grid>
                  <IconButton
                    onClick={preventEventBubbling<
                      React.MouseEvent<HTMLButtonElement>
                    >(() => setOpen(!open))}
                    sx={{
                      backgroundColor: "transparent",
                      pointerEvents: "auto",
                    }}
                  >
                    <FieldTypeMenuItemOpenIcon open={open} />
                  </IconButton>
                </Grid>
              )}
            </Grid>
          </Grid>
          <div
            style={{
              height: open ? 110 : 0,
              transition: "all 0.2s ease 0s",
              transitionDelay: open ? "0.05s" : "0s",
            }}
          >
            <Grid>
              <Box sx={{ mt: "8px", mb: "12px" }}>
                <Divider orientation="horizontal" />
              </Box>
            </Grid>
            {options && (
              <>
                <Grid
                  sx={{
                    position: "absolute",
                    top: theme.spacing(6),
                    backgroundColor: "white",
                    right: theme.spacing(8),
                    px: 1,
                    color: "rgba(0, 0, 0, 0.42)",
                    fontSize: "0.7rem",
                    opacity: open ? 1 : 0,
                    transition: "opacity 0.2s ease 0s",
                  }}
                >
                  <Typography variant="subtitle1" sx={{ fontSize: "0.9rem" }}>
                    Options
                  </Typography>
                </Grid>
                <Grid
                  sx={{
                    height: 89,
                    overflowY: "auto",
                    overflowX: "hidden",
                    pointerEvents: "auto",
                  }}
                >
                  <Grid container direction="row" spacing={1}>
                    {options.map((o, i) => (
                      <Grid key={i}>
                        <Chip label={o} variant="outlined" />
                      </Grid>
                    ))}
                  </Grid>
                </Grid>
              </>
            )}
          </div>
        </Grid>
      </MenuItem>
    );

    return (
      <Observer>
        {() =>
          inMenu ? (
            menuItem
          ) : (
            <div style={{ height: 72 }}>
              <Paper
                sx={{
                  transition: "transform 0.2s ease 0s, width 0.2s ease 0s",
                  transform: open
                    ? `translate(${uiStore.isVerySmall ? 0 : -20}px, -110px)`
                    : "none",
                  zIndex: open ? 2001 : 1,
                  position: "absolute",
                  transitionDelay: open ? "0s" : "0.05s",
                  width: "100%",
                }}
              >
                {menuItem}
              </Paper>
              <div
                style={{
                  backgroundColor: "rgba(0, 0, 0, 0.5)",
                  position: "fixed",
                  left: 0,
                  top: 0,
                  right: 0,
                  bottom: 0,
                  opacity: open ? 0.5 : 0,
                  zIndex: 2000,
                  transition: "all 0.4s ease 0s",
                  pointerEvents: open ? "auto" : "none",
                }}
                onClick={() => setOpen(false)}
                onKeyDown={(e) => {
                  if (e.key === "Escape") setOpen(false);
                }}
                role="button"
                tabIndex={0}
              />
            </div>
          )
        }
      </Observer>
    );
  },
);

FieldTypeMenuItem.displayName = "FieldTypeMenuItem";
export default FieldTypeMenuItem;
