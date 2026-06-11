import React, { forwardRef } from "react";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Paper from "@mui/material/Paper";
import { FIELD_DATA, type FieldType } from "@/stores/models/FieldTypes";
import { Observer } from "mobx-react-lite";

type FieldTypeMenuItemArgs = {
  field: FieldType;
  onClick: (event: React.MouseEvent) => void;
  inMenu?: boolean;
};

const FieldTypeMenuItem = forwardRef<HTMLLIElement, FieldTypeMenuItemArgs>(
  ({ field, onClick, inMenu = false }, ref) => {
    const _fieldData = FIELD_DATA[field];
    const menuItem = (
      <MenuItem
        ref={ref}
        onClick={onClick}
        sx={(theme) => ({
          p: theme.spacing(1, 2),
          backgroundColor: "initial !important",
          opacity: "1 !important",
        })}
        disabled={false}
      >
        <Stack direction="row" sx={{ alignItems: "center" }}>
          <ListItemAvatar>
            <Avatar>{_fieldData.icon}</Avatar>
          </ListItemAvatar>
          <Box sx={{ flexGrow: 1 }}>
            <ListItemText
              primary={_fieldData.label}
              secondary={_fieldData.help}
            />
          </Box>
        </Stack>
      </MenuItem>
    );

    return (
      <Observer>
        {() =>
          inMenu ? (
            menuItem
          ) : (
            <Box sx={{ height: 72 }}>
              {/* MUI 9 MenuItems require a Menu/MenuList ancestor; the
                  closed-state trigger renders outside the Menu, so it brings
                  its own MenuList */}
              <Paper>
                <MenuList disablePadding>{menuItem}</MenuList>
              </Paper>
            </Box>
          )
        }
      </Observer>
    );
  },
);

FieldTypeMenuItem.displayName = "FieldTypeMenuItem";
export default FieldTypeMenuItem;
