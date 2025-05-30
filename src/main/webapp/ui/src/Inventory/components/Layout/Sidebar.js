// @flow

import React, {
  type Node,
  type ComponentType,
  useState,
  type ElementProps,
} from "react";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import CreateNew from "../CreateNew";
import Drawer from "@mui/material/Drawer";
import GetAppIcon from "@mui/icons-material/GetApp";
import SettingsIcon from "@mui/icons-material/Settings";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";
import Badge from "@mui/material/Badge";
import MyBenchIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/MyBench";
import ExportDialog from "../../components/Export/ExportDialog";
import SettingsDialog from "../Settings/SettingsDialog";
import { mapNullable } from "../../../util/Util";
import { InvalidState } from "../../../util/error";
import { type Theme } from "../../../theme";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import { useTheme, ThemeProvider } from "@mui/material/styles";
import useNavigateHelpers from "../../useNavigateHelpers";
import createAccentedTheme from "../../../accentedTheme";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";
import NavigateContext from "../../../stores/contexts/Navigate";
import IgsnIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/igsn";

function isSearchListing() {
  return /inventory\/search/.test(window.location.pathname);
}

const drawerWidth = 200;

const CustomDrawer = withStyles<
  {| children: Node, drawerWidth?: number, id: string |},
  {
    drawer: string,
    drawerPaper: string,
    floatingDrawerPaper: string,
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
    /*
     * We set this position so that the drawer does not float above the AppBar.
     */
    position: "relative",
  },
  floatingDrawerPaper: {
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
  observer(({ classes, children, id }) => {
    const { uiStore } = useStores();

    return (
      <Drawer
        open={uiStore.alwaysVisibleSidebar || uiStore.sidebarOpen}
        variant={uiStore.alwaysVisibleSidebar ? "persistent" : "temporary"}
        onClose={() => uiStore.toggleSidebar(false)}
        id={id}
        className={
          !uiStore.alwaysVisibleSidebar
            ? classes.drawer
            : clsx(classes.drawer, {
                [classes.drawerOpen]: uiStore.sidebarOpen,
                [classes.drawerClose]: !uiStore.sidebarOpen,
                [classes.drawerCloseParent]: !uiStore.sidebarOpen,
              })
        }
        classes={{
          paper: clsx(
            classes.drawerPaper,
            !uiStore.alwaysVisibleSidebar && classes.floatingDrawerPaper,
            uiStore.alwaysVisibleSidebar && {
              [classes.drawerOpen]: uiStore.sidebarOpen,
              [classes.drawerClose]: !uiStore.sidebarOpen,
            }
          ),
        }}
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
      selected={
        isSearchListing() &&
        currentUser &&
        searchStore.search.onUsersBench(currentUser)
      }
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
        isSearchListing() &&
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
        !benchSearch &&
        isSearchListing() &&
        searchStore.isTypeSelected("SAMPLE")
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
        isSearchListing() &&
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

// eslint-disable-next-line no-unused-vars, @typescript-eslint/no-unused-vars
const IgsnNavItem = observer(() => {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

  return (
    <NavItem
      label="IGSN IDs"
      selected={/identifiers\/igsn/.test(window.location.pathname)}
      icon={<IgsnIcon style={{ width: "28px", height: "18px" }} />}
      badge={0}
      onClick={(e: Event) => {
        e.stopPropagation();
        navigate("/inventory/identifiers/igsn");
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
        isSearchListing() &&
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
  const { trackEvent } = React.useContext(AnalyticsContext);

  const [openExportDialog, setOpenExportDialog] = useState(false);

  return (
    <>
      <NavItem
        label="Export Data"
        datatestid="ExportUserDataFilter"
        onClick={() => {
          trackEvent("user:open:allTheirItemsExportDialog:InventorySidebar");
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

type SidebarArgs = {|
  id: string,
|};

function Sidebar({ id }: SidebarArgs): Node {
  const { classes } = useStyles();
  const { uiStore, peopleStore } = useStores();
  const isSysAdmin: boolean = Boolean(peopleStore.currentUser?.hasSysAdminRole);

  const afterClick = () => {
    if (!uiStore.alwaysVisibleSidebar) uiStore.toggleSidebar(false);
    uiStore.setVisiblePanel("right");
  };

  return (
    <CustomDrawer id={id}>
      <div className={classes.drawerContainer}>
        <List component="nav" aria-label="Create new Inventory items">
          <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
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
          <IgsnNavItem />
        </List>
        <List component="nav" aria-label="Other places and action">
          <ExportNavItem />
          {isSysAdmin && <SettingsNavItem />}
        </List>
      </div>
    </CustomDrawer>
  );
}

/**
 * The Inventory sidebar that provides rapid access the different types of
 * records as well as the menu for creating new records. On larger viewpors it
 * is always avaiable, although can be shrunk to hide the labels and provide
 * more space for the main content, and on small viewports it is hidden behind
 * a hamburger menu.
 */
export default (observer(Sidebar): ComponentType<SidebarArgs>);
