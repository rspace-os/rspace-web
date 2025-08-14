import React from "react";
import { useLandmark } from "../../../components/LandmarksContext";
import Box from "@mui/material/Box";
import { Drawer, Menu } from "../../../components/DialogBoundary";
import { styled } from "@mui/material/styles";
import {
  gallerySectionLabel,
  gallerySectionIcon,
  type GallerySection,
} from "../common";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import { darken } from "@mui/system";
import Divider from "@mui/material/Divider";
import Button from "@mui/material/Button";
import CreateNewFolderIcon from "@mui/icons-material/CreateNewFolder";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import AddIcon from "@mui/icons-material/Add";
import ArgosAccentMenuItem from "../../../eln-dmp-integration/Argos/ArgosAccentMenuItem";
import DMPOnlineAccentMenuItem from "../../../eln-dmp-integration/DMPOnline/DMPOnlineAccentMenuItem";
import DMPToolAccentMenuItem from "../../../eln-dmp-integration/DMPTool/DMPToolAccentMenuItem";
import AccentMenuItem from "../../../components/AccentMenuItem";
import { type Id } from "../useGalleryListing";
import { useGalleryActions } from "../useGalleryActions";
import * as FetchingData from "../../../util/fetchingData";
import Dialog from "@mui/material/Dialog";
import TextField from "@mui/material/TextField";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogActions from "@mui/material/DialogActions";
import { useIntegrationIsAllowedAndEnabled } from "../../../common/integrationHelpers";
import useOneDimensionalRovingTabIndex from "../../../components/useOneDimensionalRovingTabIndex";
import useViewportDimensions from "../../../hooks/browser/useViewportDimensions";
import { observer } from "mobx-react-lite";
import { autorun } from "mobx";
import EventBoundary from "../../../components/EventBoundary";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
} from "../../../components/ValidatingSubmitButton";
import Result from "../../../util/result";
import DnsIcon from "@mui/icons-material/Dns";
import axios from "@/common/axios";
import useOauthToken from "../../../hooks/api/useOauthToken";
import * as Parsers from "../../../util/parsers";
import { useDeploymentProperty } from "../../../hooks/api/useDeploymentProperty";
import AddFilestoreDialog from "./AddFilestoreDialog";
import AnalyticsContext from "../../../stores/contexts/Analytics";

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(-4px, 4px) !important",
        }
      : {}),
  },
}));

const AddButton = styled(
  ({
    drawerOpen,
    ...props
  }: { drawerOpen: boolean } & React.ComponentProps<typeof Button>) => (
    <Button
      {...props}
      fullWidth
      style={{ minWidth: "unset" }}
      aria-haspopup="menu"
      variant="contained"
      color="callToAction"
      startIcon={
        <AddIcon
          style={{
            marginLeft: drawerOpen ? "0px" : "11px",
          }}
        />
      }
    >
      {drawerOpen && <div>Create</div>}
    </Button>
  ),
)(() => ({
  overflowX: "hidden",
  height: "32px",
}));

const CustomDrawer = styled(Drawer)(({ open }) => ({
  // on small viewports, it will hidden entirely when not open
  width: open ? "200px" : "64px",
  // drawer should float over dialog in Inventory
  zIndex: 1300,
  "& .MuiPaper-root": {
    /*
     * We set this position so that the drawer does not float above the AppBar
     * and so that the active tab indicator can slide up and down relative to
     * this bounding box.
     */
    position: "relative",
  },
}));

