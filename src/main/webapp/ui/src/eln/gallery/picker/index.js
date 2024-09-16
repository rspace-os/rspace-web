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
import { COLOR } from "../common";
import { CallableImagePreview } from "../components/CallableImagePreview";
import { CallablePdfPreview } from "../components/CallablePdfPreview";
import { CallableAsposePreview } from "../components/CallableAsposePreview";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import { CLOSED_MOBILE_INFO_PANEL_HEIGHT } from "../components/InfoPanel";

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
  }: {|
    open: boolean,
    onClose: () => void,
    onSubmit: (Set<GalleryFile>) => void,
  |}) => {
    const viewport = useViewportDimensions();
    const selection = useGallerySelection();
    const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
    const [orderBy, setOrderBy] = useUiPreference<"name" | "modificationDate">(
      PREFERENCES.GALLERY_SORT_BY,
      {
        defaultValue: "name",
      }
    );
    const [sortOrder, setSortOrder] = useUiPreference<"DESC" | "ASC">(
      PREFERENCES.GALLERY_SORT_ORDER,
      {
        defaultValue: "ASC",
      }
    );
    const [selectedSection, setSelectedSection] = React.useState("Chemistry");
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
      viewport.isViewportNotLarge
    );
    const { galleryListing, path, clearPath, folderId, refreshListing } =
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
            >
              <AppBar
                appliedSearchTerm={appliedSearchTerm}
                setAppliedSearchTerm={setAppliedSearchTerm}
                setDrawerOpen={setDrawerOpen}
                drawerOpen={drawerOpen}
              />
              <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
                <Sidebar
                  selectedSection={selectedSection}
                  setSelectedSection={(mediaType) => {
                    setSelectedSection(mediaType);
                  }}
                  drawerOpen={drawerOpen}
                  setDrawerOpen={setDrawerOpen}
                  path={path}
                  folderId={folderId}
                  refreshListing={refreshListing}
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
                    clearPath={clearPath}
                    galleryListing={galleryListing}
                    folderId={folderId}
                    refreshListing={refreshListing}
                    sortOrder={sortOrder}
                    orderBy={orderBy}
                    setSortOrder={setSortOrder}
                    setOrderBy={setOrderBy}
                  />
                  <DialogActions>
                    <Button onClick={() => onClose()}>Cancel</Button>
                    <ValidatingSubmitButton
                      validationResult={
                        selection.size > 0
                          ? Result.Ok(null)
                          : Result.Error([
                              new Error("Select at least one file to proceed."),
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
}: {|
  open: boolean,
  onClose: () => void,
  onSubmit: (Set<GalleryFile>) => void,
|}): Node {
  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <GallerySelection>
        <Picker open={open} onClose={onClose} onSubmit={onSubmit} />
      </GallerySelection>
    </ThemeProvider>
  );
}
