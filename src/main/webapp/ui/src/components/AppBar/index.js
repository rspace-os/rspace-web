//@flow

import React, { type Node, type ComponentType, type Element } from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Box from "@mui/material/Box";
import useViewportDimensions from "../../util/useViewportDimensions";
import IconButtonWithTooltip from "../IconButtonWithTooltip";
import { AccessibilityTipsMenuItem } from "../AccessibilityTips";
import HelpDocs from "../Help/HelpDocs";
import HelpIcon from "@mui/icons-material/Help";
import { observer } from "mobx-react-lite";
import Stack from "@mui/material/Stack";
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
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import MessageIcon from "@mui/icons-material/Message";
import SettingsIcon from "@mui/icons-material/Settings";
import PublicIcon from "@mui/icons-material/Public";
import LogoutIcon from "@mui/icons-material/Logout";
import JwtService from "../../common/JwtService";
import SidebarToggle from "./SidebarToggle";
import Link from "@mui/material/Link";
import Avatar from "@mui/material/Avatar";
import SvgIcon from "@mui/material/SvgIcon";
import { styled } from "@mui/material/styles";

const OrcidIcon = styled(({ className }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    version="1.1"
    viewBox="0 0 50 50"
    className={className}
  >
    <g>
      <g id="Layer_1">
        <g>
          <path
            className="greenElements"
            d="M49.3,25c0,13.4-10.9,24.3-24.3,24.3S.7,38.4.7,25,11.6.7,25,.7s24.3,10.9,24.3,24.3Z"
          />
          <g>
            <path
              className="whiteElements"
              d="M17.1,36.1h-2.9V15.7h2.9v20.3Z"
            />
            <path
              className="whiteElements"
              d="M21.4,15.7h7.9c7.5,0,10.8,5.4,10.8,10.2s-4.1,10.2-10.8,10.2h-7.9s0-20.4,0-20.4ZM24.3,33.4h4.7c6.6,0,8.1-5,8.1-7.5,0-4.1-2.6-7.5-8.3-7.5h-4.5v15.1h0Z"
            />
            <path
              className="whiteElements"
              d="M17.5,11.5c0,1-.9,1.9-1.9,1.9s-1.9-.9-1.9-1.9.9-1.9,1.9-1.9c1.1,0,1.9.9,1.9,1.9Z"
            />
          </g>
        </g>
      </g>
    </g>
  </svg>
))(() => ({
  "& .greenElements": {
    fill: "#a6ce39",
  },
  "& .whiteElements": {
    fill: "#fff",
  },
}));

function NavButtons({
  currentPage,
}: {|
  // eslint-disable-next-line no-undefined -- undefined in types is fine
  currentPage: "Gallery" | "Inventory" | "Workspace" | typeof undefined,
|}) {
  return (
    <Stack direction="row" spacing={2} sx={{ mx: 1 }}>
      <Link
        target="_self"
        aria-current={currentPage === "Workspace" ? "page" : false}
        href="/workspace"
      >
        Workspace
      </Link>
      <Link
        target="_self"
        aria-current={currentPage === "Gallery" ? "page" : false}
        href="/gallery"
      >
        Gallery
      </Link>
      <Link
        target="_self"
        aria-current={currentPage === "Inventory" ? "page" : false}
        href="/inventory"
      >
        Inventory
      </Link>
    </Stack>
  );
}

type GalleryAppBarArgs = {|
  currentPage?: "Gallery" | "Inventory" | "Workspace",
  sidebarToggle?: Element<typeof SidebarToggle>,
|};

function GalleryAppBar({
  currentPage,
  sidebarToggle,
}: GalleryAppBarArgs): Node {
  const { isViewportSmall } = useViewportDimensions();
  const [appMenuAnchorEl, setAppMenuAnchorEl] = React.useState(null);
  function handleAppMenuClose() {
    setAppMenuAnchorEl(null);
  }
  const [accountMenuAnchorEl, setAccountMenuAnchorEl] =
    React.useState<null | EventTarget>(null);

  return (
    <AppBar position="relative" open={true} aria-label="page header">
      <Toolbar variant="dense">
        {sidebarToggle}
        <Box width="40px" height="40px" sx={{ ml: 0.5 }}>
          <img src="/images/icons/rspaceLogo.svg" alt="rspace logo" />
        </Box>
        {!isViewportSmall && (
          <>
            <NavButtons currentPage={currentPage} />
            <Box flexGrow={1}></Box>
          </>
        )}
        {isViewportSmall && (
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
            </Menu>
            <Box flexGrow={1}></Box>
          </>
        )}
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
              horizontal: "right",
            }}
            transformOrigin={{
              vertical: "top",
              horizontal: "right",
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
              <ListItemIcon>
                <Avatar
                  sx={{
                    /*
                     * These colours are just for the mockup. We should generate
                     * a pair of colours from the username when there isn't an
                     * avatar to show
                     */
                    color: "#FBE9E7",
                    backgroundColor: "#FF5722",
                  }}
                >
                  J
                </Avatar>
              </ListItemIcon>
              <Stack>
                <ListItemText
                  sx={{ mt: 0.5 }}
                  primary="Joe Bloggs (jbloggs)"
                  secondary={<>bloggs@example.com</>}
                />
                <ListItemText
                  /*
                   * The styling of this component is dictated by the ORCID display guidelines
                   * https://info.orcid.org/documentation/integration-guide/orcid-id-display-guidelines/#Compact_ORCID_iD
                   */
                  sx={{ mt: -0.5 }}
                  primaryTypographyProps={{
                    sx: {
                      fontFamily: "monospace",
                      lineHeight: "1em",
                      fontSize: "0.8em",
                      alignItems: "center",
                      textDecoration: "underline",
                    },
                  }}
                  primary={
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <SvgIcon>
                        <OrcidIcon />
                      </SvgIcon>
                      <span>0000-0000-0000-0000</span>
                    </Stack>
                  }
                />
              </Stack>
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
              title="Apps"
              avatar={<AppsIcon />}
              compact
              onClick={() => {
                setAccountMenuAnchorEl(null);
                window.location = "/apps";
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
            <AccessibilityTipsMenuItem
              supportsReducedMotion
              supportsHighContrastMode
              supports2xZoom
              onClose={() => {
                setAccountMenuAnchorEl(null);
              }}
            />
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
            <Divider />
            <ListItem sx={{ py: 0, mb: 1, justifyContent: "flex-end" }}>
              <img
                src="/images/icons/rspaceLogoLarge.svg"
                alt="rspace logo"
                style={{ width: "min(100%, 120px)" }}
              />
            </ListItem>
          </Menu>
        </Box>
      </Toolbar>
    </AppBar>
  );
}

/**
 * GalleryAppBar is the header bar for the gallery page.
 */
export default (observer(GalleryAppBar): ComponentType<GalleryAppBarArgs>);
