import React, { forwardRef } from "react";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
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
            <div style={{ height: 72 }}>
              <Paper>{menuItem}</Paper>
            </div>
          )
        }
      </Observer>
    );
  },
);

FieldTypeMenuItem.displayName = "FieldTypeMenuItem";
export default FieldTypeMenuItem;
