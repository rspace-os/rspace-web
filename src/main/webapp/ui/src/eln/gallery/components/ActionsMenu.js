//@flow

import React, { type Node, type ComponentType } from "react";
import ChecklistIcon from "@mui/icons-material/Checklist";
import Button from "@mui/material/Button";
import { COLOR, type GallerySection } from "../common";
import AddToPhotosIcon from "@mui/icons-material/AddToPhotos";
import { styled, useTheme, darken, lighten } from "@mui/material/styles";
import Menu from "@mui/material/Menu";
import NewMenuItem from "./NewMenuItem";
import OpenWithIcon from "@mui/icons-material/OpenWith";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import GroupIcon from "@mui/icons-material/Group";
import CropIcon from "@mui/icons-material/Crop";
import { observer } from "mobx-react-lite";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { useGalleryActions } from "../useGalleryActions";
import { useGallerySelection } from "../useGallerySelection";
import Dialog from "@mui/material/Dialog";
import Divider from "@mui/material/Divider";
import TextField from "@mui/material/TextField";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogActions from "@mui/material/DialogActions";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import Result from "../../../util/result";
import MoveToIrods, { COLOR as IRODS_COLOR } from "./MoveToIrods";
import IrodsLogo from "./IrodsLogo.svg";
import Avatar from "@mui/material/Avatar";
import Typography from "@mui/material/Typography";
import MoveDialog from "./MoveDialog";
import ExportDialog from "../../../Export/ExportDialog";
import EventBoundary from "../../../components/EventBoundary";

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
    <Dialog
      open={open}
      onClose={onClose}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
    >
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
    minWidth: "212px",
    ...(open
      ? {
          transform: "translate(0px, 4px) !important",
        }
      : {}),
  },
}));

type ActionsMenuArgs = {|
  refreshListing: () => void,
  section: GallerySection,
|};

