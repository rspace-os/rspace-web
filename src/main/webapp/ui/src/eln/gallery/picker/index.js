//@flow

import Dialog from "@mui/material/Dialog";
import React, { type Node, type ElementConfig, type Ref } from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import AppBar from "../components/AppBar";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import Box from "@mui/material/Box";
import createAccentedTheme from "../../../accentedTheme";
import Grow from "@mui/material/Grow";
import useViewportDimensions from "../../../util/useViewportDimensions";
import { useGalleryListing, type GalleryFile } from "../useGalleryListing";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import Result from "../../../util/result";
import { observer } from "mobx-react-lite";
import Sidebar from "../components/Sidebar";
import MainPanel from "../components/MainPanel";
import useUiPreference, { PREFERENCES } from "../../../util/useUiPreference";
import { COLOR, type GallerySection } from "../common";
import { CallableImagePreview } from "../components/CallableImagePreview";
import { CallablePdfPreview } from "../components/CallablePdfPreview";
import { CallableAsposePreview } from "../components/CallableAsposePreview";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import { CLOSED_MOBILE_INFO_PANEL_HEIGHT } from "../components/InfoPanel";
import RsSet from "../../../util/set";
import { DisableDragAndDropByDefault } from "../../../components/useFileImportDragAndDrop";
import OpenFolderProvider from "../components/OpenFolderProvider";

const CustomDialog = styled(Dialog)(({ theme }) => ({
  zIndex: 1100, // less than the SwipeableDrawer so that mobile info panel is shown
  "& .MuiDialog-container > .MuiPaper-root": {
    height: "calc(100% - 32px)", // 16px margin above and below dialog
    [theme.breakpoints.down("md")]: {
      // sm and xs are fullscreen, where the sidebar is a floating element
      height: "100%",
      borderRadius: 0,
    },
  },
  "& .MuiDialogContent-root": {
    width: "100%",
    height: "calc(100% - 52px)", // 52px being the height of DialogActions
    overflowY: "unset",
    padding: theme.spacing(1.5, 2),
  },
}));

const CustomGrow = React.forwardRef<ElementConfig<typeof Grow>, {||}>(
  (props: ElementConfig<typeof Grow>, ref: Ref<typeof Grow>) => (
    <Grow
      {...props}
      ref={ref}
      timeout={
        window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 300
      }
      easing="ease-in-out"
      style={{
        transformOrigin: "center 70%",
      }}
    />
  )
);
CustomGrow.displayName = "CustomGrow";