const UploadMenuItem = ({
  folderId,
  onUploadComplete,
  onCancel,
  autoFocus,
  tabIndex,
}: {
  folderId: Result<Id>;
  onUploadComplete: () => void;
  onCancel: () => void;

  /*
   * These properties are dynamically added by the MUI Menu parent component
   */
  autoFocus?: boolean;
  tabIndex?: number;
}) => {
  const { uploadFiles } = useGalleryActions();
  const inputRef = React.useRef<HTMLInputElement | null>(null);
  const { trackEvent } = React.useContext(AnalyticsContext);

  /*
   * This is necessary because React does not yet support the new cancel event
   * https://developer.mozilla.org/en-US/docs/Web/API/HTMLElement/cancel_event
   * https://github.com/facebook/react/issues/27858
   */
  React.useEffect(() => {
    const input = inputRef.current;
    input?.addEventListener("cancel", onCancel);
    return () => input?.removeEventListener("cancel", onCancel);
  }, [inputRef, onCancel]);

  return (
    <>
      <AccentMenuItem
        title="Upload Files"
        avatar={<UploadFileIcon />}
        backgroundColor={ACCENT_COLOR.background}
        foregroundColor={ACCENT_COLOR.contrastText}
        onKeyDown={(e: React.KeyboardEvent<HTMLButtonElement>) => {
          if (e.key === " ") inputRef.current?.click();
        }}
        onClick={() => {
          inputRef.current?.click();
        }}
        //eslint-disable-next-line jsx-a11y/no-autofocus
        autoFocus={autoFocus}
        tabIndex={tabIndex}
        compact
        disabled={folderId.isError}
      />
      {folderId
        .map((fId) => (
          <input
            key={null}
            ref={inputRef}
            accept="*"
            hidden
            multiple
            onChange={({ target: { files } }) => {
              if (files === null) return;
              void uploadFiles(fId, [...files]).then(() => {
                onUploadComplete();
                trackEvent("user:uploaded:file:gallery");
              });
            }}
            type="file"
          />
        ))
        .orElse(null)}
    </>
  );
};

const NewFolderMenuItem = ({
  folderId,
  onDialogClose,
  autoFocus,
  tabIndex,
}: {
  folderId: Result<Id>;
  onDialogClose: (success: boolean) => void;

  /*
   * These properties are dynamically added by the MUI Menu parent component
   */
  autoFocus?: boolean;
  tabIndex?: number;
}) => {
  const [open, setOpen] = React.useState(false);
  const [name, setName] = React.useState("");
  const { createFolder } = useGalleryActions();
  const [submitting, setSubmitting] = React.useState(false);
  const { trackEvent } = React.useContext(AnalyticsContext);

  const inputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    setTimeout(() => {
      if (open) inputRef.current?.focus();
    }, 100);
  }, [inputRef, open]);

  return (
    <>
      <EventBoundary>
        <Dialog
          open={open}
          onClose={() => {
            setOpen(false);
            onDialogClose(false);
          }}
        >
          <form /* onSubmit is handled by ValidatingSubmitButton */>
            <DialogTitle>New Folder</DialogTitle>
            <DialogContent>
              <DialogContentText variant="body2" sx={{ mb: 2 }}>
                Please give the new folder a name.
              </DialogContentText>
              <TextField
                size="small"
                label="Name"
                onChange={({ target: { value } }) => setName(value)}
                inputProps={{ ref: inputRef }}
              />
            </DialogContent>
            <DialogActions>
              <Button
                onClick={() => {
                  setName("");
                  setOpen(false);
                  onDialogClose(false);
                }}
              >
                Cancel
              </Button>
              <ValidatingSubmitButton
                loading={submitting}
                validationResult={
                  name.length > 0 ? IsValid() : IsInvalid("A name is required.")
                }
                onClick={() => {
                  setSubmitting(true);
                  const fId = folderId.elseThrow();
                  void createFolder(fId, name)
                    .then(() => {
                      onDialogClose(true);
                      trackEvent("user:create:folder:gallery");
                    })
                    .finally(() => {
                      setSubmitting(false);
                    });
                }}
              >
                Create
              </ValidatingSubmitButton>
            </DialogActions>
          </form>
        </Dialog>
      </EventBoundary>
      <AccentMenuItem
        title="New Folder"
        avatar={<CreateNewFolderIcon />}
        backgroundColor={ACCENT_COLOR.background}
        foregroundColor={ACCENT_COLOR.contrastText}
        onClick={() => {
          setOpen(true);
        }}
        //eslint-disable-next-line jsx-a11y/no-autofocus
        autoFocus={autoFocus}
        tabIndex={tabIndex}
        aria-haspopup="dialog"
        compact
        disabled={folderId.isError}
      />
    </>
  );
};