function ActionsMenu({ refreshListing, section }: ActionsMenuArgs): Node {
  const [actionsMenuAnchorEl, setActionsMenuAnchorEl] = React.useState(null);
  const { deleteFiles, duplicateFiles } = useGalleryActions();
  const selection = useGallerySelection();
  const theme = useTheme();

  const [renameOpen, setRenameOpen] = React.useState(false);
  const [moveOpen, setMoveOpen] = React.useState(false);
  const [irodsOpen, setIrodsOpen] = React.useState(false);
  const [exportOpen, setExportOpen] = React.useState(false);

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

  const renameAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    return selection
      .asSet()
      .only.toResult(() => new Error("Only one item may be renamed at once."))
      .flatMap((file) => {
        if (file.isSystemFolder)
          return Result.Error([new Error("Cannot rename system folders.")]);
        return Result.Ok(null);
      });
  };

  const moveToIrodsAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot move system folders to iRODS.")]);
    return Result.Ok(null);
  };

  const sharingSnippetsAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    if (selection.asSet().some((f) => !f.isSnippet))
      return Result.Error([new Error("Only snippets may be shared.")]);
    return Result.Error([new Error("Not yet available.")]);
  };

  const imageEditingAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    if (selection.asSet().some((f) => !f.isImage))
      return Result.Error([new Error("Only images may be edited.")]);
    return Result.Error([new Error("Not yet available.")]);
  };

  const exportAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    return Result.Ok(null);
  };

  const downloadAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    return selection
      .asSet()
      .only.toResult(
        () => new Error("Only one item may be downloaded at once.")
      )
      .flatMap((file) => {
        if (file.isFolder)
          return Result.Error([new Error("Cannot download folders.")]);
        return Result.Ok(null);
      });
  };

  const moveAllowed = (): Result<null> => {
    if (selection.isEmpty)
      return Result.Error([new Error("Nothing selected.")]);
    return Result.Ok(null);
  };

  return (
    <>
      <Button
        variant="contained"
        color="primary"
        size="small"
        disabled={selection.isEmpty}
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
        keepMounted
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
          subheader={moveAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<OpenWithIcon />}
          onClick={() => {
            setMoveOpen(true);
          }}
          compact
          disabled={moveAllowed().isError}
          aria-haspopup="dialog"
        />
        <MoveDialog
          open={moveOpen}
          onClose={() => {
            setMoveOpen(false);
            setActionsMenuAnchorEl(null);
          }}
          section={section}
          refreshListing={refreshListing}
        />
        <NewMenuItem
          title="Rename"
          subheader={renameAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<DriveFileRenameOutlineIcon />}
          onClick={() => {
            setRenameOpen(true);
          }}
          compact
          disabled={renameAllowed().isError}
          aria-haspopup="dialog"
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
          title="Download"
          subheader={downloadAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<FileDownloadIcon />}
          onClick={() => {
            selection.asSet().only.do((file) => {
              window.open(file.downloadHref);
            });
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled={downloadAllowed().isError}
        />
        <NewMenuItem
          title="Export"
          subheader={exportAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<FileDownloadIcon />}
          onClick={() => {
            setExportOpen(true);
          }}
          compact
          disabled={exportAllowed().isError}
        />
        <EventBoundary>
          <ExportDialog
            open={exportOpen}
            onClose={() => {
              setExportOpen(false);
              setActionsMenuAnchorEl(null);
            }}
            exportSelection={{
              type: "selection",
              exportTypes: selection
                .asSet()
                .map(() => "MEDIA_FILE")
                .toArray(),
              exportNames: selection
                .asSet()
                .map(({ name }) => name)
                .toArray(),
              exportIds: selection
                .asSet()
                .map(({ id }) => idToString(id))
                .toArray(),
            }}
            allowFileStores={false}
          />
        </EventBoundary>
        <NewMenuItem
          title="Edit"
          subheader={imageEditingAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<CropIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled={imageEditingAllowed().isError}
        />
        <NewMenuItem
          title="Share"
          subheader={sharingSnippetsAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<GroupIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled={sharingSnippetsAllowed().isError}
        />
        <NewMenuItem
          title="Move to iRODS"
          subheader={moveToIrodsAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={IRODS_COLOR.background}
          foregroundColor={IRODS_COLOR.contrastText}
          avatar={
            <Avatar
              variant="square"
              sx={{
                width: 28,
                height: 28,
                bgcolor: `hsl(${IRODS_COLOR.main.hue}deg, ${IRODS_COLOR.main.saturation}%, ${IRODS_COLOR.main.lightness}%, 100%)`,
                border: `4px solid hsl(${IRODS_COLOR.main.hue}deg, ${IRODS_COLOR.main.saturation}%, ${IRODS_COLOR.main.lightness}%, 100%)`,
              }}
              src={IrodsLogo}
            />
          }
          onClick={() => {
            setIrodsOpen(true);
          }}
          compact
          disabled={moveToIrodsAllowed().isError}
          aria-haspopup="dialog"
        />
        <MoveToIrods
          selectedIds={selection
            .asSet()
            .map(({ id }) => idToString(id))
            .toArray()}
          dialogOpen={irodsOpen}
          setDialogOpen={(newState) => {
            setIrodsOpen(newState);
            if (!newState) {
              setActionsMenuAnchorEl(null);
              refreshListing();
            }
          }}
        />
        <Divider aria-orientation="horizontal" />
        <NewMenuItem
          title="Delete"
          subheader={deleteAllowed()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={lighten(theme.palette.error.light, 0.5)}
          foregroundColor={darken(theme.palette.error.dark, 0.3)}
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
      </StyledMenu>
      <Typography
        variant="body2"
        sx={{
          p: 0,
          pl: 1,
          fontWeight: 500,
          display: { xs: "none", sm: "initial" },
          ...(selection.isEmpty
            ? {
                color: "grey",
              }
            : {}),
        }}
      >
        {selection.label}
      </Typography>
    </>
  );
}

export default (observer(ActionsMenu): ComponentType<ActionsMenuArgs>);
