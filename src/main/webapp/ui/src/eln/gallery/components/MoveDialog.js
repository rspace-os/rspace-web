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
import TextField from "@mui/material/TextField";
import InputAdornment from "@mui/material/InputAdornment";
import Grid from "@mui/material/Grid";
import TreeView from "./TreeView";
import { useGalleryListing, type GalleryFile } from "../useGalleryListing";
import * as FetchingData from "../../../util/fetchingData";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";
import { useGalleryActions, rootDestination } from "../useGalleryActions";
import RsSet from "../../../util/set";
import useViewportDimensions from "../../../util/useViewportDimensions";

type MoveDialogArgs = {|
  open: boolean,
  onClose: () => void,
  section: string,
  selectedFiles: RsSet<GalleryFile>,
  refreshListing: () => void,
|};

const MoveDialog = ({
  open,
  onClose,
  section,
  selectedFiles,
  refreshListing,
}: MoveDialogArgs): Node => {
  const viewport = useViewportDimensions();
  const { galleryListing } = useGalleryListing({
    section,
    searchTerm: "",
    path: [],
    orderBy: "name",
    sortOrder: "ASC",
  });
  const { moveFiles } = useGalleryActions();
  const [pathString, setPathString] = React.useState("");
  const selection = useGallerySelection({
    onChange: (sel) => {
      sel.asSet().only.do((file) => {
        setPathString(file.pathAsString());
      });
    },
  });

  React.useEffect(() => {
    if (!open) setPathString("");
  }, [open]);

  const [topLevelLoading, setTopLevelLoading] = React.useState(false);
  const [submitLoading, setSubmitLoading] = React.useState(false);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      scroll="paper"
      fullScreen={viewport.isViewportVerySmall}
    >
      <DialogTitle>Move</DialogTitle>
      <DialogContent sx={{ overflow: "hidden", flexGrow: 0 }}>
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
                selectedSection={section}
                refreshListing={refreshListing}
                filter={(file) => file.isFolder && !file.isSnippetFolder}
                disableDragAndDrop
                sortOrder="ASC"
                orderBy="name"
              />
            </Box>
          ),
        })}
      </DialogContent>
      <DialogActions>
        <Grid container spacing={1} direction="column">
          <Grid item>
            <TextField
              value={pathString}
              onChange={({ target: { value } }) => {
                setPathString(value);
              }}
              fullWidth
              size="small"
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">Path</InputAdornment>
                ),
              }}
              placeholder="Tap a folder or type a path here"
            />
          </Grid>
          <Grid item>
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
                      refreshListing();
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
                  void moveFiles(selectedFiles)
                    .toDestinationWithPath(section, pathString)
                    .then(() => {
                      setSubmitLoading(false);
                      refreshListing();
                      onClose();
                    });
                }}
                validationResult={(() => {
                  const files = selection.asSet();
                  if (files.isEmpty && pathString === "")
                    return Result.Error([new Error("No folder is selected.")]);
                  if (files.size > 1)
                    return Result.Error([
                      new Error("More than one folder is selected."),
                    ]);
                  return Result.Ok(null);
                })()}
              >
                Move
              </ValidatingSubmitButton>
            </Stack>
          </Grid>
        </Grid>
      </DialogActions>
    </Dialog>
  );
};

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
