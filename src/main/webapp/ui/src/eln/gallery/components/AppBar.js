//@flow

import React, { type Node, type ComponentType } from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import IconButton from "@mui/material/IconButton";
import Box from "@mui/material/Box";
import useViewportDimensions from "../../../util/useViewportDimensions";
import MenuIcon from "@mui/icons-material/Menu";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import SearchIcon from "@mui/icons-material/Search";
import TextField from "@mui/material/TextField";
import InputAdornment from "@mui/material/InputAdornment";
import { styled } from "@mui/material/styles";
import CloseIcon from "@mui/icons-material/Close";
import AccessibilityTips from "../../../components/AccessibilityTips";
import HelpDocs from "../../../components/Help/HelpDocs";
import HelpIcon from "@mui/icons-material/Help";
import { observer } from "mobx-react-lite";
import Stack from "@mui/material/Stack";
import Button from "@mui/material/Button";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import Menu from "@mui/material/Menu";
import AccentMenuItem from "./AccentMenuItem";
import NotebookIcon from "@mui/icons-material/AutoStories";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import FlaskIcon from "@mui/icons-material/Science";
import AppsIcon from "@mui/icons-material/AppRegistration";
import { COLOR as GALLERY_COLOR } from "../common";
import { COLOR as INVENTORY_COLOR } from "../../../Inventory/components/Layout/Sidebar";

const StyledCloseIcon = styled(CloseIcon)(({ theme }) => ({
  color: theme.palette.standardIcon.main,
  height: 20,
  width: 20,
}));

type GalleryAppBarArgs = {|
  appliedSearchTerm: string,
  setAppliedSearchTerm: (string) => void,
  setDrawerOpen: (boolean) => void,
  hideSearch: boolean,
  drawerOpen: boolean,
  sidebarId: string,
|};

