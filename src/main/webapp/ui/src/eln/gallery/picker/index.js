//@flow

import Dialog from "@mui/material/Dialog";
import React, { type Node, type ElementConfig, type Ref } from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import AppBar from "../components/AppBar";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import Box from "@mui/material/Box";
import { library } from "@fortawesome/fontawesome-svg-core";
import {
  faImage,
  faFilm,
  faFile,
  faFileInvoice,
  faDatabase,
  faShapes,
  faCircleDown,
  faVolumeLow,
} from "@fortawesome/free-solid-svg-icons";
import { faNoteSticky } from "@fortawesome/free-regular-svg-icons";
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
library.add(faImage);
library.add(faFilm);
library.add(faFile);
library.add(faFileInvoice);
library.add(faDatabase);
library.add(faShapes);
library.add(faNoteSticky);
library.add(faCircleDown);
library.add(faVolumeLow);

const CustomDialog = styled(Dialog)(({ theme }) => ({
  "& .MuiDialog-container > .MuiPaper-root": {
    width: "1000px",
    maxWidth: "1000px",
    height: "calc(100% - 32px)", // 16px margin above and below dialog
    [theme.breakpoints.only("xs")]: {
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

export default function Wrapper({
  open,
  onClose,
}: {|
  open: boolean,
  onClose: () => void,
|}): Node {
  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <Picker open={open} onClose={onClose} />
    </ThemeProvider>
  );
}

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
  ({ open, onClose }: {| open: boolean, onClose: () => void |}) => {
    const viewport = useViewportDimensions();
    const [selectedFile, setSelectedFile] = React.useState<GalleryFile | null>(
      null
    );
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
      !viewport.isViewportSmall
    );
    const { galleryListing, path, clearPath, folderId, refreshListing } =
      useGalleryListing({
        section: selectedSection,
        searchTerm: appliedSearchTerm,
        path: [],
        orderBy,
        sortOrder,
      });

    React.useEffect(() => {
      setSelectedFile(null);
    }, [selectedSection, appliedSearchTerm, path]);

    return (
      <CallableImagePreview>
        <CallablePdfPreview>
          <CallableAsposePreview>
            <CustomDialog
              open={open}
              TransitionComponent={CustomGrow}
              onClose={onClose}
              fullScreen={viewport.isViewportSmall}
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
                  }}
                >
                  <MainPanel
                    selectedSection={selectedSection}
                    path={path}
                    clearPath={clearPath}
                    galleryListing={galleryListing}
                    folderId={folderId}
                    refreshListing={refreshListing}
                    key={null}
                    sortOrder={sortOrder}
                    orderBy={orderBy}
                    setSortOrder={setSortOrder}
                    setOrderBy={setOrderBy}
                  />
                  <DialogActions>
                    <Button onClick={() => onClose()}>Cancel</Button>
                    <ValidatingSubmitButton
                      validationResult={
                        selectedFile
                          ? Result.Ok(null)
                          : Result.Error([new Error("No file selected.")])
                      }
                      loading={false}
                      onClick={() => {
                        alert("Yet to be implemented!");
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
