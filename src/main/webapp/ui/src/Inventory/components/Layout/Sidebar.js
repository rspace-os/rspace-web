// @flow

import React, {
  type Node,
  type ComponentType,
  useState,
  type ElementProps,
} from "react";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CreateNew from "../CreateNew";
import Drawer from "@mui/material/Drawer";
import ExitToAppIcon from "@mui/icons-material/ExitToApp";
import GetAppIcon from "@mui/icons-material/GetApp";
import SettingsIcon from "@mui/icons-material/Settings";
import HelpOutlineOutlinedIcon from "@mui/icons-material/HelpOutlineOutlined";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import SidebarActivator from "./SidebarActivator";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";
import Badge from "@mui/material/Badge";
import Typography from "@mui/material/Typography";
import MyBenchIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/MyBench";
import { isImportPage } from "../../../stores/stores/ImportStore";
import ExportDialog from "../../components/Export/ExportDialog";
import SettingsDialog from "../Settings/SettingsDialog";
import { mapNullable } from "../../../util/Util";
import { InvalidState } from "../../../util/error";
import Box from "@mui/material/Box";
import { type Theme } from "../../../theme";
import HelpDocs from "../../../components/Help/HelpDocs";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import { useTheme, ThemeProvider } from "@mui/material/styles";
import useNavigateHelpers from "../../useNavigateHelpers";
import createAccentedTheme from "../../../accentedTheme";
import Dialog from "@mui/material/Dialog";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import AccessibilityTips from "../../../components/AccessibilityTips";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";

export const FIELDMARK_COLOR = {
  main: {
    hue: 82,
    saturation: 80,
    lightness: 33,
  },
  darker: {
    hue: 82,
    saturation: 80,
    lightness: 22,
  },
  contrastText: {
    hue: 82,
    saturation: 80,
    lightness: 19,
  },
  background: {
    hue: 82,
    saturation: 46,
    lightness: 66,
  },
  backgroundContrastText: {
    hue: 82,
    saturation: 70,
    lightness: 22,
  },
};

const COLOR = {
  main: {
    hue: 197,
    saturation: 50,
    lightness: 80,
  },
  darker: {
    hue: 197,
    saturation: 100,
    lightness: 30,
  },
  contrastText: {
    hue: 200,
    saturation: 30,
    lightness: 36,
  },
  background: {
    hue: 200,
    saturation: 20,
    lightness: 82,
  },
  backgroundContrastText: {
    hue: 203,
    saturation: 17,
    lightness: 35,
  },
};

const drawerWidth = 200;

const CustomDrawer = withStyles<
  {| children: Node, drawerWidth?: number |},
  {
    drawer: string,
    drawerPaper: string,
    drawerOpen: string,
    drawerClose: string,
    drawerCloseParent: string,
  }
>((theme) => ({
  drawer: {
    width: drawerWidth,
    flexShrink: 0,
    whiteSpace: "nowrap",
    zIndex: 1100, // less than the Gallery picker, which is a dialog
  },
  drawerPaper: {
    width: drawerWidth,
  },
  drawerOpen: {
    overflow: "visible",
    width: drawerWidth,
    backgroundColor: "#fafafa !important",
    borderRight: "0px !important",
    transition: theme.transitions.create("width", {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.enteringScreen,
    }),
  },
  drawerClose: {
    transition: theme.transitions.create("width", {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.leavingScreen,
    }),
    overflowX: "hidden",
    backgroundColor: `${theme.palette.background.default} !important`,
    borderRight: "0px !important",
    width: theme.spacing(5),
    [theme.breakpoints.up("sm")]: {
      width: theme.spacing(7.75),
    },
    overflow: "visible",
  },
  drawerCloseParent: {
    [theme.breakpoints.up("sm")]: {
      width: theme.spacing(8),
    },
  },
}))(
  observer(({ classes, children }) => {
    const { uiStore } = useStores();

    return (
      <Drawer
        open={uiStore.alwaysVisibleSidebar || uiStore.sidebarOpen}
        variant={uiStore.alwaysVisibleSidebar ? "persistent" : "temporary"}
        onClose={() => uiStore.toggleSidebar(false)}
        className={
          !uiStore.alwaysVisibleSidebar
            ? classes.drawer
            : clsx(classes.drawer, {
                [classes.drawerOpen]: uiStore.sidebarOpen,
                [classes.drawerClose]: !uiStore.sidebarOpen,
                [classes.drawerCloseParent]: !uiStore.sidebarOpen,
              })
        }
        classes={
          !uiStore.alwaysVisibleSidebar
            ? { paper: classes.drawerPaper }
            : {
                paper: clsx({
                  [classes.drawerOpen]: uiStore.sidebarOpen,
                  [classes.drawerClose]: !uiStore.sidebarOpen,
                }),
              }
        }
      >
        {children}
      </Drawer>
    );
  })
);

