import React, { useState } from "react";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import CreateNew from "../CreateNew";
import Drawer from "@mui/material/Drawer";
import GetAppIcon from "@mui/icons-material/GetApp";
import SettingsIcon from "@mui/icons-material/Settings";
import List from "@mui/material/List";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";
import MyBenchIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/MyBench";
import ExportDialog from "../Export/ExportDialog";
import SettingsDialog from "../Settings/SettingsDialog";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import { useTheme } from "@mui/material/styles";
import useNavigateHelpers from "../../useNavigateHelpers";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import NavigateContext from "../../../stores/contexts/Navigate";
import IgsnIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/igsn";
import { useLandmark } from "../../../components/LandmarksContext";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import DrawerTab from "../../../components/DrawerTab";
import useOneDimensionalRovingTabIndex from "../../../hooks/ui/useOneDimensionalRovingTabIndex";

function isSearchListing() {
  return /inventory\/search/.test(window.location.pathname);
}

const drawerWidth = 200;

const CustomDrawer = withStyles<
  { children: React.ReactNode; drawerWidth?: number; id: string },
  {
    drawer: string;
    drawerPaper: string;
    floatingDrawerPaper: string;
    drawerOpen: string;
    drawerClose: string;
    drawerCloseParent: string;
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
            },
          ),
        }}
      >
        {children}
      </Drawer>
    );
  }),
);

const largestFittingCount = 999;

const MyBenchNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const { peopleStore, searchStore, uiStore } = useStores();
    const { navigateToSearch } = useNavigateHelpers();
    const currentUser = peopleStore.currentUser;

    return (
      <DrawerTab
        label="My Bench"
        selected={
          (isSearchListing() &&
            currentUser &&
            searchStore.search.onUsersBench(currentUser)) ??
          false
        }
        icon={<MyBenchIcon />}
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          navigateToSearch(
            currentUser
              ? { parentGlobalId: `BE${currentUser.workbenchId}` }
              : {},
          );
        }}
      />
    );
  },
);

const ContainersNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const { searchStore, uiStore } = useStores();
    const theme = useTheme();
    const benchSearch = searchStore.search.benchSearch;
    const { navigateToSearch } = useNavigateHelpers();

    return (
      <DrawerTab
        label="Containers"
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
            color={theme.palette.standardIcon.main}
          />
        }
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          navigateToSearch({
            resultType: "CONTAINER",
            deletedItems: "EXCLUDE",
          });
        }}
      />
    );
  },
);

const SampleNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const { searchStore, uiStore } = useStores();
    const theme = useTheme();
    const benchSearch = searchStore.search.benchSearch;
    const { navigateToSearch } = useNavigateHelpers();

    return (
      <DrawerTab
        label="Samples"
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
            color={theme.palette.standardIcon.main}
          />
        }
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          navigateToSearch({
            resultType: "SAMPLE",
          });
        }}
      />
    );
  },
);

const TemplateNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const { searchStore, uiStore } = useStores();
    const theme = useTheme();
    const benchSearch = searchStore.search.benchSearch;
    const { navigateToSearch } = useNavigateHelpers();

    return (
      <DrawerTab
        label="Templates"
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
            color={theme.palette.standardIcon.main}
          />
        }
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          navigateToSearch({
            resultType: "TEMPLATE",
          });
        }}
      />
    );
  },
);

const IgsnNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const { uiStore } = useStores();
    const { useNavigate } = React.useContext(NavigateContext);
    const { trackEvent } = React.useContext(AnalyticsContext);
    const navigate = useNavigate();

    return (
      <DrawerTab
        label="IGSN IDs"
        selected={/identifiers\/igsn/.test(window.location.pathname)}
        icon={<IgsnIcon style={{ width: "28px", height: "18px" }} />}
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          trackEvent("user:navigate:igsnManagementPage:InventorySidebar");
          navigate("/inventory/identifiers/igsn");
          if (uiStore.isVerySmall) uiStore.toggleSidebar(false);
        }}
      />
    );
  },
);

const SubsampleNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const { searchStore, uiStore } = useStores();
    const theme = useTheme();
    const benchSearch = searchStore.search.benchSearch;
    const { navigateToSearch } = useNavigateHelpers();

    return (
      <DrawerTab
        label="Subsamples"
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
            color={theme.palette.standardIcon.main}
          />
        }
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          navigateToSearch({
            resultType: "SUBSAMPLE",
          });
        }}
      />
    );
  },
);

const ExportNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const {
      peopleStore: { currentUser },
      uiStore,
    } = useStores();
    const { trackEvent } = React.useContext(AnalyticsContext);

    const [openExportDialog, setOpenExportDialog] = useState(false);

    return (
      <>
        <DrawerTab
          label="Export Data"
          onClick={() => {
            trackEvent("user:open:allTheirItemsExportDialog:InventorySidebar");
            setOpenExportDialog(true);
          }}
          selected={false}
          icon={<GetAppIcon />}
          index={index}
          tabIndex={tabIndex}
          ref={getRef(index)}
          drawerOpen={uiStore.sidebarOpen}
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
  },
);

const SettingsNavItem = observer(
  ({
    index,
    tabIndex,
    getRef,
  }: {
    index: number;
    tabIndex: number;
    getRef: (index: number) => React.RefObject<HTMLDivElement> | null;
  }) => {
    const { uiStore } = useStores();
    const [openSettingsDialog, setOpenSettingsDialog] = useState(false);
    return (
      <>
        <DrawerTab
          label="Settings"
          onClick={() => {
            setOpenSettingsDialog(true);
          }}
          selected={false}
          icon={<SettingsIcon />}
          index={index}
          tabIndex={tabIndex}
          ref={getRef(index)}
          drawerOpen={uiStore.sidebarOpen}
        />
        {openSettingsDialog && (
          <SettingsDialog
            open={openSettingsDialog}
            setOpen={setOpenSettingsDialog}
          />
        )}
      </>
    );
  },
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

type SidebarArgs = {
  id: string;
};

function Sidebar({ id }: SidebarArgs): React.ReactNode {
  const { classes } = useStyles();
  const { uiStore, peopleStore } = useStores();
  const isSysAdmin: boolean = Boolean(peopleStore.currentUser?.hasSysAdminRole);
  const sidebarRef = useLandmark("Navigation");

  const { getTabIndex, getRef, eventHandlers } =
    useOneDimensionalRovingTabIndex<HTMLDivElement>({
      max: isSysAdmin ? 7 : 6,
    });

  const afterClick = () => {
    if (!uiStore.alwaysVisibleSidebar) uiStore.toggleSidebar(false);
    uiStore.setVisiblePanel("right");
  };

  return (
    <CustomDrawer id={id}>
      <Box ref={sidebarRef} aria-label="Inventory Sidebar Navigation">
        <CreateNew onClick={afterClick} />
        <Box
          {...eventHandlers}
          sx={{
            overflowY: "auto",
            overflowX: "hidden",
            position: "relative",
          }}
        >
          <List
            component="nav"
            onClick={afterClick}
            aria-label="List existing Inventory items"
          >
            <MyBenchNavItem
              index={0}
              tabIndex={getTabIndex(0)}
              getRef={getRef}
            />
            <ContainersNavItem
              index={1}
              tabIndex={getTabIndex(1)}
              getRef={getRef}
            />
            <SampleNavItem
              index={2}
              tabIndex={getTabIndex(2)}
              getRef={getRef}
            />
            <SubsampleNavItem
              index={3}
              tabIndex={getTabIndex(3)}
              getRef={getRef}
            />
            <TemplateNavItem
              index={4}
              tabIndex={getTabIndex(4)}
              getRef={getRef}
            />
            <IgsnNavItem index={5} tabIndex={getTabIndex(5)} getRef={getRef} />
          </List>
          <Divider />
          <List component="nav" aria-label="Other places and action">
            <ExportNavItem
              index={6}
              tabIndex={getTabIndex(6)}
              getRef={getRef}
            />
            {isSysAdmin && (
              <SettingsNavItem
                index={7}
                tabIndex={getTabIndex(7)}
                getRef={getRef}
              />
            )}
          </List>
        </Box>
      </Box>
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
export default observer(Sidebar);
