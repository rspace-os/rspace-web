//@flow

import Dialog from "@mui/material/Dialog";
import React, { type Node } from "react";
import { ThemeProvider } from "@mui/material/styles";
import DialogContent from "@mui/material/DialogContent";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import IconButton from "@mui/material/IconButton";
import MenuIcon from "@mui/icons-material/Menu";
import Typography from "@mui/material/Typography";
import Card from "@mui/material/Card";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import TextField from "@mui/material/TextField";
import Box from "@mui/material/Box";
import SearchIcon from "@mui/icons-material/Search";
import InputAdornment from "@mui/material/InputAdornment";
import styled from "@mui/material/styles/styled";
import Breadcrumbs from "@mui/material/Breadcrumbs";
import Drawer from "@mui/material/Drawer";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { library } from "@fortawesome/fontawesome-svg-core";
import {
  faImage,
  faFilm,
  faFile,
  faFileInvoice,
  faDatabase,
  faShapes,
  faCircleDown,
  faVolumeLow,
} from "@fortawesome/free-solid-svg-icons";
import { faNoteSticky } from "@fortawesome/free-regular-svg-icons";
import Divider from "@mui/material/Divider";
import Chip from "@mui/material/Chip";
import createAccentedTheme from "../../../accentedTheme";
import { COLORS as baseThemeColors } from "../../../theme";
import ChemistryIcon from "./ChemistryIcon";
import Avatar from "@mui/material/Avatar";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
library.add(faImage);
library.add(faFilm);
library.add(faFile);
library.add(faFileInvoice);
library.add(faDatabase);
library.add(faShapes);
library.add(faNoteSticky);
library.add(faCircleDown);
library.add(faVolumeLow);

const COLOR = {
  main: {
    hue: 190,
    saturation: 30,
    lightness: 80,
  },
  darker: {
    hue: 190,
    saturation: 30,
    lightness: 32,
  },
  contrastText: {
    hue: 190,
    saturation: 20,
    lightness: 29,
  },
  background: {
    hue: 190,
    saturation: 30,
    lightness: 80,
  },
  backgroundContrastText: {
    hue: 190,
    saturation: 20,
    lightness: 29,
  },
};

const CustomDialog = styled(Dialog)(() => ({
  "& .MuiDialog-container > .MuiPaper-root": {
    width: "1000px",
    maxWidth: "1000px",
    height: "calc(100% - 32px)", // 16px margin above and below dialog
  },
  "& .MuiDialogContent-root": {
    width: "100%",
    height: "calc(100% - 52px)", // 52px being the height of DialogActions
    overflowY: "unset",
    paddingBottom: 0,
  },
}));

const CustomDrawer = styled(Drawer)(({ open }) => ({
  width: open ? "200px" : "64px",
  transition: "width .15s ease-in-out",
  "& .MuiPaper-root": {
    position: "relative",
  },
  "& .MuiListItemText-root": {
    transition: "all .1s ease-in-out",
    opacity: open ? 1 : 0,
    transform: open ? "unset" : "translateX(-10px)",
  },
}));