const NavButtonBadge = withStyles<
  {| sidebarOpen?: boolean, selected: boolean, ...ElementProps<typeof Badge> |},
  { root: string, badge: string }
>((theme, { sidebarOpen, selected }) => ({
  root: {
    alignItems: "center",
    width: "100%",
  },
  badge: {
    transform: sidebarOpen
      ? "translate(0px, 6px) scale(1)"
      : "translate(calc(50% + 2px), calc(-50% + 2px))",
    backgroundColor: selected
      ? theme.palette.sidebar.selected.badge
      : theme.palette.sidebar.selected.bg,
    color: theme.palette.standardIcon.main,
    borderTop:
      !sidebarOpen && selected
        ? `2px solid ${theme.palette.sidebar.selected.bg}`
        : "none",
    borderRight:
      !sidebarOpen && selected
        ? `2px solid ${theme.palette.sidebar.selected.bg}`
        : "none",
    top: 0,
    right: sidebarOpen ? 0 : 3,
  },
}))((props) => {
  const rest = { ...props };
  delete rest.sidebarOpen;
  return <Badge {...rest} />;
});

const NavItem = withStyles<
  {|
    label: string,
    datatestid: string,
    badge: Node,
    icon: Node,
    ...ElementProps<typeof ListItem>,
  |},
  { button: string, listIcon: string }
>((theme: Theme) => ({
  button: {
    paddingLeft: theme.spacing(2.5),
    borderTopRightRadius: theme.spacing(3),
    borderBottomRightRadius: theme.spacing(3),
    cursor: "pointer",
  },
  listIcon: {
    minWidth: theme.spacing(6.5),
  },
}))(
  observer(({ classes, icon, datatestid, badge, label, ...args }) => {
    const { uiStore } = useStores();
    return (
      <ListItemButton
        {...args}
        component="a"
        className={classes.button}
        data-test-id={datatestid}
      >
        <NavButtonBadge
          badgeContent={badge}
          color="primary"
          selected={args.selected}
          max={999}
          sidebarOpen={uiStore.sidebarOpen}
        >
          <ListItemIcon className={classes.listIcon}>{icon}</ListItemIcon>
          <ListItemText primary={label} />
        </NavButtonBadge>
      </ListItemButton>
    );
  })
);

const largestFittingCount = 999;

const MyBenchNavItem = observer(() => {
  const { peopleStore, searchStore } = useStores();
  const { navigateToSearch } = useNavigateHelpers();
  const currentUser = peopleStore.currentUser;
  const benchContentSummary = peopleStore.currentUser?.bench?.contentSummary;
  const benchContentCount: ?number = mapNullable((summary) => {
    if (!summary.isAccessible)
      throw new InvalidState(
        "A user should always be able to access a summary of the contents of their own bench."
      );
    return summary.value.totalCount;
  }, benchContentSummary);

  return (
    <NavItem
      label="My Bench"
      datatestid="MyBenchNavFilter"
      selected={currentUser && searchStore.search.onUsersBench(currentUser)}
      icon={<MyBenchIcon />}
      disabled={!currentUser}
      badge={Math.min(benchContentCount ?? 0, largestFittingCount)}
      onClick={(e: Event) => {
        e.stopPropagation();
        navigateToSearch(
          currentUser ? { parentGlobalId: `BE${currentUser.workbenchId}` } : {}
        );
      }}
    />
  );
});

const ContainersNavItem = observer(() => {
  const { searchStore } = useStores();
  const theme = useTheme();
  const benchSearch = searchStore.search.benchSearch;
  const { navigateToSearch } = useNavigateHelpers();

  return (
    <NavItem
      label="Containers"
      datatestid="ContainersNavFilter"
      selected={
        !benchSearch &&
        !isImportPage() &&
        searchStore.isTypeSelected("CONTAINER")
      }
      icon={
        <RecordTypeIcon
          record={{
            iconName: "container",
            recordTypeLabel: "",
          }}
          style={{ width: "24px" }}
          color={theme.palette.standardIcon}
        />
      }
      badge={0}
      onClick={(e: Event) => {
        e.stopPropagation();
        navigateToSearch({
          resultType: "CONTAINER",
          deletedItems: "EXCLUDE",
        });
      }}
    />
  );
});

