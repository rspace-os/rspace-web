//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import IconButton from "@mui/material/IconButton";
import MenuIcon from "@mui/icons-material/Menu";
import useStores from "../../../stores/use-stores";

const useStyles = makeStyles()((theme, { isSmall }) => ({
  appBar: {
    backgroundColor: "transparent",
    color: "grey",
    left: isSmall ? 68 : 0,
    width: isSmall ? "calc(100% - 68px)" : "100%",
  },
  toolbar: {
    display: "flex",
    justifyContent: "space-between",
    paddingLeft: isSmall ? 0 : 16,
  },
  spacer: {
    flexGrow: 1,
  },
}));

function Header(): Node {
  const { uiStore } = useStores();
  const { classes } = useStyles({ isSmall: uiStore.isSmall });

  const handleToggleOpen = () => {
    uiStore.toggleSidebar();
  };

  return (
    <AppBar position={"absolute"} className={classes.appBar} elevation={0}>
      <Toolbar className={classes.toolbar}>
        <>
          {uiStore.isVerySmall && (
            <IconButton
              color="inherit"
              aria-label="open drawer"
              onClick={handleToggleOpen}
              edge="start"
            >
              <MenuIcon />
            </IconButton>
          )}
          {(!uiStore.isSmall || !uiStore.sidebarOpen) && (
            <Typography
              component="h1"
              variant="h6"
              noWrap
              style={{ color: "black" }}
            >
              Inventory
            </Typography>
          )}
        </>
        <div className={classes.spacer} />
      </Toolbar>
    </AppBar>
  );
}

export default (observer(Header): ComponentType<{||}>);