const Picker = observer(
  ({
    open,
    onClose,
    onSubmit,
    validateSelection,
  }: {|
    open: boolean,
    onClose: () => void,
    onSubmit: (RsSet<GalleryFile>) => void,
    validateSelection: (GalleryFile) => Result<null>,
  |}) => {
    const sidebarId = React.useId();
    const viewport = useViewportDimensions();
    const selection = useGallerySelection();
    const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
    const [orderBy, setOrderBy] = useUiPreference<"name" | "modificationDate">(
      PREFERENCES.GALLERY_SORT_BY,
      {
        defaultValue: "modificationDate",
      }
    );
    const [sortOrder, setSortOrder] = useUiPreference<"DESC" | "ASC">(
      PREFERENCES.GALLERY_SORT_ORDER,
      {
        defaultValue: "DESC",
      }
    );
    const [selectedSection, setSelectedSection] =
      useUiPreference<GallerySection>(
        PREFERENCES.GALLERY_PICKER_INITIAL_SECTION,
        { defaultValue: "Chemistry" }
      );
    const [drawerOpen, setDrawerOpen] = React.useState(
      /*
       * On the Gallery page, the sidebar is open on any viewport size that
       * isn't small -- on the smaller viewport sizes the sidebar is floating
       * instead of being persistent. However, in the picker dialog there is
       * less space than on the main page as the dialog isn't fullscreen and on
       * the medium size we run into there not being enough horizontal space to
       * properly display the info panel. As such, initially the sidebar is
       * closed. The user can open it if they wish but at least we initially
       * present everything with enough space.
       */
      viewport.isViewportLarge
    );
    const { galleryListing, path, setPath, folderId, refreshListing } =
      useGalleryListing({
        section: selectedSection,
        searchTerm: appliedSearchTerm,
        path: [],
        orderBy,
        sortOrder,
      });

    return (
      <CallableImagePreview>
        <CallablePdfPreview>
          <CallableAsposePreview>
            <OpenFolderProvider setPath={setPath}>
              <CustomDialog
                maxWidth="xl"
                fullWidth
                open={open}
                TransitionComponent={CustomGrow}
                onClose={onClose}
                fullScreen={viewport.isViewportSmall}
                sx={{
                  height: {
                    xs:
                      selection.size > 0
                        ? `calc(100% - ${CLOSED_MOBILE_INFO_PANEL_HEIGHT}px)`
                        : "100%",
                    md: "unset",
                  },
                }}
                aria-label="Gallery Picker"
              >
                <AppBar
                  appliedSearchTerm={appliedSearchTerm}
                  setAppliedSearchTerm={setAppliedSearchTerm}
                  hideSearch={selectedSection === "NetworkFiles"}
                  setDrawerOpen={setDrawerOpen}
                  drawerOpen={drawerOpen}
                  sidebarId={sidebarId}
                />
                <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
                  <Sidebar
                    selectedSection={selectedSection}
                    setSelectedSection={(mediaType) => {
                      setSelectedSection(mediaType);
                    }}
                    drawerOpen={drawerOpen}
                    setDrawerOpen={setDrawerOpen}
                    folderId={folderId}
                    refreshListing={refreshListing}
                    id={sidebarId}
                  />
                  <Box
                    sx={{
                      height: "100%",
                      display: "flex",
                      flexDirection: "column",
                      flexGrow: 1,
                      width: "calc(100% - 200px)",
                    }}
                  >
                    <MainPanel
                      selectedSection={selectedSection}
                      path={path}
                      clearPath={() => setPath([])}
                      galleryListing={galleryListing}
                      folderId={folderId}
                      refreshListing={refreshListing}
                      sortOrder={sortOrder}
                      orderBy={orderBy}
                      setSortOrder={setSortOrder}
                      setOrderBy={setOrderBy}
                      appliedSearchTerm={appliedSearchTerm}
                      setAppliedSearchTerm={setAppliedSearchTerm}
                    />
                    <DialogActions>
                      <Button onClick={() => onClose()}>Cancel</Button>
                      <ValidatingSubmitButton
                        validationResult={
                          selection.size > 0
                            ? Result.all(
                                ...selection.asSet().map(validateSelection)
                              ).map(() => null)
                            : Result.Error([
                                new Error(
                                  "Select at least one file to proceed."
                                ),
                              ])
                        }
                        loading={false}
                        onClick={() => {
                          onSubmit(selection.asSet());
                        }}
                      >
                        Add
                      </ValidatingSubmitButton>
                    </DialogActions>
                  </Box>
                </Box>
              </CustomDialog>
            </OpenFolderProvider>
          </CallableAsposePreview>
        </CallablePdfPreview>
      </CallableImagePreview>
    );
  }
);

/**
 * This component allows other parts of the product to present an interface for
 * the user to pick a number of Gallery files
 */
export default function Wrapper({
  open,
  onClose,
  onSubmit,
  onlyAllowSingleSelection,
  validateSelection = () => Result.Ok(null),
}: {|
  open: boolean,
  onClose: () => void,
  onSubmit: (RsSet<GalleryFile>) => void,
  onlyAllowSingleSelection?: boolean,
  validateSelection?: (GalleryFile) => Result<null>,
|}): Node {
  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <GallerySelection onlyAllowSingleSelection={onlyAllowSingleSelection}>
        <DisableDragAndDropByDefault>
          <Picker
            open={open}
            onClose={onClose}
            onSubmit={onSubmit}
            validateSelection={validateSelection}
          />
        </DisableDragAndDropByDefault>
      </GallerySelection>
    </ThemeProvider>
  );
}