const SampleNavItem = observer(() => {
  const { searchStore } = useStores();
  const theme = useTheme();
  const benchSearch = searchStore.search.benchSearch;
  const { navigateToSearch } = useNavigateHelpers();

  return (
    <NavItem
      label="Samples"
      datatestid="SamplesNavFilter"
      selected={
        !benchSearch && !isImportPage() && searchStore.isTypeSelected("SAMPLE")
      }
      icon={
        <RecordTypeIcon
          record={{
            iconName: "sample",
            recordTypeLabel: "",
          }}
          style={{ width: "24px" }}
          color={theme.palette.standardIcon}
        />
      }
      badge={0}
      onClick={(e: Event) => {
        e.stopPropagation();
        navigateToSearch({
          resultType: "SAMPLE",
        });
      }}
    />
  );
});

const TemplateNavItem = observer(() => {
  const { searchStore } = useStores();
  const theme = useTheme();
  const benchSearch = searchStore.search.benchSearch;
  const { navigateToSearch } = useNavigateHelpers();

  return (
    <NavItem
      label="Templates"
      datatestid="TemplatesNavFilter"
      selected={
        !benchSearch &&
        !isImportPage() &&
        searchStore.isTypeSelected("TEMPLATE")
      }
      icon={
        <RecordTypeIcon
          record={{
            iconName: "template",
            recordTypeLabel: "",
          }}
          style={{ width: "28px", height: "18px" }}
          color={theme.palette.standardIcon}
        />
      }
      badge={0}
      onClick={(e: Event) => {
        e.stopPropagation();
        navigateToSearch({
          resultType: "TEMPLATE",
        });
      }}
    />
  );
});

const SubsampleNavItem = observer(() => {
  const { searchStore } = useStores();
  const theme = useTheme();
  const benchSearch = searchStore.search.benchSearch;
  const { navigateToSearch } = useNavigateHelpers();

  return (
    <NavItem
      label="Subsamples"
      datatestid="SubsamplesNavFilter"
      selected={
        !benchSearch &&
        !isImportPage() &&
        searchStore.isTypeSelected("SUBSAMPLE")
      }
      icon={
        <RecordTypeIcon
          record={{
            iconName: "subsample",
            recordTypeLabel: "",
          }}
          style={{ width: "24px" }}
          color={theme.palette.standardIcon}
        />
      }
      badge={0}
      onClick={(e: Event) => {
        e.stopPropagation();
        navigateToSearch({
          resultType: "SUBSAMPLE",
        });
      }}
    />
  );
});

const ExportNavItem = observer(() => {
  const {
    peopleStore: { currentUser },
  } = useStores();

  const [openExportDialog, setOpenExportDialog] = useState(false);

  return (
    <>
      <NavItem
        label="Export Data"
        datatestid="ExportUserDataFilter"
        onClick={() => {
          setOpenExportDialog(true);
        }}
        selected={false}
        icon={<GetAppIcon />}
        badge={0}
      />
      {openExportDialog && (
        <ExportDialog
          openExportDialog={openExportDialog}
          setOpenExportDialog={setOpenExportDialog}
          onExport={(exportOptions) =>
            currentUser?.exportData(exportOptions, currentUser.username)
          }
          exportType="userData"
        />
      )}
    </>
  );
});

const ELNNavItem = observer(() => {
  const { uiStore } = useStores();

  return (
    <NavItem
      label="RSpace ELN"
      datatestid="ELNNavFilter"
      onClick={() => {
        void uiStore.confirmDiscardAnyChanges().then((canNav) => {
          if (canNav) {
            window.location.href = "/workspace";
          }
        });
      }}
      selected={false}
      icon={<ArrowBackIcon />}
      badge={0}
    />
  );
});

const SettingsNavItem = observer(() => {
  const [openSettingsDialog, setOpenSettingsDialog] = useState(false);
  return (
    <>
      <NavItem
        label="Settings"
        datatestid="SettingsNavFilter"
        onClick={() => {
          setOpenSettingsDialog(true);
        }}
        selected={false}
        icon={<SettingsIcon />}
        badge={0}
      />
      {openSettingsDialog && (
        <SettingsDialog
          open={openSettingsDialog}
          setOpen={setOpenSettingsDialog}
        />
      )}
    </>
  );
});

const NeedHelpNavItem = observer(() => {
  return (
    <HelpDocs
      Action={({ onClick, disabled }) => (
        <NavItem
          onClick={onClick}
          disabled={disabled}
          label="Need Help?"
          datatestid="HelpNavFilter"
          selected={false}
          icon={<HelpOutlineOutlinedIcon />}
          badge={0}
        />
      )}
    />
  );
});

