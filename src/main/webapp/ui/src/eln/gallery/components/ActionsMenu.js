//@flow

import React, { type Node, type ComponentType } from "react";
import ChecklistIcon from "@mui/icons-material/Checklist";
import Button from "@mui/material/Button";
import { COLOR } from "../common";
import AddToPhotosIcon from "@mui/icons-material/AddToPhotos";
import { styled } from "@mui/material/styles";
import Menu from "@mui/material/Menu";
import NewMenuItem from "./NewMenuItem";
import OpenWithIcon from "@mui/icons-material/OpenWith";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import AcUnitIcon from "@mui/icons-material/AcUnit";
import GroupIcon from "@mui/icons-material/Group";
import CropIcon from "@mui/icons-material/Crop";
import { observer } from "mobx-react-lite";
import { type GalleryFile } from "../useGalleryListing";
import { useGalleryActions } from "../useGalleryActions";
import { useGallerySelection } from "../useGallerySelection";
import Dialog from "@mui/material/Dialog";
import TextField from "@mui/material/TextField";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogActions from "@mui/material/DialogActions";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import { match } from "../../../util/Util";
import Result from "../../../util/result";

const RenameDialog = ({
  open,
  onClose,
  file,
}: {|
  open: boolean,
  onClose: () => void,
  file: GalleryFile,
|}) => {
  const [newName, setNewName] = React.useState("");
  const { rename } = useGalleryActions();
  return (
    <Dialog open={open} onClose={onClose}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void rename(file, newName).then(() => {
            onClose();
          });
        }}
      >
        <DialogTitle>Rename</DialogTitle>
        <DialogContent>
          <DialogContentText variant="body2" sx={{ mb: 2 }}>
            Please give a new name for <strong>{file.name}</strong>
          </DialogContentText>
          <TextField
            size="small"
            label="Name"
            onChange={({ target: { value } }) => setNewName(value)}
          />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setNewName("");
              onClose();
            }}
          >
            Cancel
          </Button>
          <ValidatingSubmitButton
            loading={false}
            onClick={() => {
              void rename(file, newName).then(() => {
                onClose();
              });
            }}
            validationResult={
              newName.length === 0
                ? Result.Error([new Error("Empty name is not permitted.")])
                : Result.Ok(null)
            }
          >
            Rename
          </ValidatingSubmitButton>
        </DialogActions>
      </form>
    </Dialog>
  );
};

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(0px, 4px) !important",
        }
      : {}),
  },
}));

type ActionsMenuArgs = {|
  refreshListing: () => void,
|};

function ActionsMenu({ refreshListing }: ActionsMenuArgs): Node {
  const [actionsMenuAnchorEl, setActionsMenuAnchorEl] = React.useState(null);
  const { deleteFiles, duplicateFiles } = useGalleryActions();
  const selection = useGallerySelection();

  const [renameOpen, setRenameOpen] = React.useState(false);

  const duplicateAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot duplicate system folders.")]);
    return Result.Ok(null);
  };

  const deleteAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot delete system folders.")]);
    return Result.Ok(null);
  };

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        aria-haspopup="menu"
        startIcon={<ChecklistIcon />}
        onClick={(e) => {
          setActionsMenuAnchorEl(e.target);
        }}
      >
        Actions
      </Button>
      <StyledMenu
        open={Boolean(actionsMenuAnchorEl)}
        anchorEl={actionsMenuAnchorEl}
        onClose={() => setActionsMenuAnchorEl(null)}
        MenuListProps={{
          disablePadding: true,
        }}
      >
        <NewMenuItem
          title="Duplicate"
          subheader={duplicateAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<AddToPhotosIcon />}
          onClick={() => {
            void duplicateFiles(selection.asSet()).then(() => {
              refreshListing();
              setActionsMenuAnchorEl(null);
            });
          }}
          compact
          disabled={duplicateAllowed().isError}
        />
        <NewMenuItem
          title="Move"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<OpenWithIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Rename"
          subheader={match<void, string>([
            [() => selection.isEmpty, "Nothing selected."],
            [() => selection.size > 1, "Only item may be renamed at once."],
            [() => true, ""],
          ])()}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<DriveFileRenameOutlineIcon />}
          onClick={() => {
            setRenameOpen(true);
          }}
          compact
          disabled={selection.size !== 1}
        />
        {selection
          .asSet()
          .only.map((file) => (
            <RenameDialog
              key={null}
              open={renameOpen}
              onClose={() => {
                setRenameOpen(false);
                setActionsMenuAnchorEl(null);
                refreshListing();
              }}
              file={file}
            />
          ))
          .orElse(null)}
        <NewMenuItem
          title="Delete"
          subheader={deleteAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<DeleteOutlineOutlinedIcon />}
          onClick={() => {
            void deleteFiles(selection.asSet()).then(() => {
              refreshListing();
              setActionsMenuAnchorEl(null);
            });
          }}
          compact
          disabled={deleteAllowed().isError}
        />
        <NewMenuItem
          title="Export"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<FileDownloadIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Move to iRODS"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          // TODO: iRODS logo
          avatar={<AcUnitIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Edit"
          subheader="Only images can be edited in place."
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<CropIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled
        />
        <NewMenuItem
          title="Share"
          subheader="Only snippets can be shared."
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<GroupIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled
        />
      </StyledMenu>
    </>
  );
}

export default (observer(ActionsMenu): ComponentType<ActionsMenuArgs>);
