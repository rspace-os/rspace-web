//@flow

import React, { type Node, type ComponentType } from "react";
import { makeStyles } from "tss-react/mui";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import IconButton from "@mui/material/IconButton";
import MenuIcon from "@mui/icons-material/Menu";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";

const useStyles = makeStyles()((theme) => ({
  appBar: {
    zIndex: theme.zIndex.drawer + 1,
    backgroundColor: `#fafafa`,
    color: "grey",
  },
  toolarBar: {
    paddingLeft: 25,
  },
  logo: {
    color: "black",
    paddingLeft: 7,
  },
}));

function Header(): Node {
  const { classes } = useStyles();
  const { uiStore } = useStores();

  const handleToggleOpen = () => {
    uiStore.toggleSidebar();
  };

  return (
    <AppBar position="relative" className={classes.appBar} elevation={0}>
      <Toolbar className={classes.toolarBar}>
        <IconButton
          color="inherit"
          aria-label="open drawer"
          onClick={handleToggleOpen}
          edge="start"
        >
          <MenuIcon />
        </IconButton>
        <Typography component="h1" variant="h6" noWrap className={classes.logo}>
          Inventory
        </Typography>
      </Toolbar>
    </AppBar>
  );
}

export default (observer(Header): ComponentType<{||}>);
