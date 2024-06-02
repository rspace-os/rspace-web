//@flow

import React, { forwardRef, type ComponentType } from "react";
import Avatar from "@mui/material/Avatar";
import Grid from "@mui/material/Grid";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import useStores from "../../../stores/use-stores";
import { FIELD_DATA, type FieldType } from "../../../stores/models/FieldTypes";
import { makeStyles } from "tss-react/mui";
import { Observer } from "mobx-react-lite";

const useStyles = makeStyles()((theme) => ({
  menuItem: {
    padding: theme.spacing(1, 2),
    backgroundColor: "initial !important",
    opacity: "1 !important",
  },
  text: {
    flexGrow: 1,
  },
  outOfMenuWrapper: {
    height: 72,
  },
}));

type FieldTypeMenuItemProps = {
  field: FieldType,
  onClick: (Event) => void,
  inMenu?: boolean,
};

const FieldTypeMenuItem: ComponentType<FieldTypeMenuItemProps> = forwardRef<
  FieldTypeMenuItemProps,
  {}
>(
  // eslint-disable-next-line react/prop-types
  ({ field, onClick, inMenu = false }: FieldTypeMenuItemProps, ref) => {
    const { uiStore } = useStores();
    const { classes } = useStyles({ isVerySmall: uiStore.isVerySmall });

    const _fieldData = FIELD_DATA[field];
    const menuItem = (
      <MenuItem
        ref={ref}
        onClick={onClick}
        className={classes.menuItem}
        disabled={false}
      >
        <Grid container direction="column">
          <Grid item>
            <Grid container direction="row" alignItems="center">
              <Grid item>
                <ListItemAvatar>
                  <Avatar>{_fieldData.icon}</Avatar>
                </ListItemAvatar>
              </Grid>
              <Grid item className={classes.text}>
                <ListItemText
                  primary={_fieldData.label}
                  secondary={_fieldData.help}
                />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </MenuItem>
    );

    return (
      <Observer>
        {() =>
          inMenu ? (
            menuItem
          ) : (
            <div className={classes.outOfMenuWrapper}>
              <Paper className={classes.outOfMenuPaper}>{menuItem}</Paper>
            </div>
          )
        }
      </Observer>
    );
  }
);

FieldTypeMenuItem.displayName = "FieldTypeMenuItem";
export default FieldTypeMenuItem;
