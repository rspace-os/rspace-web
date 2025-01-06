//@flow

import React, { type Node } from "react";
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
import { useGalleryActions, rootDestination } from "../useGalleryActions";
import RsSet from "../../../util/set";
import useViewportDimensions from "../../../util/useViewportDimensions";
import { type GallerySection } from "../common";
import { observer } from "mobx-react-lite";
import PlaceholderLabel from "./PlaceholderLabel";

type MoveDialogArgs = {|
  open: boolean,
  onClose: () => void,
  section: GallerySection,
  selectedFiles: RsSet<GalleryFile>,
  refreshListing: () => Promise<void>,
|};

const MoveDialog = observer(
  ({
    open,
    onClose,
    section,
    selectedFiles,
    refreshListing,
  }: MoveDialogArgs): Node => {
    const { isViewportVerySmall } = useViewportDimensions();

    const { galleryListing, refreshListing: refreshListingInsideDialog } =
      useGalleryListing({
        section,
        searchTerm: "",
        path: [],
        orderBy: "name",
        sortOrder: "ASC",
        foldersOnly: true,
      });
    const { moveFiles } = useGalleryActions();
    const selection = useGallerySelection();

    React.useEffect(() => {
      if (open) void refreshListingInsideDialog();
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
              onClick={() => {
                setTopLevelLoading(true);
                void moveFiles(selectedFiles)
                  .to({
                    destination: rootDestination(),
                    section,
                  })
                  .then(() => {
                    setTopLevelLoading(false);
                    void refreshListing();
                    onClose();
                  });
              }}
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
              onClick={() => {
                setSubmitLoading(true);
                const destinationFolder = selection
                  .asSet()
                  .only.toResult(
                    () =>
                      new Error(
                        "Impossible; submit button requires a selection of one"
                      )
                  )
                  .elseThrow();
                void moveFiles(selectedFiles)
                  .toDestinationWithFolder(section, destinationFolder)
                  .then(() => {
                    setSubmitLoading(false);
                    void refreshListing();
                    onClose();
                  });
              }}
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

export default (
  props: $Diff<MoveDialogArgs, {| selectedFiles: mixed |}>
): Node => {
  const selection = useGallerySelection();
  return (
    <GallerySelection onlyAllowSingleSelection>
      <MoveDialog {...props} selectedFiles={selection.asSet()} />
    </GallerySelection>
  );
};
