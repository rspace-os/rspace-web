import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef } from "react";
import { FIELD_DATA, type FieldType } from "@/stores/models/FieldTypes";

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
            <ListItemText primary={_fieldData.label} secondary={_fieldData.help} />
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
              {/*
               * MUI v9's MenuItem requires a surrounding MenuList/Menu context;
               * when used standalone (as the type-selector trigger) it must be
               * wrapped in a MenuList, otherwise it throws "MenuListContext is
               * missing". See FieldTypeMenu for the trigger usage.
               */}
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
