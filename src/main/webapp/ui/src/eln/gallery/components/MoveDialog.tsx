import React from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import Result from "../../../util/result";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import TreeView from "./TreeView";
import { useGalleryListing, type GalleryFile } from "../useGalleryListing";
import * as FetchingData from "../../../util/fetchingData";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import {
  useGalleryActions,
  rootDestination,
  folderDestination,
} from "../useGalleryActions";
import RsSet from "../../../util/set";
import useViewportDimensions from "../../../util/useViewportDimensions";
import { type GallerySection } from "../common";
import { observer } from "mobx-react-lite";
import PlaceholderLabel from "./PlaceholderLabel";
import { doNotAwait } from "../../../util/Util";
import AnalyticsContext from "../../../stores/contexts/Analytics";

type MoveDialogArgs = {
  open: boolean;
  onClose: () => void;
  section: GallerySection;
  selectedFiles: RsSet<GalleryFile>;

  /**
   * A function to refresh the page's main listing after a move operation.
   * Note that this is distinct from the refreshListing function for refreshing
   * the tree view inside of the dialog.
   */
  refreshListing: () => Promise<void>;
};

const MoveDialog = observer(
  ({
    open,
    onClose,
    section,
    selectedFiles,
    refreshListing,
  }: MoveDialogArgs): React.ReactNode => {
    const { isViewportVerySmall } = useViewportDimensions();
    const { trackEvent } = React.useContext(AnalyticsContext);

    const listingOf = React.useMemo(
      () => ({
        tag: "section" as const,
        section,
        path: [],
      }),
      [section]
    );
    const { galleryListing, refreshListing: refreshListingInsideDialog } =
      useGalleryListing({
        listingOf,
        searchTerm: "",
        orderBy: "name",
        sortOrder: "ASC",
        foldersOnly: true,
      });
    const { moveFiles } = useGalleryActions();
    const selection = useGallerySelection();

    React.useEffect(() => {
      if (open) void refreshListingInsideDialog();
      /* eslint-disable-next-line react-hooks/exhaustive-deps --
       * - refreshListingInsideDialog will not meaningfully change between renders
       */
    }, [open]);

    const [topLevelLoading, setTopLevelLoading] = React.useState(false);
    const [submitLoading, setSubmitLoading] = React.useState(false);

    function computeValidation() {
      const files = selection.asSet();
      if (files.isEmpty)
        return Result.Error<null>([new Error("No folder is selected.")]);
      if (files.size > 1)
        return Result.Error<null>([
          new Error("More than one folder is selected."),
        ]);
      return Result.Ok(null);
    }

    const selectedFileIds = selectedFiles.map(({ id }) => id);
    function filterFile(file: GalleryFile) {
      if (!file.isFolder) return "hide";
      if (file.isSnippetFolder) return "hide";
      if (selectedFileIds.has(file.id)) return "disabled";
      return "enabled";
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
            Choose a folder, enter a path, or tap the &quot;top-level&quot;
            button.
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
                  path={[]}
                  selectedSection={section}
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
              onClick={doNotAwait(async () => {
                setTopLevelLoading(true);
                try {
                  await moveFiles(section, rootDestination(), selectedFiles);
                  void refreshListing();
                  onClose();
                  trackEvent("user:moved:files:gallery", {
                    count: selectedFiles.size,
                  });
                } finally {
                  setTopLevelLoading(false);
                }
              })}
              disabled={topLevelLoading}
              loading={topLevelLoading}
              label="Make top-level"
            />
            <Box flexGrow={1}></Box>
            <Button
              onClick={() => {
                onClose();
              }}
            >
              Cancel
            </Button>
            <ValidatingSubmitButton
              loading={submitLoading}
              onClick={doNotAwait(async () => {
                setSubmitLoading(true);
                try {
                  const destinationFolder = selection
                    .asSet()
                    .only.toResult(
                      () =>
                        new Error(
                          "Impossible; submit button requires a selection of one"
                        )
                    )
                    .elseThrow();
                  await moveFiles(
                    section,
                    folderDestination(destinationFolder),
                    selectedFiles
                  );
                  void refreshListing();
                  onClose();
                  trackEvent("user:moved:files:gallery", {
                    count: selectedFiles.size,
                  });
                } finally {
                  setSubmitLoading(false);
                }
              })}
              validationResult={computeValidation()}
            >
              Move
            </ValidatingSubmitButton>
          </Stack>
        </DialogActions>
      </Dialog>
    );
  }
);

/**
 * A dialog for moving files between folders.
 *
 * Note that the dialog is added to the DOM as soon as the component is
 * rendered, and will make network requests to fetch the folder structure
 * immediately.
 */
// eslint-disable-next-line react/display-name -- This is a wrapper component
export default (
  props: Omit<MoveDialogArgs, "selectedFiles">
): React.ReactNode => {
  const selection = useGallerySelection();
  return (
    <GallerySelection onlyAllowSingleSelection>
      <MoveDialog {...props} selectedFiles={selection.asSet()} />
    </GallerySelection>
  );
};