const FileCard = styled(({ filename, className, checked }) => (
  <Grid item xs={3}>
    <Card elevation={0} className={className}>
      <Grid container direction="column" height="100%" flexWrap="nowrap">
        <Grid
          item
          sx={{
            flexShrink: 0,
            padding: "8px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            height: "calc(100% - 9999999px)",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <Avatar
            src="/images/icons/chemistry-file.png"
            sx={{
              width: "auto",
              height: "100%",
              aspectRatio: "1 / 1",
            }}
          />
        </Grid>
        <Grid
          item
          container
          direction="row"
          flexWrap="nowrap"
          alignItems="baseline"
          sx={{
            padding: "8px",
            paddingTop: 0,
          }}
        >
          <Grid
            item
            sx={{
              textAlign: "center",
              flexGrow: 1,
              ...(checked
                ? {
                    backgroundColor: "#35afef",
                    p: 0.25,
                    borderRadius: "4px",
                    mx: 0.5,
                  }
                : {}),
            }}
          >
            <Typography
              sx={{
                color: checked
                  ? `hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, 99%)`
                  : `hsl(${COLOR.contrastText.hue} ${COLOR.contrastText.saturation}% ${COLOR.contrastText.lightness}% / 100%)`,
                fontSize: "0.8125rem",

                // wrap onto a second line, but use an ellipsis after that
                overflowWrap: "anywhere",
                overflow: "hidden",
                textOverflow: "ellipsis",
                display: "-webkit-box",
                "-webkit-line-clamp": "2",
                "-webkit-box-orient": "vertical",
              }}
            >
              {filename}
            </Typography>
          </Grid>
        </Grid>
      </Grid>
    </Card>
  </Grid>
))(({ checked }) => ({
  height: "150px",
  border: checked
    ? `2px solid hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`
    : `2px solid hsl(${COLOR.main.hue} ${COLOR.main.saturation}% ${COLOR.main.lightness}%)`,
  borderRadius: "8px",
  boxShadow: checked ? "none" : "hsl(19 66% 20% / 20%) 0px 2px 8px 0px",
}));

export default function Wrapper(): Node {
  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <Picker />
    </ThemeProvider>
  );
}

function Picker(): Node {
  const [drawerOpen, setDrawerOpen] = React.useState(true);

  return (
    <CustomDialog open>
      <AppBar position="relative" open={true}>
        <Toolbar variant="dense">
          <IconButton
            onClick={() => {
              setDrawerOpen(!drawerOpen);
            }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" noWrap component="h3">
            Gallery
          </Typography>
          <Box flexGrow={1}></Box>
          <TextField
            placeholder="Search"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
          />
          <Box ml={1} sx={{ transform: "translateY(2px)" }}>
            <HelpLinkIcon title="Importing from Gallery help" link="#" />
          </Box>
        </Toolbar>
      </AppBar>
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <CustomDrawer open={drawerOpen} anchor="left" variant="permanent">
          <Box sx={{ overflowY: "auto", overflowX: "hidden" }}>
            <List>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="image" />
                  </ListItemIcon>
                  <ListItemText primary="IMAGES" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="volume-low" />
                  </ListItemIcon>
                  <ListItemText primary="AUDIO" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="film" />
                  </ListItemIcon>
                  <ListItemText primary="VIDEOS" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="file" />
                  </ListItemIcon>
                  <ListItemText primary="DOCUMENTS" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton selected>
                  <ListItemIcon>
                    <ChemistryIcon />
                  </ListItemIcon>
                  <ListItemText primary="CHEMISTRY" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="file-invoice" />
                  </ListItemIcon>
                  <ListItemText primary="DMPS" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="database" />
                  </ListItemIcon>
                  <ListItemText primary="FILESTORES" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="fa-regular fa-note-sticky" />
                  </ListItemIcon>
                  <ListItemText primary="SNIPPETS" />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="shapes" />
                  </ListItemIcon>
                  <ListItemText primary="MISCELLANEOUS" />
                </ListItemButton>
              </ListItem>
            </List>
            <Divider />
            <List>
              <ListItem disablePadding>
                <ListItemButton>
                  <ListItemIcon>
                    <FontAwesomeIcon icon="fa-circle-down" />
                  </ListItemIcon>
                  <ListItemText primary="EXPORTS" />
                </ListItemButton>
              </ListItem>
            </List>
          </Box>
        </CustomDrawer>
        <Box
          sx={{
            height: "100%",
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <DialogContent>
            <Grid
              container
              direction="column"
              spacing={2}
              sx={{ height: "100%", flexWrap: "nowrap" }}
            >
              <Grid item>
                <Typography variant="h3">chemistry</Typography>
                <Breadcrumbs
                  separator="›"
                  aria-label="breadcrumb"
                  sx={{ mt: 1 }}
                >
                  <Chip size="small" clickable label="Chemistry" />
                  <Chip size="small" clickable label="Examples" />
                </Breadcrumbs>
              </Grid>
              <Grid item></Grid>
              <Grid
                item
                sx={{ overflowY: "auto", pt: "0 !important" }}
                flexGrow={1}
              >
                <Grid container spacing={2}>
                  <FileCard filename="Aminoglutethimide.mol" />
                  <FileCard
                    filename="Thisfilehasareallylongnamethatneedstowrap"
                    checked
                  />
                  <FileCard filename="Thisfilehasareallylongnamethatneedstowrap_itssolongthatitwrapsontomanymanylines" />
                </Grid>
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button>Cancel</Button>
            <Button variant="contained">Add</Button>
          </DialogActions>
        </Box>
      </Box>
    </CustomDialog>
  );
}
