import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import useViewportDimensions from "../../../hooks/browser/useViewportDimensions";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import * as FetchingData from "../../../util/fetchingData";
import Result from "../../../util/result";
import type RsSet from "../../../util/set";
import { GALLERY_SECTION } from "../common";
import { useGalleryActions } from "../useGalleryActions";
import { type Filestore, type GalleryFile, RemoteFile, useGalleryListing } from "../useGalleryListing";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import PlaceholderLabel from "./PlaceholderLabel";
import TreeView from "./TreeView";

type MoveWithinFilestoreDialogArgs = {
  open: boolean;
  onClose: () => void;
  /** The S3 filestore being moved within; also the root of the destination tree. */
  filestore: Filestore;
  sources: RsSet<RemoteFile>;
  /** Refreshes the page's main listing after a move (distinct from the dialog's own tree refresh). */
  refreshListing: () => Promise<void>;
};

/**
 * Move dialog for items inside an S3 filestore: like MoveDialog but browses the filestore's own
 * folder tree, moving via the filestore API to any subfolder or the filestore root.
 */
const MoveWithinFilestoreDialog = observer(
  ({ open, onClose, filestore, sources, refreshListing }: MoveWithinFilestoreDialogArgs): React.ReactNode => {
    const { isViewportVerySmall } = useViewportDimensions();
    const { trackEvent } = React.useContext(AnalyticsContext);
    const { t } = useTranslation(["gallery", "common"]);

    const listingOf = React.useMemo(
      () => ({
        tag: "section" as const,
        section: GALLERY_SECTION.NETWORKFILES,
        path: [filestore],
      }),
      [filestore],
    );
    const { galleryListing, refreshListing: refreshListingInsideDialog } = useGalleryListing({
      listingOf,
      searchTerm: "",
      orderBy: "name",
      sortOrder: "ASC",
      foldersOnly: true,
    });
    const { moveRemoteFiles } = useGalleryActions();
    const selection = useGallerySelection();

    React.useEffect(() => {
      if (open) void refreshListingInsideDialog();
    }, [open]);

    const [rootLoading, setRootLoading] = React.useState(false);
    const [submitLoading, setSubmitLoading] = React.useState(false);

    function computeValidation() {
      const files = selection.asSet();
      if (files.isEmpty) return Result.Error<null>([new Error(t("moveWithinFilestore.validation.noFolder"))]);
      if (files.size > 1) return Result.Error<null>([new Error(t("moveWithinFilestore.validation.tooManyFolders"))]);
      return Result.Ok(null);
    }

    // A folder cannot be moved into itself; disable the source folders in the destination tree.
    const sourcePaths = new Set(sources.toArray().map((f) => f.remotePath));
    function filterFile(file: GalleryFile): "hide" | "enabled" | "disabled" {
      if (!file.isFolder) return "hide";
      if (file instanceof RemoteFile && sourcePaths.has(file.remotePath)) return "disabled";
      return "enabled";
    }

    async function move(destPath: string, setLoading: (b: boolean) => void) {
      setLoading(true);
      try {
        await moveRemoteFiles(sources.toArray(), destPath);
        void refreshListing();
        onClose();
        trackEvent("user:moved:files:gallery", { count: sources.size });
      } finally {
        setLoading(false);
      }
    }

    return (
      <Dialog
        open={open}
        onClose={onClose}
        onKeyDown={(e) => {
          e.stopPropagation();
        }}
        scroll="paper"
        fullScreen={isViewportVerySmall}
      >
        <DialogTitle>{t("moveWithinFilestore.title")}</DialogTitle>
        <DialogContent sx={{ overflow: "hidden", flexGrow: 0 }}>
          <DialogContentText variant="body2">{t("moveWithinFilestore.description")}</DialogContentText>
        </DialogContent>
        <DialogContent sx={{ pt: 0 }}>
          <Box sx={{ overflowY: "auto" }}>
            {FetchingData.match(galleryListing, {
              loading: () => <PlaceholderLabel>{t("moveWithinFilestore.loading")}</PlaceholderLabel>,
              error: (error) => <PlaceholderLabel>{error}</PlaceholderLabel>,
              success: (listing) => (
                <TreeView
                  listing={listing}
                  path={[filestore]}
                  selectedSection={GALLERY_SECTION.NETWORKFILES}
                  refreshListing={refreshListingInsideDialog}
                  filter={filterFile}
                  disableDragAndDrop
                  sortOrder="ASC"
                  orderBy="name"
                  foldersOnly
                />
              ),
            })}
          </Box>
        </DialogContent>
        <DialogActions>
          <Stack direction="row" spacing={1}>
            <SubmitSpinnerButton
              onClick={() => {
                void move("", setRootLoading);
              }}
              disabled={rootLoading}
              loading={rootLoading}
              label={t("moveWithinFilestore.moveToTopLevel")}
            />
            <Box sx={{ flexGrow: 1 }}></Box>
            <Button
              onClick={() => {
                onClose();
              }}
            >
              {t("common:actions.cancel")}
            </Button>
            <ValidatingSubmitButton
              loading={submitLoading}
              onClick={() => {
                const destination = selection
                  .asSet()
                  .only.toResult(() => new Error("Impossible; submit button requires a selection of one"))
                  .elseThrow();
                const destPath = destination instanceof RemoteFile ? destination.remotePath : "";
                void move(destPath, setSubmitLoading);
              }}
              validationResult={computeValidation()}
            >
              {t("moveWithinFilestore.move")}
            </ValidatingSubmitButton>
          </Stack>
        </DialogActions>
      </Dialog>
    );
  },
);

/** Wraps the dialog in a fresh single-selection context for picking the destination folder. */
export default (props: MoveWithinFilestoreDialogArgs): React.ReactNode => {
  return (
    <GallerySelection onlyAllowSingleSelection>
      <MoveWithinFilestoreDialog {...props} />
    </GallerySelection>
  );
};
