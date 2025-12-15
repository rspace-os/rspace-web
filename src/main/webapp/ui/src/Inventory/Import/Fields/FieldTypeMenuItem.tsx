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
import useStores from "../../../stores/use-stores";
import {
  FIELD_DATA,
  hasOptions,
  type FieldType,
} from "../../../stores/models/FieldTypes";
import { makeStyles } from "tss-react/mui";
import { preventEventBubbling } from "../../../util/Util";
import { Observer } from "mobx-react-lite";

const useStyles = makeStyles<{ open: boolean; isVerySmall: boolean }>()(
  (theme, { open, isVerySmall }) => ({
    menuItem: {
      padding: theme.spacing(1, 2),
      backgroundColor: "initial !important",
      opacity: "1 !important",
      overflow: open ? "initial" : "hidden",
    },
    text: {
      flexGrow: 1,
    },
    openButton: {
      backgroundColor: "transparent",
      pointerEvents: "auto",
    },
    openPanel: {
      height: open ? 110 : 0,
      transition: "all 0.2s ease 0s",
      transitionDelay: open ? "0.05s" : "0s",
    },
    floatingOptionsHeading: {
      position: "absolute",
      top: theme.spacing(6),
      backgroundColor: "white",
      right: theme.spacing(8),
      padding: theme.spacing(0, 1),
      color: "rgba(0, 0, 0, 0.42)",
      fontSize: "0.7rem",
      opacity: open ? 1.0 : 0.0,
      transition: "opacity 0.2s ease 0s",
    },
    floatingOptionsHeadingText: {
      fontSize: "0.9rem",
    },
    optionsScrollPanel: {
      height: 89,
      overflowY: "auto",
      overflowX: "hidden",
      pointerEvents: "auto",
    },
    outOfMenuWrapper: {
      height: 72,
    },
    outOfMenuPaper: {
      transition: "transform 0.2s ease 0s, width 0.2s ease 0s",
      transform: open
        ? `translate(${isVerySmall ? 0 : -20}px, -110px)`
        : "none",
      zIndex: open ? 2001 : 1,
      position: "absolute",
      transitionDelay: open ? "0s" : "0.05s",
      width: "100%",
    },
    backdrop: {
      backgroundColor: "rgba(0, 0, 0, 0.5)",
      position: "fixed",
      left: 0,
      top: 0,
      right: 0,
      bottom: 0,
      opacity: open ? 0.5 : 0.0,
      zIndex: 2000,
      transition: "all 0.4s ease 0s",
      pointerEvents: open ? "auto" : "none",
    },
  })
);

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
    const { classes } = useStyles({ open, isVerySmall: uiStore.isVerySmall });
    const menuItem = (
      <MenuItem
        ref={ref}
        onClick={onClick}
        className={classes.menuItem}
        disabled={open}
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
              {!inMenu && hasOptions(field) && (
                <Grid item>
                  <IconButton
                    onClick={preventEventBubbling<
                      React.MouseEvent<HTMLButtonElement>
                    >(() => setOpen(!open))}
                    className={classes.openButton}
                  >
                    <FieldTypeMenuItemOpenIcon open={open} />
                  </IconButton>
                </Grid>
              )}
            </Grid>
          </Grid>
          <div className={classes.openPanel}>
            <Grid item>
              <Box mt="8px" mb="12px">
                <Divider orientation="horizontal" />
              </Box>
            </Grid>
            {options && (
              <>
                <Grid item className={classes.floatingOptionsHeading}>
                  <Typography
                    variant="subtitle1"
                    className={classes.floatingOptionsHeadingText}
                  >
                    Options
                  </Typography>
                </Grid>
                <Grid item className={classes.optionsScrollPanel}>
                  <Grid container direction="row" spacing={1}>
                    {options.map((o, i) => (
                      <Grid item key={i}>
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
            <div className={classes.outOfMenuWrapper}>
              <Paper className={classes.outOfMenuPaper}>{menuItem}</Paper>
              <div
                className={classes.backdrop}
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
  }
);

FieldTypeMenuItem.displayName = "FieldTypeMenuItem";
export default FieldTypeMenuItem;