const SignOutNavItem = observer(() => {
  const { authStore, uiStore } = useStores();

  return (
    <NavItem
      label="Sign Out"
      datatestid="SignOutNavFilter"
      onClick={() => {
        void uiStore
          .confirmDiscardAnyChanges()
          .then((canSignOut) => {
            if (canSignOut) {
              authStore.signOut();
            }
          })
          .then(() => {
            window.location.href = "/logout";
          });
      }}
      selected={false}
      icon={<ExitToAppIcon />}
      badge={0}
    />
  );
});

const LoggedInLabel = withStyles<{||}, { username: string }>((theme) => ({
  username: {
    whiteSpace: "normal",
    color: theme.palette.standardIcon.main,
  },
}))(
  observer(({ classes }) => {
    const { uiStore, peopleStore } = useStores();
    const user = peopleStore.currentUser;

    return uiStore.sidebarOpen ? (
      <Box px={2} py={0.5}>
        <Typography variant="overline">
          {user?.isOperated ? "Operating" : "Logged in"} as
        </Typography>
        <br />
        <Typography variant="body2" className={classes.username}>
          {user?.label ?? "Loading...."}
        </Typography>
      </Box>
    ) : null;
  })
);

const useStyles = makeStyles()(() => ({
  drawerContainer: {
    overflowY: "auto",
    overflowX: "hidden",
    "& a.active": {
      backgroundColor: "transparent",
      color: "#00adef",
      fontWeight: "bold !important",
      "& svg": {
        color: "#00adef",
      },
    },
  },
}));

function Sidebar(): Node {
  const { classes } = useStyles();
  const { uiStore, peopleStore } = useStores();
  const isSysAdmin: boolean = Boolean(peopleStore.currentUser?.hasSysAdminRole);

  const afterClick = () => {
    if (!uiStore.alwaysVisibleSidebar) uiStore.toggleSidebar(false);
    uiStore.setVisiblePanel("right");
  };

  return (
    <CustomDrawer>
      <SidebarActivator />
      <div className={classes.drawerContainer}>
        <List component="nav" aria-label="Create new Inventory items">
          <ThemeProvider theme={createAccentedTheme(COLOR)}>
            <CreateNew onClick={afterClick} />
          </ThemeProvider>
        </List>
        <List
          component="nav"
          onClick={afterClick}
          aria-label="List existing Inventory items"
        >
          <MyBenchNavItem />
          <ContainersNavItem />
          <SampleNavItem />
          <SubsampleNavItem />
          <TemplateNavItem />
        </List>
        <List component="nav" aria-label="Other places and action">
          <ExportNavItem />
          <ELNNavItem />
          {isSysAdmin && <SettingsNavItem />}
          <NeedHelpNavItem />
          <SignOutNavItem />
        </List>
        <LoggedInLabel />
      </div>
      <ThemeProvider theme={createAccentedTheme(FIELDMARK_COLOR)}>
        <Dialog open>
          <AppBar position="relative" open={true}>
            <Toolbar variant="dense">
              <Typography variant="h6" noWrap component="h2">
                Fieldmark
              </Typography>
              <Box flexGrow={1}></Box>
              <Box ml={1}>
                <AccessibilityTips
                  supportsHighContrastMode
                  elementType="dialog"
                />
              </Box>
              <Box ml={1} sx={{ transform: "translateY(2px)" }}>
                <HelpLinkIcon title="Fieldmark help" link="#" />
              </Box>
            </Toolbar>
          </AppBar>
          <Box sx={{ display: "flex", minHeight: 0 }}>
            <DialogContent>
              <Grid
                container
                direction="column"
                spacing={2}
                sx={{ height: "100%", flexWrap: "nowrap" }}
              >
                <Grid item>
                  <Typography variant="h3">Import from Fieldmark</Typography>
                </Grid>
                <Grid item>
                  <Typography variant="body2">
                    Choose a Fieldmark notebook to import into Inventory. The
                    new list container will be placed on your bench.
                  </Typography>
                  <Typography variant="body2">
                    See <Link href="#">docs.fieldmark.au</Link> and our{" "}
                    <Link href={"#"}>Fieldmark integration docs</Link> for more.
                  </Typography>
                </Grid>
              </Grid>
            </DialogContent>
          </Box>
        </Dialog>
      </ThemeProvider>
    </CustomDrawer>
  );
}

export default (observer(Sidebar): ComponentType<{||}>);
