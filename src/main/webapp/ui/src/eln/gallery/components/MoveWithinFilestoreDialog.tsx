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
  /** The S3 filestore whose contents are being moved; also the root of the destination tree. */
  filestore: Filestore;
  /** The items to move (all inside `filestore`). */
  sources: RsSet<RemoteFile>;

  /** Refreshes the page's main listing after a move (distinct from the dialog's own tree refresh). */
  refreshListing: () => Promise<void>;
};

/**
 * Move dialog for files/folders inside an S3 filestore. Mirrors MoveDialog but browses the
 * filestore's own folder tree (rather than a local Gallery section) and moves via the filestore
 * API, using each item's filestore-relative remotePath. The destination is a folder within the
 * same filestore, or the filestore root ("Move to top level").
 */
const MoveWithinFilestoreDialog = observer(
  ({ open, onClose, filestore, sources, refreshListing }: MoveWithinFilestoreDialogArgs): React.ReactNode => {
    const { isViewportVerySmall } = useViewportDimensions();
    const { trackEvent } = React.useContext(AnalyticsContext);

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
      if (files.isEmpty) return Result.Error<null>([new Error("No folder is selected.")]);
      if (files.size > 1) return Result.Error<null>([new Error("More than one folder is selected.")]);
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
      if (filestore.id === null) return;
      setLoading(true);
      try {
        await moveRemoteFiles(filestore.id, sources.toArray(), destPath);
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
        <DialogTitle>Move</DialogTitle>
        <DialogContent sx={{ overflow: "hidden", flexGrow: 0 }}>
          <DialogContentText variant="body2">
            Choose a destination folder in this filestore, or move to its top level.
          </DialogContentText>
        </DialogContent>
        <DialogContent sx={{ pt: 0 }}>
          <Box sx={{ overflowY: "auto" }}>
            {FetchingData.match(galleryListing, {
              loading: () => <PlaceholderLabel>Loading...</PlaceholderLabel>,
              error: (error) => <PlaceholderLabel>{error}</PlaceholderLabel>,
              success: (listing) => (
                <TreeView
                  listing={listing}
                  path={[filestore]}
                  selectedSection={GALLERY_SECTION.NETWORKFILES}
                  refreshListing={refreshListing}
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
              label="Move to top level"
            />
            <Box sx={{ flexGrow: 1 }}></Box>
            <Button
              onClick={() => {
                onClose();
              }}
            >
              Cancel
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
              Move
            </ValidatingSubmitButton>
          </Stack>
        </DialogActions>
      </Dialog>
    );
  },
);

/**
 * A dialog for moving files/folders to another folder within the same S3 filestore.
 *
 * Like MoveDialog, the dialog mounts (and fetches the filestore folder tree) as soon as it is
 * rendered. It owns a fresh single-selection context for picking the destination folder.
 */
export default (props: MoveWithinFilestoreDialogArgs): React.ReactNode => {
  return (
    <GallerySelection onlyAllowSingleSelection>
      <MoveWithinFilestoreDialog {...props} />
    </GallerySelection>
  );
};
