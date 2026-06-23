import { faFileExport } from "@fortawesome/free-solid-svg-icons/faFileExport";
import { faGear } from "@fortawesome/free-solid-svg-icons/faGear";
import { faMicroscope } from "@fortawesome/free-solid-svg-icons/faMicroscope";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Drawer, { drawerClasses } from "@mui/material/Drawer";
import List from "@mui/material/List";
import { useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import React, { useState } from "react";
import { InvalidState } from "@/util/error";
import { mapNullable } from "@/util/Util";
import IgsnIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/IgsnIcon";
import MyBenchIcon from "../../../assets/graphics/RecordTypeGraphics/Icons/MyBench";
import DrawerTab from "../../../components/DrawerTab";
import { useLandmark } from "../../../components/LandmarksContext";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import useOneDimensionalRovingTabIndex from "../../../hooks/ui/useOneDimensionalRovingTabIndex";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import NavigateContext from "../../../stores/contexts/Navigate";
import useStores from "../../../stores/use-stores";
import useNavigateHelpers from "../../useNavigateHelpers";
import CreateNew from "../CreateNew";
import ExportDialog from "../Export/ExportDialog";
import SettingsDialog from "../Settings/SettingsDialog";

function isSearchListing() {
  return /inventory\/search/.test(window.location.pathname);
}

const drawerWidth = 200;

const CustomDrawer = observer(({ children, id }: { children: React.ReactNode; id: string }) => {
  const { uiStore } = useStores();
  const alwaysVisible = uiStore.alwaysVisibleSidebar;
  const isOpen = uiStore.sidebarOpen;
  return (
    <Drawer
      open={alwaysVisible || isOpen}
      variant={alwaysVisible ? "persistent" : "temporary"}
      onClose={() => uiStore.toggleSidebar(false)}
      id={id}
      sx={(theme) => ({
        width: drawerWidth,
        flexShrink: 0,
        whiteSpace: "nowrap",
        zIndex: 1100,
        ...(alwaysVisible &&
          isOpen && {
            overflow: "visible",
            transition: theme.transitions.create("width", {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.enteringScreen,
            }),
          }),
        ...(alwaysVisible &&
          !isOpen && {
            overflowX: "hidden",
            overflow: "visible",
            width: theme.spacing(5),
            transition: theme.transitions.create("width", {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.leavingScreen,
            }),
            [theme.breakpoints.up("sm")]: {
              width: theme.spacing(8),
            },
          }),
        [`& .${drawerClasses.paper}`]: {
          position: "relative",
          ...(!alwaysVisible && { width: drawerWidth }),
          ...(alwaysVisible &&
            isOpen && {
              overflow: "visible",
              width: drawerWidth,
              transition: theme.transitions.create("width", {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.enteringScreen,
              }),
            }),
          ...(alwaysVisible &&
            !isOpen && {
              overflowX: "hidden",
              overflow: "visible",
              width: theme.spacing(5),
              transition: theme.transitions.create("width", {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.leavingScreen,
              }),
              [theme.breakpoints.up("sm")]: {
                width: theme.spacing(7.75),
              },
            }),
        },
      })}
    >
      {children}
    </Drawer>
  );
});

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
    const benchContentSummary = peopleStore.currentUser?.bench?.contentSummary;
    const benchContentCount: number | null =
      mapNullable((summary) => {
        if (!summary.isAccessible)
          throw new InvalidState(
            "A user should always be able to access a summary of the contents of their own bench.",
          );
        return summary.value.totalCount;
      }, benchContentSummary) ?? null;

    return (
      <DrawerTab
        label="My Bench"
        selected={(isSearchListing() && currentUser && searchStore.search.onUsersBench(currentUser)) ?? false}
        icon={<MyBenchIcon />}
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        badge={Math.min(benchContentCount ?? 0, largestFittingCount)}
        onClick={() => {
          navigateToSearch(currentUser ? { parentGlobalId: `BE${currentUser.workbenchId}` } : {});
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
        selected={!benchSearch && isSearchListing() && searchStore.isTypeSelected("CONTAINER")}
        icon={
          <RecordTypeIcon
            record={{
              iconName: "container",
              recordTypeLabel: "",
            }}
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
        selected={!benchSearch && isSearchListing() && searchStore.isTypeSelected("SAMPLE")}
        icon={
          <RecordTypeIcon
            record={{
              iconName: "sample",
              recordTypeLabel: "",
            }}
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

const InstrumentNavItem = observer(
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
        label="Instruments"
        selected={!benchSearch && isSearchListing() && searchStore.isTypeSelected("INSTRUMENT")}
        icon={<FontAwesomeIcon icon={faMicroscope} color={theme.palette.standardIcon.main} />}
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          navigateToSearch({
            resultType: "INSTRUMENT",
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
        label={
          <>
            Sample
            <br />
            Templates
          </>
        }
        selected={!benchSearch && isSearchListing() && searchStore.isTypeSelected("TEMPLATE")}
        icon={
          <RecordTypeIcon
            record={{
              iconName: "template",
              recordTypeLabel: "",
            }}
            style={{ width: "16px", height: "16px" }}
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

const InstrumentTemplateNavItem = observer(
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
        label={
          <>
            Instrument
            <br />
            Templates
          </>
        }
        selected={!benchSearch && isSearchListing() && searchStore.isTypeSelected("INSTRUMENT_TEMPLATE")}
        icon={
          <RecordTypeIcon
            record={{
              iconName: "instrumentTemplate",
              recordTypeLabel: "",
            }}
            style={{ width: "16px", height: "16px" }}
            color={theme.palette.standardIcon.main}
          />
        }
        index={index}
        tabIndex={tabIndex}
        ref={getRef(index)}
        drawerOpen={uiStore.sidebarOpen}
        onClick={() => {
          navigateToSearch({
            resultType: "INSTRUMENT_TEMPLATE",
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
        icon={<IgsnIcon sx={{ width: "16px", height: "16px" }} />}
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
        selected={!benchSearch && isSearchListing() && searchStore.isTypeSelected("SUBSAMPLE")}
        icon={
          <RecordTypeIcon
            record={{
              iconName: "subsample",
              recordTypeLabel: "",
            }}
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
          icon={<FontAwesomeIcon icon={faFileExport} />}
          index={index}
          tabIndex={tabIndex}
          ref={getRef(index)}
          drawerOpen={uiStore.sidebarOpen}
        />
        {openExportDialog && (
          <ExportDialog
            openExportDialog={openExportDialog}
            setOpenExportDialog={setOpenExportDialog}
            onExport={(exportOptions) => currentUser?.exportData(exportOptions, currentUser.username)}
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
          icon={<FontAwesomeIcon icon={faGear} />}
          index={index}
          tabIndex={tabIndex}
          ref={getRef(index)}
          drawerOpen={uiStore.sidebarOpen}
        />
        {openSettingsDialog && <SettingsDialog open={openSettingsDialog} setOpen={setOpenSettingsDialog} />}
      </>
    );
  },
);

type SidebarArgs = {
  id: string;
};

function Sidebar({ id }: SidebarArgs): React.ReactNode {
  const { uiStore, peopleStore } = useStores();
  const isSysAdmin: boolean = Boolean(peopleStore.currentUser?.hasSysAdminRole);
  const sidebarRef = useLandmark("Navigation");

  const { getTabIndex, getRef, eventHandlers } = useOneDimensionalRovingTabIndex<HTMLDivElement>({
    max: isSysAdmin ? 9 : 8,
  });

  const afterClick = () => {
    if (!uiStore.alwaysVisibleSidebar) uiStore.toggleSidebar(false);
    uiStore.setVisiblePanel("right");
  };

  return (
    <CustomDrawer id={id}>
      <Box ref={sidebarRef} aria-label="Inventory Sidebar Navigation">
        <CreateNew onClick={afterClick} />
        <Divider />
        <Box
          {...eventHandlers}
          sx={{
            overflowY: "auto",
            overflowX: "hidden",
            position: "relative",
          }}
        >
          <List component="ul" onClick={afterClick} aria-label="List existing Inventory items">
            <MyBenchNavItem index={0} tabIndex={getTabIndex(0)} getRef={getRef} />
            <ContainersNavItem index={1} tabIndex={getTabIndex(1)} getRef={getRef} />
            <SampleNavItem index={2} tabIndex={getTabIndex(2)} getRef={getRef} />
            <SubsampleNavItem index={3} tabIndex={getTabIndex(3)} getRef={getRef} />
            <InstrumentNavItem index={4} tabIndex={getTabIndex(4)} getRef={getRef} />
            <TemplateNavItem index={5} tabIndex={getTabIndex(5)} getRef={getRef} />
            <InstrumentTemplateNavItem index={6} tabIndex={getTabIndex(6)} getRef={getRef} />
            <IgsnNavItem index={7} tabIndex={getTabIndex(7)} getRef={getRef} />
          </List>
          <Divider />
          <List component="ul" aria-label="Other places and action">
            <ExportNavItem index={8} tabIndex={getTabIndex(8)} getRef={getRef} />
            {isSysAdmin && <SettingsNavItem index={9} tabIndex={getTabIndex(9)} getRef={getRef} />}
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