const AddFilestoreMenuItem = ({
  onMenuClose,
  autoFocus,
  tabIndex,
}: {
  onMenuClose: (success: boolean) => void;
  /*
   * These properties are dynamically added by the MUI Menu parent component
   */
  autoFocus?: boolean;
  tabIndex?: number;
}) => {
  const filestoresEnabled = useDeploymentProperty("netfilestores.enabled");
  const [open, setOpen] = React.useState(false);
  const [filesystems, setFilesystems] = React.useState<null | ReadonlyArray<{
    id: number;
    name: string;
    url: string;
  }>>(null);
  const { getToken } = useOauthToken();
  const api = React.useRef(
    (async () => {
      return axios.create({
        baseURL: "/api/v1/gallery",
        headers: {
          Authorization: "Bearer " + (await getToken()),
        },
      });
    })(),
  );

  React.useEffect(() => {
    void (async () => {
      const { data } = await (await api.current).get<unknown>("filesystems");
      Parsers.isArray(data)
        .flatMap((array) =>
          Result.all(
            ...array.map((m) =>
              Parsers.isObject(m)
                .flatMap(Parsers.isNotNull)
                .flatMap((obj) => {
                  try {
                    const id = Parsers.getValueWithKey("id")(obj)
                      .flatMap(Parsers.isNumber)
                      .elseThrow();
                    const name = Parsers.getValueWithKey("name")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();
                    const url = Parsers.getValueWithKey("url")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();
                    return Result.Ok({ id, name, url });
                  } catch (e) {
                    return Result.Error<{
                      id: number;
                      name: string;
                      url: string;
                    }>([e instanceof Error ? e : new Error("Unknown error")]);
                  }
                }),
            ),
          ),
        )
        .do((newFilesystems) => setFilesystems(newFilesystems));
    })();
  }, []);

  return (
    <>
      <AddFilestoreDialog
        open={open}
        onClose={(success) => {
          setOpen(false);
          onMenuClose(success);
        }}
      />
      {FetchingData.getSuccessValue(filestoresEnabled)
        .flatMap(Parsers.isBoolean)
        .flatMap(Parsers.isTrue)
        .map(() => (
          <AccentMenuItem
            key={null}
            title="Add a Filestore"
            subheader={
              (filesystems ?? []).length === 0
                ? "System Admin has not configured any external filestores."
                : null
            }
            avatar={<DnsIcon />}
            backgroundColor={ACCENT_COLOR.background}
            foregroundColor={ACCENT_COLOR.contrastText}
            onClick={() => {
              setOpen(true);
            }}
            //eslint-disable-next-line jsx-a11y/no-autofocus
            autoFocus={autoFocus}
            tabIndex={tabIndex}
            aria-haspopup="dialog"
            compact
            disabled={(filesystems ?? []).length === 0}
          />
        ))
        .orElse(null)}
    </>
  );
};

type DmpMenuSectionArgs = {
  onDialogClose: () => void;
  showDmpPanel: () => void;
};

const DmpMenuSection = ({
  onDialogClose,
  showDmpPanel,
}: DmpMenuSectionArgs) => {
  const showArgos = FetchingData.getSuccessValue(
    useIntegrationIsAllowedAndEnabled("ARGOS"),
  ).orElse(false);
  const showDmponline = FetchingData.getSuccessValue(
    useIntegrationIsAllowedAndEnabled("DMPONLINE"),
  ).orElse(false);
  const showDmptool = FetchingData.getSuccessValue(
    useIntegrationIsAllowedAndEnabled("DMPTOOL"),
  ).orElse(false);

  React.useEffect(() => {
    /*
     * This is to maintain backwards compatibility with the old Gallery. It
     * exposes a global function `gallery` to updates the current listing of
     * files. Once we no longer need to maintain backwards compatibility, we
     * could pass `showDmpPanel` down into each DMPDialog component.
     */
    // @ts-expect-error gallery is a global function
    window.gallery = showDmpPanel;
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - showDmpPanel will not meaningfully change
     */
  }, []);

  if (!showArgos && !showDmponline && !showDmptool) return null;
  return (
    <>
      <Divider textAlign="left" aria-label="DMPs">
        DMP Import
      </Divider>
      {showArgos && <ArgosAccentMenuItem onDialogClose={onDialogClose} />}
      {showDmponline && (
        <DMPOnlineAccentMenuItem onDialogClose={onDialogClose} />
      )}
      {showDmptool && <DMPToolAccentMenuItem onDialogClose={onDialogClose} />}
    </>
  );
};

