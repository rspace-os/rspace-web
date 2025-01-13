//@flow

import React, { type Node, type ComponentType, type Element } from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Box from "@mui/material/Box";
import useViewportDimensions from "../../util/useViewportDimensions";
import IconButtonWithTooltip from "../IconButtonWithTooltip";
import SearchIcon from "@mui/icons-material/Search";
import TextField from "@mui/material/TextField";
import InputAdornment from "@mui/material/InputAdornment";
import { styled } from "@mui/material/styles";
import CloseIcon from "@mui/icons-material/Close";
import AccessibilityTips from "../AccessibilityTips";
import HelpDocs from "../Help/HelpDocs";
import HelpIcon from "@mui/icons-material/Help";
import { observer } from "mobx-react-lite";
import Stack from "@mui/material/Stack";
import Button from "@mui/material/Button";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import Divider from "@mui/material/Divider";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import Menu, { menuClasses } from "@mui/material/Menu";
import AccentMenuItem from "../AccentMenuItem";
import ListItem from "@mui/material/ListItem";
import NotebookIcon from "@mui/icons-material/AutoStories";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import FlaskIcon from "@mui/icons-material/Science";
import AppsIcon from "@mui/icons-material/AppRegistration";
import { COLOR as GALLERY_COLOR } from "../../eln/gallery/common";
import { COLOR as INVENTORY_COLOR } from "../../Inventory/components/Layout/Sidebar";
import { COLOR as APPS_COLOR } from "../../eln/apps/App";
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import MessageIcon from "@mui/icons-material/Message";
import SettingsIcon from "@mui/icons-material/Settings";
import PublicIcon from "@mui/icons-material/Public";
import LogoutIcon from "@mui/icons-material/Logout";
import JwtService from "../../common/JwtService";
import SidebarToggle from "./SidebarToggle";

const StyledCloseIcon = styled(CloseIcon)(({ theme }) => ({
  color: theme.palette.standardIcon.main,
  height: 20,
  width: 20,
}));

type GalleryAppBarArgs = {|
  currentPage: "Apps" | "Gallery" | "Inventory",
  appliedSearchTerm: string,
  setAppliedSearchTerm: (string) => void,
  hideSearch: boolean,
  sidebarToggle?: Element<typeof SidebarToggle>,
|};

function GalleryAppBar({
  currentPage,
  appliedSearchTerm,
  setAppliedSearchTerm,
  hideSearch,
  sidebarToggle,
}: GalleryAppBarArgs): Node {
  const { isViewportVerySmall, isViewportSmall } = useViewportDimensions();
  const [showTextfield, setShowTextfield] = React.useState(false);
  const searchTextfield = React.useRef();
  const [appMenuAnchorEl, setAppMenuAnchorEl] = React.useState(null);
  function handleAppMenuClose() {
    setAppMenuAnchorEl(null);
  }
  const [accountMenuAnchorEl, setAccountMenuAnchorEl] =
    React.useState<null | EventTarget>(null);

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
        {sidebarToggle}
        {!isViewportSmall && (
          <>
            <Box flexGrow={1}></Box>
            <Stack direction="row" spacing={1} sx={{ mx: 1 }}>
              <Button href="/workspace">Workspace</Button>
              <Button
                aria-current={currentPage === "Gallery" ? "page" : "false"}
                href="/gallery"
              >
                Gallery
              </Button>
              <Button
                aria-current={currentPage === "Inventory" ? "page" : false}
                href="/inventory"
              >
                Inventory
              </Button>
              <Button
                aria-current={currentPage === "Apps" ? "page" : "false"}
                href="/apps"
              >
                Apps
              </Button>
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
                {...(appMenuAnchorEl
                  ? {
                      ["aria-expanded"]: "true",
                    }
                  : {})}
                onClick={(event) => {
                  setAppMenuAnchorEl(event.currentTarget);
                }}
              >
                <ListItemText primary={currentPage} />
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
                disablePadding: true,
              }}
              anchorOrigin={{
                vertical: "bottom",
                horizontal: "left",
              }}
              transformOrigin={{
                vertical: "top",
                horizontal: "left",
              }}
              sx={{
                [`.${menuClasses.paper}`]: {
                  /*
                   * Generally we don't add box shadows to menus, but we do add
                   * box shadows to popups opened from the app bar to make them
                   * hover over the page's content.
                   */
                  boxShadow:
                    "3px 5px 5px -3px rgba(0,0,0,0.2),0px 8px 10px 1px rgba(0,0,0,0.14),0px 3px 14px 2px rgba(0,0,0,0.12)",
                },
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
                foregroundColor={APPS_COLOR.contrastText}
                backgroundColor={APPS_COLOR.main}
                onClick={() => {
                  window.location = "/apps";
                  handleAppMenuClose();
                }}
              />
            </Menu>
            <Box flexGrow={1}></Box>
            {!showTextfield && !hideSearch && (
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
          <IconButtonWithTooltip
            size="small"
            onClick={(event) => {
              setAccountMenuAnchorEl(event.currentTarget);
            }}
            icon={<AccountCircleIcon />}
            title="Account Menu"
            id="account-menu-button"
            aria-haspopup="menu"
            aria-controls="account-menu"
            {...(accountMenuAnchorEl
              ? {
                  ["aria-expanded"]: "true",
                }
              : {})}
          />
          <Menu
            id="account-menu"
            anchorEl={accountMenuAnchorEl}
            open={Boolean(accountMenuAnchorEl)}
            onClose={() => {
              setAccountMenuAnchorEl(null);
            }}
            MenuListProps={{
              "aria-labelledby": "account-menu-button",
              disablePadding: true,
              sx: { pt: 0.5 },
            }}
            anchorOrigin={{
              vertical: "bottom",
              horizontal: "left",
            }}
            transformOrigin={{
              vertical: "top",
              horizontal: "left",
            }}
            sx={{
              [`.${menuClasses.paper}`]: {
                /*
                 * Generally we don't add box shadows to menus, but we do add
                 * box shadows to popups opened from the app bar to make them
                 * hover over the page's content.
                 */
                boxShadow:
                  "3px 5px 5px -3px rgba(0,0,0,0.2),0px 8px 10px 1px rgba(0,0,0,0.14),0px 3px 14px 2px rgba(0,0,0,0.12)",
              },
            }}
          >
            <ListItem sx={{ py: 0 }}>
              <ListItemText
                sx={{ mt: 0.5 }}
                primary="Joe Bloggs"
                secondary="ORCID: -----"
              />
            </ListItem>
            <Divider sx={{ my: 0.5 }} />
            <AccentMenuItem
              title="Messaging"
              avatar={<MessageIcon />}
              compact
              onClick={() => {
                setAccountMenuAnchorEl(null);
                window.location = "/dashboard";
              }}
            />
            <AccentMenuItem
              title="My RSpace"
              avatar={<SettingsIcon />}
              compact
              onClick={() => {
                setAccountMenuAnchorEl(null);
                window.location = "/userform";
              }}
            />
            <AccentMenuItem
              title="Published"
              avatar={<PublicIcon />}
              compact
              onClick={() => {
                setAccountMenuAnchorEl(null);
                window.location = "/public/publishedView/publishedDocuments";
              }}
            />
            <Divider />
            <AccentMenuItem
              title="Log Out"
              avatar={<LogoutIcon />}
              compact
              onClick={() => {
                JwtService.destroyToken();
                setAccountMenuAnchorEl(null);
                window.location = "/logout";
              }}
            />
          </Menu>
        </Box>
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
