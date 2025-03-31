import React from "react";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Box from "@mui/material/Box";
import useViewportDimensions from "../../util/useViewportDimensions";
import IconButtonWithTooltip from "../IconButtonWithTooltip";
import {
  AccessibilityTipsMenuItem,
  AccessibilityTipsIconButton,
} from "../AccessibilityTips";
import HelpDocs from "../Help/HelpDocs";
import HelpIcon from "@mui/icons-material/Help";
import { observer } from "mobx-react-lite";
import Stack from "@mui/material/Stack";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import { Menu } from "../DialogBoundary";
import { menuClasses } from "@mui/material/Menu";
import AccentMenuItem from "../AccentMenuItem";
import ListItem from "@mui/material/ListItem";
import NotebookIcon from "@mui/icons-material/AutoStories";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import FlaskIcon from "@mui/icons-material/Science";
import AppsIcon from "@mui/icons-material/AppRegistration";
import { ACCENT_COLOR as GALLERY_COLOR } from "../../assets/branding/rspace/gallery";
import { ACCENT_COLOR as INVENTORY_COLOR } from "../../assets/branding/rspace/inventory";
import { ACCENT_COLOR as WORKSPACE_COLOR } from "../../assets/branding/rspace/workspace";
import { ACCENT_COLOR as SYSADMIN_COLOR } from "../../assets/branding/rspace/sysadmin";
import { ACCENT_COLOR as OTHER_COLOR } from "../../assets/branding/rspace/other";
import MessageIcon from "@mui/icons-material/Message";
import ProfileIcon from "@mui/icons-material/ManageAccounts";
import PublicIcon from "@mui/icons-material/Public";
import LogoutIcon from "@mui/icons-material/Logout";
import SystemIcon from "@mui/icons-material/SettingsInputComposite";
import JwtService from "../../common/JwtService";
import SidebarToggle from "./SidebarToggle";
import Link from "@mui/material/Link";
import Avatar from "@mui/material/Avatar";
import SvgIcon from "@mui/material/SvgIcon";
import { styled, useTheme, darken, lighten } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import HelpLinkIcon from "../HelpLinkIcon";
import docLinks from "../../assets/DocLinks";
import VisuallyHiddenHeading from "../VisuallyHiddenHeading";
import useUiNavigationData, {
  type UiNavigationData,
} from "./useUiNavigationData";
import * as FetchingData from "../../util/fetchingData";
import * as Parsers from "../../util/parsers";
import OperateAsIcon from "@mui/icons-material/SupervisedUserCircleOutlined";
import CircularProgress from "@mui/material/CircularProgress";
import MaintenanceIcon from "@mui/icons-material/Construction";
import Popover from "@mui/material/Popover";
import IconButton from "@mui/material/IconButton";
import { getRelativeTime } from "../../stores/definitions/Units";
import Result from "../../util/result";
import useSessionStorage from "../../util/useSessionStorage";

declare global {
  interface Window {
    gapi?: {
      auth2?: {
        getAuthInstance(): {
          signOut(): Promise<void>;
        };
      };
    };
  }
}

const IncomingMaintenancePopup = ({ startDate }: { startDate: Date }) => {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const popoverId = React.useId();

  /*
   * On the non-react parts of the product, the app bar is rendered inside of a
   * shadow root to prevent the styles of the app bar from leaking into the
   * rest of the page. It is important that the popover is rendered inside of
   * the shadow root so that it is styled correctly. To do this, we pass a
   * reference to this wrapper div.
   */
  const ref = React.useRef<HTMLDivElement>(null);

  return (
    <div ref={ref}>
      <IconButton
        onClick={(e) => {
          setAnchorEl(e.currentTarget);
        }}
        aria-label={`A scheduled maintenance window begins ${getRelativeTime(
          startDate
        )}.`}
        aria-controls={popoverId}
        aria-haspopup="dialog"
        color="error"
      >
        <MaintenanceIcon sx={{ color: "inherit !important" }} />
      </IconButton>
      <Popover
        container={ref.current}
        id={popoverId}
        open={Boolean(anchorEl)}
        role="dialog"
        anchorEl={anchorEl}
        onClose={() => {
          setAnchorEl(null);
        }}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
      >
        <Typography sx={{ p: 2 }}>
          {/*
           * We show a relative time here rather than an absolute time to avoid
           * the need to take into account the user's timezone.
           */}
          A scheduled maintenance window begins {getRelativeTime(startDate)}.
        </Typography>
      </Popover>
    </div>
  );
};

const StyledAvatar: React.FC<
  { size?: "small" } & React.ComponentProps<typeof Avatar>