const DrawerTab = styled(
  //eslint-disable-next-line react/display-name
  React.forwardRef<
    HTMLDivElement,
    {
      drawerOpen: boolean;
      icon: React.ReactNode;
      label: React.ReactNode;
      index: number;
      className?: string;
      selected: boolean;
      onClick: () => void;
      tabIndex: number;
    }
  >(
    (
      { icon, label, index, className, selected, onClick, tabIndex },
      ref: React.ForwardedRef<HTMLDivElement>,
    ) => (
      <ListItem disablePadding className={className}>
        <ListItemButton
          selected={selected}
          onClick={onClick}
          tabIndex={tabIndex}
          ref={ref}
        >
          <ListItemIcon>{icon}</ListItemIcon>
          <ListItemText
            primary={label}
            sx={{ transitionDelay: `${(index + 1) * 0.02}s !important` }}
          />
        </ListItemButton>
      </ListItem>
    ),
  ),
)(({ drawerOpen }) => ({
  position: "static",
  "& .MuiListItemText-root": {
    transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "none"
      : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
    opacity: drawerOpen ? 1 : 0,
    transform: drawerOpen ? "unset" : "translateX(-20px)",
    textTransform: "uppercase",
  },
  "& .MuiListItemButton-root": {
    "&:hover": {
      backgroundColor: darken(
        `hsl(${ACCENT_COLOR.background.hue}deg, ${ACCENT_COLOR.background.saturation}%, 100%)`,
        0.05,
      ),
    },
    "&.Mui-selected": {
      "&:hover": {
        backgroundColor: darken(
          `hsl(${ACCENT_COLOR.background.hue}deg, ${ACCENT_COLOR.background.saturation}%, 100%)`,
          0.05,
        ),
      },
    },
  },
}));

type SidebarArgs = {
  selectedSection: GallerySection | null;
  setSelectedSection: (section: GallerySection) => void;
  drawerOpen: boolean;
  setDrawerOpen: (open: boolean) => void;
  folderId: FetchingData.Fetched<Id>;
  refreshListing: () => Promise<void>;
  id: string;
};

