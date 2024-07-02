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
import TreeView from "./TreeView";
import { useGalleryListing, type GalleryFile } from "../useGalleryListing";
import * as FetchingData from "../../../util/fetchingData";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import { observer } from "mobx-react-lite";
import {
  useGalleryActions,
  rootDestination,
  folderDestination,
} from "../useGalleryActions";
import RsSet from "../../../util/set";

type MoveDialogArgs = {|
  open: boolean,
  onClose: () => void,
  section: string,
  selectedFiles: RsSet<GalleryFile>,
  refreshListing: () => void,
|};

const MoveDialog = observer(
  ({
    open,
    onClose,
    section,
    selectedFiles,
    refreshListing,
  }: MoveDialogArgs): Node => {
    const { galleryListing } = useGalleryListing({
      section,
      searchTerm: "",
      path: [],
    });
    const { moveFiles } = useGalleryActions();
    const selection = useGallerySelection();

    return (
      <Dialog
        open={open}
        onClose={onClose}
        onKeyDown={(e) => {
          e.stopPropagation();
        }}
        scroll="paper"
      >
        <DialogTitle>Move</DialogTitle>
        <DialogContent sx={{ overflow: "hidden" }}>
          <DialogContentText variant="body2">
            Choose a folder, enter a path, or tap the &quot;top-level&quot;
            button.
          </DialogContentText>
        </DialogContent>
        <DialogContent sx={{ pt: 0 }}>
          {FetchingData.match(galleryListing, {
            loading: () => <></>,
            error: (error) => <>{error}</>,
            success: (listing) => (
              <Box sx={{ overflowY: "auto" }}>
                <TreeView
                  listing={listing}
                  path={[]}
                  selectedSection="images"
                  refreshListing={refreshListing}
                  filter={(file) => file.isFolder && !file.isSnippetFolder}
                />
              </Box>
            ),
          })}
        </DialogContent>
        <DialogActions>
          <SubmitSpinnerButton
            onClick={() => {
              void moveFiles(selectedFiles)
                .to({
                  destination: rootDestination(),
                  section,
                })
                .then(() => {
                  refreshListing();
                  onClose();
                });
            }}
            disabled={false}
            loading={false}
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
            loading={false}
            onClick={() => {
              const folder = selection.asSet().only.orElseGet(() => {
                throw new Error("More than one folder is selected");
              });
              void moveFiles(selectedFiles)
                .to({
                  destination: folderDestination(folder),
                  section,
                })
                .then(() => {
                  refreshListing();
                  onClose();
                });
            }}
            validationResult={Result.Ok(null)}
          >
            Move
          </ValidatingSubmitButton>
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
    <GallerySelection>
      <MoveDialog {...props} selectedFiles={selection.asSet()} />
    </GallerySelection>
  );
};
