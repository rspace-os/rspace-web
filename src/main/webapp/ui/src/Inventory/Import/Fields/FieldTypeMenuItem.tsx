import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { Observer } from "mobx-react-lite";
// biome-ignore lint/style/useImportType: initial biome migration
import React, { forwardRef, useState } from "react";
import { FIELD_DATA, type FieldType, hasOptions } from "../../../stores/models/FieldTypes";
import useStores from "../../../stores/use-stores";
import { preventEventBubbling } from "../../../util/Util";
import FieldTypeMenuItemOpenIcon from "./FieldTypeMenuItemOpenIcon";

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
        <Box>
          <Stack direction="row" sx={{ alignItems: "center" }}>
            <ListItemAvatar>
              <Avatar>{_fieldData.icon}</Avatar>
            </ListItemAvatar>
            <Box sx={{ flexGrow: 1 }}>
              <ListItemText primary={_fieldData.label} secondary={_fieldData.help} />
            </Box>
            {!inMenu && hasOptions(field) && (
              <IconButton
                onClick={preventEventBubbling<React.MouseEvent<HTMLButtonElement>>(() => setOpen(!open))}
                sx={{
                  backgroundColor: "transparent",
                  pointerEvents: "auto",
                }}
              >
                <FieldTypeMenuItemOpenIcon open={open} />
              </IconButton>
            )}
          </Stack>
          <Box
            sx={{
              height: open ? 110 : 0,
              transition: "all 0.2s ease 0s",
              transitionDelay: open ? "0.05s" : "0s",
            }}
          >
            <Box sx={{ mt: "8px", mb: "12px" }}>
              <Divider orientation="horizontal" />
            </Box>
            {options && (
              <>
                <Box
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
                </Box>
                <Box
                  sx={{
                    height: 89,
                    overflowY: "auto",
                    overflowX: "hidden",
                    pointerEvents: "auto",
                  }}
                >
                  <Stack direction="row" spacing={1}>
                    {options.map((o, i) => (
                      <Chip key={i} label={o} variant="outlined" />
                    ))}
                  </Stack>
                </Box>
              </>
            )}
          </Box>
        </Box>
      </MenuItem>
    );

    return (
      <Observer>
        {() =>
          inMenu ? (
            menuItem
          ) : (
            <Box sx={{ height: 72 }}>
              <Paper
                sx={{
                  transition: "transform 0.2s ease 0s, width 0.2s ease 0s",
                  transform: open ? `translate(${uiStore.isVerySmall ? 0 : -20}px, -110px)` : "none",
                  zIndex: open ? 2001 : 1,
                  position: "absolute",
                  transitionDelay: open ? "0s" : "0.05s",
                  width: "100%",
                }}
              >
                {menuItem}
              </Paper>
              <Box
                sx={{
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
            </Box>
          )
        }
      </Observer>
    );
  },
);

FieldTypeMenuItem.displayName = "FieldTypeMenuItem";
export default FieldTypeMenuItem;