> = styled(Avatar)(({ size }: { size?: "small" }) => ({
  /*
   * This pink colour is taken from the RSpace logo, a
   * colour that we don't otherwise use in the product. The
   * pair of colours do not quite have sufficient contrast
   * to meet the WCAG standard, but this is only for
   * aesthetic purposes.
   */
  color: "#fce8f0",
  backgroundColor: "#ed1064",

  ...(size === "small"
    ? {
        height: "24px",
        width: "24px",
        fontSize: "0.8em",
      }
    : {}),
}));

/**
 * This image is used to indicate the current user
 * - If the user has uploaded a profile image then it is shown
 * - Otherwise a magenta circle with their first initial is shown
 * - If they're operating as another user then a red icon mark is
 *   shown
 */
const DynamicAvatar = ({
  uiNavigationData,
  size,
}: {
  uiNavigationData: FetchingData.Fetched<UiNavigationData>;
  size?: "small";
}) => {
  return FetchingData.match(uiNavigationData, {
    loading: () => (
      <StyledAvatar size={size}>
        <CircularProgress size="16px" />
      </StyledAvatar>
    ),
    error: () => <StyledAvatar size={size}>!</StyledAvatar>,
    success: ({ operatedAs, userDetails: { profileImgSrc, fullName } }) => {
      if (operatedAs) {
        if (size === "small")
          return <OperateAsIcon sx={{ color: "red !important" }} />;
        return (
          <Avatar sx={{ backgroundColor: "white", color: "red" }}>
            <OperateAsIcon sx={{ width: "2em", height: "2em" }} />
          </Avatar>
        );
      }
      return (
        <StyledAvatar
          size={size}
          {...(profileImgSrc !== null ? { src: profileImgSrc } : {})}
        >
          {fullName[0]}
        </StyledAvatar>
      );
    },
  });
};