function GalleryAppBar({
  appliedSearchTerm,
  setAppliedSearchTerm,
  hideSearch,
  setDrawerOpen,
  drawerOpen,
  sidebarId,
}: GalleryAppBarArgs): Node {
  const { isViewportVerySmall, isViewportSmall } = useViewportDimensions();
  const [showTextfield, setShowTextfield] = React.useState(false);
  const searchTextfield = React.useRef();
  const [appMenuAnchorEl, setAppMenuAnchorEl] = React.useState(null);
  function handleAppMenuClose() {
    setAppMenuAnchorEl(null);
  }

  /*
   * We use a copy of the search term string so that edits the user makes are
   * not immediately passed to useGalleryListing which will immediately make a
   * network call.
   */
  const [searchTerm, setSearchTerm] = React.useState("");
  React.useEffect(() => {
    setSearchTerm(appliedSearchTerm);
  }, [appliedSearchTerm]);

  return (
    <AppBar position="relative" open={true} aria-label="page header">
      <Toolbar variant="dense">
        <IconButton
          aria-label={drawerOpen ? "close drawer" : "open drawer"}
          aria-controls={sidebarId}
          aria-expanded={drawerOpen ? "true" : "false"}
          onClick={() => {
            setDrawerOpen(!drawerOpen);
          }}
        >
          <MenuIcon />
        </IconButton>
        {!isViewportSmall && (
          <>
            <Box flexGrow={1}></Box>
            <Stack direction="row" spacing={1} sx={{ mx: 1 }}>
              <Button href="/workspace">Workspace</Button>
              <Button aria-current="page" href="/gallery">
                Gallery
              </Button>
              <Button href="/inventory">Inventory</Button>
              <Button href="/apps">Apps</Button>
            </Stack>
          </>
        )}
        {isViewportSmall && (!isViewportVerySmall || !showTextfield) && (
          <>
            <List
              component="nav"
              aria-label="Main Navigation"
              disablePadding
              sx={{ ml: 1 }}
            >
              <ListItemButton
                dense
                id="app-menu-button"
                aria-haspopup="menu"
                aria-controls="app-menu"
                {...(open
                  ? {
                      ["aria-expanded"]: "true",
                    }
                  : {})}
                onClick={(event) => {
                  setAppMenuAnchorEl(event.currentTarget);
                }}
              >
                <ListItemText primary="Gallery" />
                <ListItemIcon>
                  <ArrowDropDownIcon />
                </ListItemIcon>
              </ListItemButton>
            </List>
            <Menu
              id="app-menu"
              anchorEl={appMenuAnchorEl}
              open={Boolean(appMenuAnchorEl)}
              onClose={handleAppMenuClose}
              MenuListProps={{
                "aria-labelledby": "app-menu-button",
              }}
              anchorOrigin={{
                vertical: "bottom",
                horizontal: "left",
              }}
              transformOrigin={{
                vertical: "top",
                horizontal: "left",
              }}
            >
              <AccentMenuItem
                title="Workspace"
                avatar={<NotebookIcon />}
                subheader="Notebooks and documents"
                foregroundColor={{
                  hue: 120,
                  saturation: 18,
                  lightness: 20,
                }}
                backgroundColor={{
                  hue: 120,
                  saturation: 18,
                  lightness: 71,
                }}
                onClick={() => {
                  window.location = "/workspace";
                  handleAppMenuClose();
                }}
              />
              <AccentMenuItem
                title="Gallery"
                avatar={<FileIcon />}
                subheader="Your files in RSpace and connected filestores"
                foregroundColor={GALLERY_COLOR.contrastText}
                backgroundColor={GALLERY_COLOR.main}
                onClick={() => {
                  window.location = "/gallery";
                  handleAppMenuClose();
                }}
              />
              <AccentMenuItem
                title="Inventory"
                avatar={<FlaskIcon />}
                subheader="Samples and laboratory resources"
                foregroundColor={INVENTORY_COLOR.contrastText}
                backgroundColor={INVENTORY_COLOR.main}
                onClick={() => {
                  window.location = "/inventory";
                  handleAppMenuClose();
                }}
              />
              <AccentMenuItem
                title="Apps"
                avatar={<AppsIcon />}
                subheader="Integrations and third-party applications"
                foregroundColor={{
                  hue: 200,
                  saturation: 10,
                  lightness: 20,
                }}
                backgroundColor={{
                  hue: 200,
                  saturation: 10,
                  lightness: 70,
                }}
                onClick={() => {
                  window.location = "/apps";
                  handleAppMenuClose();
                }}
              />
            </Menu>
            <Box flexGrow={1}></Box>
            {!showTextfield && (
              <IconButtonWithTooltip
                size="small"
                onClick={() => {
                  setShowTextfield(true);
                  setTimeout(() => {
                    searchTextfield.current?.focus();
                  }, 0);
                }}
                icon={<SearchIcon />}
                title="Search this folder"
              />
            )}
          </>
        )}
        {!hideSearch && (
          <>
            <Box
              flexGrow={1}
              sx={{
                //flexGrow: isViewportSmall ? 1 : 0,
                display: isViewportSmall && showTextfield ? "block" : "none",
              }}
            ></Box>
            <Box
              mx={1}
              sx={{
                //flexGrow: isViewportSmall ? 1 : 0,
                display: !isViewportSmall || showTextfield ? "block" : "none",
              }}
            >
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  setAppliedSearchTerm(searchTerm);
                  setShowTextfield(searchTerm !== "");
                }}
              >
                <TextField
                  placeholder="Search"
                  sx={{
                    /*
                     * 300px is just an arbitrary width so that the search box
                     * doesn't fill the entire app bar as the viewport grows
                     */
                    maxWidth: isViewportSmall ? "100%" : 300,
                  }}
                  value={searchTerm}
                  onChange={({ currentTarget: { value } }) =>
                    setSearchTerm(value)
                  }
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon />
                      </InputAdornment>
                    ),
                    endAdornment:
                      searchTerm !== "" ? (
                        <IconButtonWithTooltip
                          title="Clear"
                          icon={<StyledCloseIcon />}
                          size="small"
                          onClick={() => {
                            setSearchTerm("");
                            setAppliedSearchTerm("");
                            setShowTextfield(false);
                          }}
                        />
                      ) : null,
                  }}
                  inputProps={{
                    ref: searchTextfield,
                    onBlur: () => {
                      setSearchTerm(appliedSearchTerm);
                      setShowTextfield(appliedSearchTerm !== "");
                    },
                    "aria-label": "Search current folder",
                  }}
                />
              </form>
            </Box>
          </>
        )}
        <Box ml={1}>
          <AccessibilityTips
            elementType="dialog"
            supportsReducedMotion
            supportsHighContrastMode
            supports2xZoom
          />
        </Box>
        <Box ml={1}>
          <HelpDocs
            Action={({ onClick, disabled }) => (
              <IconButtonWithTooltip
                size="small"
                onClick={onClick}
                icon={<HelpIcon />}
                title="Open Help"
                disabled={disabled}
              />
            )}
          />
        </Box>
      </Toolbar>
    </AppBar>
  );
}

/**
 * GalleryAppBar is the header bar for the gallery page.
 */
export default (observer(GalleryAppBar): ComponentType<GalleryAppBarArgs>);
