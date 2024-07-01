//@flow

import React, { type Node, type ComponentType } from "react";
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

type MoveDialogArgs = {|
  open: boolean,
  onClose: () => void,
|};

export default function MoveDialog({ open, onClose }: MoveDialogArgs): Node {
  const { galleryListing, path, clearPath, folderId, refreshListing } =
    useGalleryListing({
      section: "images",
      searchTerm: "",
      path: [],
    });

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
                filter={(file) => file.isFolder}
              />
            </Box>
          ),
        })}
      </DialogContent>
      <DialogActions>
        <SubmitSpinnerButton
          onClick={() => {}}
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
            // do move
          }}
          validationResult={Result.Ok(null)}
        >
          Move
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
}