const OrcidIcon = styled(({ className }: { className?: string }) => (
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

type GalleryAppBarArgs = {
  /**
   * The app bar is used across the top of the pages that consistitute most of
   * the product, as wel as several dialogs. The behaviour of this component is
   * slightly different based on this, providing navigation only when `variant`
   * is "page".
   */
  variant: "page" | "dialog";

  /**
   * The app bar is used across the product on whole pages and some dialogs. If
   * the variant is "page" then we provide a series of links for jumping
   * between the main parts of the product and if the `currentPage` is one of
   * them then it is shown as active (e.g. "Gallery", "Inventory",
   * "Workspace"). If the variant is "dialog" then `currentPage` is just used
   * to show a heading.
   */
  currentPage: string;

  /**
   * Some pages have a sidebar that needs a toggle for opening and closing.
   * Those pages should pass an instance of SidebarToggle here.
   */
  sidebarToggle?: React.ReactElement<
    React.ComponentProps<typeof SidebarToggle>
  >;

  /**
   * Which accessibility tips to show based on the conformance the current
   * page/dialog has with the accessibility standard.
   * @see {@link module:AccessibilityTips} for more info.
   */
  accessibilityTips: {
    supportsReducedMotion?: boolean;
    supportsHighContrastMode?: boolean;
    supports2xZoom?: boolean;
  };

  /*
   * A page of documentation to link to in the top right corner. If one is not
   * specified then the lighthouse dialog is opened allowing the user to browse
   * the documentation.
   */
  helpPage?: {
    docLink: (typeof docLinks)[keyof typeof docLinks];
    title: string;
  };
};

// eslint-disable-next-line complexity -- yep, there's quite a lot of conditional logic here
function GalleryAppBar({
  variant,
  currentPage,
  sidebarToggle,
  accessibilityTips,
  helpPage,
}: GalleryAppBarArgs): React.ReactNode {
  const theme = useTheme();
  const { isViewportSmall } = useViewportDimensions();
  const uiNavigationData = useUiNavigationData();
  const [appMenuAnchorEl, setAppMenuAnchorEl] =
    React.useState<null | HTMLElement>(null);
  function handleAppMenuClose() {
    setAppMenuAnchorEl(null);
  }
  const [accountMenuAnchorEl, setAccountMenuAnchorEl] =
    React.useState<null | HTMLElement>(null);
  const leftClipId = React.useId();
  const rightClipId = React.useId();

  const [brandingHref, setBrandingHref] = useSessionStorage<string | null>(
    "brandingHref",
    null
  );
  React.useEffect(() => {
    FetchingData.getSuccessValue(uiNavigationData).do(({ bannerImgSrc }) => {
      setBrandingHref(bannerImgSrc);
    });
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - setBrandingHref wont meaningfully change
     */
  }, [uiNavigationData]);

  const { showInventory, showSystem, showMyLabGroups } =
    FetchingData.getSuccessValue(uiNavigationData)
      .map(({ visibleTabs: { inventory, system, myLabGroups } }) => ({
        showInventory: Boolean(inventory),
        showSystem: Boolean(system),
        showMyLabGroups: Boolean(myLabGroups),
      }))
      .orElse({
        showInventory: false,
        showSystem: false,
        showMyLabGroups: false,
      });

  return (
    <AppBar
      position="relative"
      aria-label={variant === "page" ? "page header" : "dialog header"}
    >
      <Toolbar variant="dense">
        {variant === "page" && !isViewportSmall && (
          <>
            <div
              style={{
                height: "40px",
                backgroundColor: "white",
                width: "4px",
                clipPath: `url(#${leftClipId})`,
                borderRight: "1px solid white",
                marginLeft: "4px",
              }}
            ></div>
            <svg width="0" height="0" viewBox="0 0 3.9 40">
              <defs>
                <clipPath id={leftClipId}>
                  <path
                    d="M3.9,40C1.7,40,0,38.3,0,36.1V3.9C0,1.7,1.7,0,3.9,0v40Z"
                    fill="#000000"
                  />
                </clipPath>
              </defs>
            </svg>
          </>
        )}
        <Box
          sx={{
            display: "flex",
            backgroundColor:
              variant === "page" && !isViewportSmall ? "white" : "unset",
            height: "40px",
            px: 0.5,
            gap: 0.5,
          }}
        >
          {sidebarToggle}
          {variant === "page" && !isViewportSmall && (
            <Box height="36px" sx={{ ml: 0.5, py: 0.25, position: "relative" }}>
              {Result.fromNullable(
                brandingHref,
                new Error("branding not cached")
              )
                .orElseTry(() =>
                  FetchingData.getSuccessValue(uiNavigationData).map(
                    ({ bannerImgSrc }) => bannerImgSrc
                  )
                )
                .map((href) => (
                  <img
                    key="branding small"
                    src={href}
                    role="presentation"
                    alt="branding"
                    style={{ height: "100%" }}
                  />
                ))
                .orElse(null)}
            </Box>
          )}
        </Box>
        {variant === "page" && !isViewportSmall && (
          <>
            <div
              style={{
                height: "40px",
                backgroundColor: "white",
                width: "12px",
                clipPath: `url(#${rightClipId})`,
                borderLeft: "1px solid white",
              }}
            ></div>
            <svg width="0" height="0">
              <defs>
                <clipPath id={rightClipId}>
                  <path
                    d="M0,0c2.1,0,4.2,1.7,4.6,3.9l5.7,32.2c.4,2.1-1.1,3.9-3.2,3.9H0V0Z"
                    fill="#000000"
                  />
                </clipPath>
              </defs>
            </svg>
          </>
        )}
        {variant === "page" && (
          <VisuallyHiddenHeading variant="h1">
            {currentPage}
          </VisuallyHiddenHeading>
        )}
        {variant === "dialog" && (
          <Box sx={{ ml: 0.5 }}>
            <Typography variant="h6" noWrap component="h2">
              {currentPage}
            </Typography>
          </Box>
        )}
        {!isViewportSmall && variant === "page" && (
          <Stack
            direction="row"
            spacing={2}
            sx={{ mx: 1 }}
            component="nav"
            aria-label="main links"
          >
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
            {showInventory && (
              <Link
                target="_self"
                aria-current={currentPage === "Inventory" ? "page" : false}
                href="/inventory"
              >
                Inventory
              </Link>
            )}
            <Link
              target="_self"
              aria-current={currentPage === "My RSpace" ? "page" : false}
              href={showMyLabGroups ? "/groups/viewPIGroup" : "/userform"}
            >
              My RSpace
            </Link>
            {showSystem && (
              <Link
                target="_self"
                aria-current={currentPage === "System" ? "page" : false}
                href="/system"
              >
                System
              </Link>
            )}
          </Stack>
        )}
        {isViewportSmall && variant === "page" && (
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
                <ListItemText
                  primary={
                    [
                      "Workspace",
                      "Gallery",
                      "Inventory",
                      "System",
                      "My RSpace",
                    ].includes(currentPage)
                      ? currentPage
                      : "Go to..."
                  }
                />
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
                foregroundColor={WORKSPACE_COLOR.contrastText}
                backgroundColor={WORKSPACE_COLOR.main}
                onClick={() => {
                  window.location.href = "/workspace";
                  handleAppMenuClose();
                }}
                current={currentPage === "Workspace" ? "page" : false}
              />
              <AccentMenuItem
                title="Gallery"
                avatar={<FileIcon />}
                subheader="Your files in RSpace and connected filestores"
                foregroundColor={GALLERY_COLOR.contrastText}
                backgroundColor={GALLERY_COLOR.main}
                onClick={() => {
                  window.location.href = "/gallery";
                  handleAppMenuClose();
                }}
                current={currentPage === "Gallery" ? "page" : false}
              />
              {showInventory && (
                <AccentMenuItem
                  title="Inventory"
                  avatar={<FlaskIcon />}
                  subheader="Samples and laboratory resources"
                  foregroundColor={INVENTORY_COLOR.contrastText}
                  backgroundColor={INVENTORY_COLOR.main}
                  onClick={() => {
                    window.location.href = "/inventory";
                    handleAppMenuClose();
                  }}
                  current={currentPage === "Inventory" ? "page" : false}
                />
              )}
              <AccentMenuItem
                title="My RSpace"
                avatar={<ProfileIcon />}
                subheader="Your profile details, labgroups, and preferences"
                foregroundColor={OTHER_COLOR.contrastText}
                backgroundColor={OTHER_COLOR.main}
                onClick={() => {
                  window.location.href = showMyLabGroups
                    ? "/groups/viewPIGroup"
                    : "/userform";
                  setAccountMenuAnchorEl(null);
                }}
                current={currentPage === "My RSpace" ? "page" : false}
              />
              {showSystem && (
                <AccentMenuItem
                  title="System"
                  avatar={<SystemIcon />}
                  subheader="System administration"
                  foregroundColor={SYSADMIN_COLOR.contrastText}
                  backgroundColor={SYSADMIN_COLOR.main}
                  onClick={() => {
                    window.location.href = "/system";
                    handleAppMenuClose();
                  }}
                  current={currentPage === "System" ? "page" : false}
                />
              )}
            </Menu>
          </>
        )}
        <Box flexGrow={1}></Box>
        {FetchingData.getSuccessValue(uiNavigationData)
          .map(({ nextMaintenance }) => nextMaintenance)
          .flatMap(Parsers.isNotNull)
          .map(({ startDate }) => (
            <IncomingMaintenancePopup key="maintenance" startDate={startDate} />
          ))
          .orElse(null)}
        <Box ml={1}>
          {helpPage ? (
            <Box ml={1} sx={{ transform: "translateY(2px)" }}>
              <HelpLinkIcon title={helpPage.title} link={helpPage.docLink} />
            </Box>
          ) : (
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
          )}
        </Box>
        {variant === "page" && (
          <Box ml={1}>
            <IconButtonWithTooltip
              size="small"
              disabled={FetchingData.isLoading(uiNavigationData)}
              onClick={(event) => {
                setAccountMenuAnchorEl(event.currentTarget);
              }}
              icon={
                <DynamicAvatar
                  uiNavigationData={uiNavigationData}
                  size="small"
                />
              }
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
              {FetchingData.match(uiNavigationData, {
                loading: () => null,
                error: (errorMsg) => (
                  <ListItem>
                    <ListItemText
                      primary="Error loading your details"
                      secondary={errorMsg}
                    />
                  </ListItem>
                ),
                success: ({ userDetails }) => (
                  <ListItem key="user details" sx={{ py: 0 }}>
                    <ListItemIcon sx={{ alignSelf: "flex-start", mt: 1 }}>
                      <DynamicAvatar uiNavigationData={uiNavigationData} />
                    </ListItemIcon>
                    <Stack>
                      <ListItemText
                        sx={{ mt: 0.5 }}
                        primary={
                          <>
                            {FetchingData.getSuccessValue(uiNavigationData)
                              .map(({ operatedAs }) => operatedAs)
                              .flatMap(Parsers.isTrue)
                              .map(() => (
                                <>
                                  <strong>Operating as:</strong>
                                  <br />
                                </>
                              ))
                              .orElse(null)}
                            {userDetails.fullName} ({userDetails.username})
                          </>
                        }
                        secondary={userDetails.email}
                      />
                      {userDetails.orcidAvailable && (
                        <ListItemText
                          /*
                           * The styling of this component is dictated by the ORCID display guidelines
                           * https://info.orcid.org/documentation/integration-guide/orcid-id-display-guidelines/#Compact_ORCID_iD
                           */
                          sx={{ mt: -0.5 }}
                          primaryTypographyProps={{
                            sx: {
                              fontFamily:
                                userDetails.orcidId === null
                                  ? "inherit"
                                  : "monospace",
                              lineHeight:
                                userDetails.orcidId === null ? "unset" : "1em",
                              fontSize: "0.8em",
                              alignItems: "center",
                              textDecoration:
                                userDetails.orcidId === null
                                  ? "none"
                                  : "underline",
                            },
                          }}
                          primary={
                            userDetails.orcidId === null ? (
                              <>
                                Add an ORCID iD to your{" "}
                                <Link href="/userform">profile</Link>.
                              </>
                            ) : (
                              <Stack
                                direction="row"
                                spacing={0.5}
                                alignItems="center"
                              >
                                <SvgIcon>
                                  <OrcidIcon />
                                </SvgIcon>
                                <span>{userDetails.orcidId}</span>
                              </Stack>
                            )
                          }
                        />
                      )}
                    </Stack>
                  </ListItem>
                ),
              })}
              <AccentMenuItem
                title="Messaging"
                avatar={<MessageIcon />}
                compact
                onClick={() => {
                  setAccountMenuAnchorEl(null);
                }}
                component="a"
                href="/dashboard"
              />
              <AccentMenuItem
                title="Apps"
                avatar={<AppsIcon />}
                compact
                onClick={() => {
                  setAccountMenuAnchorEl(null);
                }}
                component="a"
                href="/apps"
              />
              {FetchingData.getSuccessValue(uiNavigationData)
                .map(({ visibleTabs: { published } }) => published)
                .flatMap(Parsers.isTrue)
                .map(() => (
                  <AccentMenuItem
                    key="published"
                    title="Published"
                    avatar={<PublicIcon />}
                    compact
                    onClick={(e) => {
                      e.preventDefault();
                      setAccountMenuAnchorEl(null);
                      window.open(
                        "/public/publishedView/publishedDocuments",
                        "_target"
                      );
                    }}
                    component="a"
                    href="/public/publishedView/publishedDocuments"
                  />
                ))
                .orElse(null)}
              <AccessibilityTipsMenuItem
                {...(accessibilityTips ?? {})}
                onClose={() => {
                  setAccountMenuAnchorEl(null);
                }}
              />
              {FetchingData.getSuccessValue(uiNavigationData)
                .map(({ operatedAs }) => operatedAs)
                .flatMap(Parsers.isTrue)
                .map(() => (
                  <AccentMenuItem
                    key="release"
                    title="Release"
                    avatar={<LogoutIcon />}
                    backgroundColor={lighten(theme.palette.error.light, 0.5)}
                    foregroundColor={darken(theme.palette.error.dark, 0.3)}
                    compact
                    onClick={() => {
                      JwtService.destroyToken();
                      setAccountMenuAnchorEl(null);
                      window.location.href = "/logout/runAsRelease";
                    }}
                  />
                ))
                .orElse(
                  <AccentMenuItem
                    title="Log Out"
                    avatar={<LogoutIcon />}
                    backgroundColor={lighten(theme.palette.error.light, 0.5)}
                    foregroundColor={darken(theme.palette.error.dark, 0.3)}
                    compact
                    onClick={() => {
                      JwtService.destroyToken();

                      /*
                       * On some servers the user can login with the Google
                       * Login workflow. On those servers, `gapi` will be
                       * defined globally by header.jsp
                       */
                      if (
                        typeof window.gapi !== "undefined" &&
                        window.gapi.auth2
                      ) {
                        const auth2 = window.gapi.auth2.getAuthInstance();
                        if (auth2) {
                          void auth2.signOut().then(() => {
                            // eslint-disable-next-line no-console
                            console.log("User signed out.");
                          });
                        } else {
                          // eslint-disable-next-line no-console
                          console.log("No GAPI authinstance defined");
                        }
                      } else {
                        // eslint-disable-next-line no-console
                        console.log("No GAPI defined");
                      }

                      setAccountMenuAnchorEl(null);
                      window.location.href = "/logout";
                    }}
                  />
                )}
              {FetchingData.getSuccessValue(uiNavigationData)
                .map(({ bannerImgSrc }) => (
                  <ListItem
                    key="branding large"
                    sx={{ py: 0, mb: 1, justifyContent: "flex-end" }}
                  >
                    <img
                      src={bannerImgSrc}
                      role="presentation"
                      alt="branding"
                      style={{ width: "min(100%, 120px)" }}
                    />
                  </ListItem>
                ))
                .orElse(null)}
            </Menu>
          </Box>
        )}
        {variant === "dialog" && (
          <Box sx={{ ml: 0.5 }}>
            <AccessibilityTipsIconButton {...(accessibilityTips ?? {})} />
          </Box>
        )}
      </Toolbar>
    </AppBar>
  );
}

/**
 * GalleryAppBar is the header bar for the gallery page.
 */
export default observer(GalleryAppBar);