const Sidebar = ({
  selectedSection,
  setSelectedSection,
  drawerOpen,
  setDrawerOpen,
  folderId,
  refreshListing,
  id,
}: SidebarArgs): React.ReactNode => {
  const sidebarRef = useLandmark("Navigation");
  const [newMenuAnchorEl, setNewMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const viewport = useViewportDimensions();
  const filestoresEnabled = useDeploymentProperty("netfilestores.enabled");

  React.useEffect(() => {
    autorun(() => {
      if (viewport.isViewportSmall) setDrawerOpen(false);
    });
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - setDrawerOpen should not meaningfully change
     */
  }, [viewport]);

  const showFilestores = FetchingData.getSuccessValue(filestoresEnabled)
    .flatMap(Parsers.isBoolean)
    .flatMap(Parsers.isTrue)
    .orElse(false);

  const { getTabIndex, getRef, eventHandlers } =
    useOneDimensionalRovingTabIndex<HTMLDivElement>({
      max: showFilestores ? 9 : 8,
    });

  return (
    <CustomDrawer
      open={drawerOpen}
      anchor="left"
      variant={viewport.isViewportSmall ? "temporary" : "permanent"}
      onClose={() => {
        if (viewport.isViewportSmall) setDrawerOpen(false);
      }}
      role="region"
      aria-label="gallery sections drawer"
      id={id}
      ref={sidebarRef as React.Ref<HTMLDivElement>}
    >
      <Box width="100%" p={1.5}>
        <AddButton
          onClick={(e) => setNewMenuAnchorEl(e.currentTarget)}
          drawerOpen={drawerOpen}
        />
        <StyledMenu
          open={Boolean(newMenuAnchorEl)}
          anchorEl={newMenuAnchorEl}
          onClose={() => {
            if (viewport.isViewportSmall) setDrawerOpen(false);
            setNewMenuAnchorEl(null);
          }}
          MenuListProps={{
            disablePadding: true,
          }}
        >
          <UploadMenuItem
            key={"upload"}
            folderId={FetchingData.getSuccessValue(folderId)}
            onUploadComplete={() => {
              void refreshListing();
              setNewMenuAnchorEl(null);
              if (viewport.isViewportSmall) setDrawerOpen(false);
            }}
            onCancel={() => {
              setNewMenuAnchorEl(null);
              if (viewport.isViewportSmall) setDrawerOpen(false);
            }}
          />
          <NewFolderMenuItem
            key={"newFolder"}
            folderId={FetchingData.getSuccessValue(folderId)}
            onDialogClose={(success) => {
              if (success) void refreshListing();
              setNewMenuAnchorEl(null);
              if (viewport.isViewportSmall) setDrawerOpen(false);
            }}
          />
          <AddFilestoreMenuItem
            onMenuClose={(success) => {
              if (success) void refreshListing();
              setNewMenuAnchorEl(null);
              if (viewport.isViewportSmall) setDrawerOpen(false);
            }}
          />
          <DmpMenuSection
            onDialogClose={() => {
              setNewMenuAnchorEl(null);
              if (viewport.isViewportSmall) setDrawerOpen(false);
            }}
            showDmpPanel={() => {
              if (selectedSection === "DMPs") {
                void refreshListing();
              } else {
                setSelectedSection("DMPs");
              }
            }}
          />
        </StyledMenu>
      </Box>
      <Divider />
      <Box
        {...eventHandlers}
        sx={{
          overflowY: "auto",
          overflowX: "hidden",
          position: "relative",
        }}
      >
        <div role="navigation">
          <List sx={{ position: "static" }}>
            <DrawerTab
              label={gallerySectionLabel.Images}
              icon={gallerySectionIcon.Images}
              index={0}
              tabIndex={getTabIndex(0)}
              ref={getRef(0)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "Images"}
              onClick={() => {
                setSelectedSection("Images");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
            <DrawerTab
              label={gallerySectionLabel.Audios}
              icon={gallerySectionIcon.Audios}
              index={1}
              tabIndex={getTabIndex(1)}
              ref={getRef(1)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "Audios"}
              onClick={() => {
                setSelectedSection("Audios");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
            <DrawerTab
              label={gallerySectionLabel.Videos}
              icon={gallerySectionIcon.Videos}
              index={2}
              tabIndex={getTabIndex(2)}
              ref={getRef(2)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "Videos"}
              onClick={() => {
                setSelectedSection("Videos");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
            <DrawerTab
              label={gallerySectionLabel.Documents}
              icon={gallerySectionIcon.Documents}
              index={3}
              tabIndex={getTabIndex(3)}
              ref={getRef(3)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "Documents"}
              onClick={() => {
                setSelectedSection("Documents");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
            <DrawerTab
              label={gallerySectionLabel.Chemistry}
              icon={gallerySectionIcon.Chemistry}
              index={4}
              tabIndex={getTabIndex(4)}
              ref={getRef(4)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "Chemistry"}
              onClick={() => {
                setSelectedSection("Chemistry");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
            <DrawerTab
              label={gallerySectionLabel.Miscellaneous}
              icon={gallerySectionIcon.Miscellaneous}
              index={5}
              tabIndex={getTabIndex(5)}
              ref={getRef(5)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "Miscellaneous"}
              onClick={() => {
                setSelectedSection("Miscellaneous");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
            <DrawerTab
              label={gallerySectionLabel.Snippets}
              icon={gallerySectionIcon.Snippets}
              index={6}
              tabIndex={getTabIndex(6)}
              ref={getRef(6)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "Snippets"}
              onClick={() => {
                setSelectedSection("Snippets");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
          </List>
          <Divider />
          <List sx={{ position: "static" }}>
            {showFilestores && (
              <DrawerTab
                key={null}
                label={gallerySectionLabel.NetworkFiles}
                icon={gallerySectionIcon.NetworkFiles}
                index={7}
                tabIndex={getTabIndex(7)}
                ref={getRef(7)}
                drawerOpen={drawerOpen}
                selected={selectedSection === "NetworkFiles"}
                onClick={() => {
                  setSelectedSection("NetworkFiles");
                  if (viewport.isViewportSmall) setDrawerOpen(false);
                }}
              />
            )}
            <DrawerTab
              label={gallerySectionLabel.DMPs}
              icon={gallerySectionIcon.DMPs}
              index={showFilestores ? 8 : 7}
              tabIndex={getTabIndex(showFilestores ? 8 : 7)}
              ref={getRef(showFilestores ? 8 : 7)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "DMPs"}
              onClick={() => {
                setSelectedSection("DMPs");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
            <DrawerTab
              label={gallerySectionLabel.PdfDocuments}
              icon={gallerySectionIcon.PdfDocuments}
              index={showFilestores ? 9 : 8}
              tabIndex={getTabIndex(showFilestores ? 9 : 8)}
              ref={getRef(showFilestores ? 9 : 8)}
              drawerOpen={drawerOpen}
              selected={selectedSection === "PdfDocuments"}
              onClick={() => {
                setSelectedSection("PdfDocuments");
                if (viewport.isViewportSmall) setDrawerOpen(false);
              }}
            />
          </List>
        </div>
      </Box>
    </CustomDrawer>
  );
};

/**
 * The gallery's main sidebar for navigating between the different sections,
 * for creating new folders, uploading new files, and connecting to external
 * filestores.
 */
export default observer(Sidebar);
