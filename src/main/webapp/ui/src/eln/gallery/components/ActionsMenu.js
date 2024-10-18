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
import FileUploadIcon from "@mui/icons-material/FileUpload";
import GroupIcon from "@mui/icons-material/Group";
import EditIcon from "@mui/icons-material/Edit";
import FolderOpenIcon from "@mui/icons-material/FolderOpen";
import { observer } from "mobx-react-lite";
import { computed } from "mobx";
import { type GalleryFile, idToString, type Id } from "../useGalleryListing";
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
import * as Parsers from "../../../util/parsers";
import * as FetchingData from "../../../util/fetchingData";
import * as ArrayUtils from "../../../util/ArrayUtils";
import {
  useOpen,
  useCollaboraEdit,
  useOfficeOnlineEdit,
} from "../primaryActionHooks";

/**
 * When tapped, the user is presented with their operating system's file
 * picker. Once they have picked a file its contents is uploaded and is used to
 * replace the contents of the selected gallery file. The filename is also
 * replaced and the version number incremented.
 */
const UploadNewVersionMenuItem = ({
  onSuccess,
  onError,
  folderId,
}: {|
  /*
   * Called when the selected local file has been uploaded and has successfully
   * replaced the contents of the gallery file.
   */
  onSuccess: () => void,

  /*
   * Called when either there is an error uploading the file or the user has
   * cancelled the operating system's file picker.
   */
  onError: () => void,

  /*
   * The current folder being shown in the UI. It's not clear why this is
   * necessary given that the Id of the selected file should be sufficient to
   * identify it, but the API requires it so pass it we must.
   */
  folderId: FetchingData.Fetched<Id>,
|}) => {
  const { uploadNewVersion } = useGalleryActions();
  const selection = useGallerySelection();
  const newVersionInputRef = React.useRef<HTMLInputElement | null>(null);

  /*
   * This is necessary because React does not yet support the new cancel event
   * https://developer.mozilla.org/en-US/docs/Web/API/HTMLElement/cancel_event
   * https://github.com/facebook/react/issues/27858
   */
  React.useEffect(() => {
    const input = newVersionInputRef.current;
    input?.addEventListener("cancel", onError);
    return () => input?.removeEventListener("cancel", onError);
  }, [newVersionInputRef, onError]);

  const uploadNewVersionAllowed = (): Result<null> => {
    return selection
      .asSet()
      .only.toResult(
        () =>
          new Error("Only one item may be updated with a new version at once.")
      )
      .flatMap((file) => {
        if (file.isFolder)
          return Result.Error([
            new Error("Cannot update folders with a new version."),
          ]);
        if (!file.extension)
          return Result.Error([
            new Error(
              "An extension is required to be able to update the file with a new version"
            ),
          ]);
        return Result.Ok(null);
      });
  };

  return (
    <>
      <NewMenuItem
        title="Upload New Version"
        subheader={uploadNewVersionAllowed()
          .map(() => "")
          .orElseGet(([e]) => e.message)}
        avatar={<FileUploadIcon />}
        backgroundColor={COLOR.background}
        foregroundColor={COLOR.contrastText}
        onKeyDown={(e: KeyboardEvent) => {
          if (e.key === " ") newVersionInputRef.current?.click();
        }}
        onClick={() => {
          newVersionInputRef.current?.click();
        }}
        compact
        disabled={uploadNewVersionAllowed().isError}
      />
      {selection
        .asSet()
        .only.map((file) => [file, file.extension])
        .flatMap(([f, ext]) =>
          Parsers.isNotNull(ext)
            .toOptional()
            .map((e) => [f, e])
        )
        .map(([file, extension]) => (
          <input
            key={null}
            ref={newVersionInputRef}
            accept={`.${extension}`}
            hidden
            onChange={({ target: { files } }) => {
              /*
               * In grid view, the id of the last file in the path and the
               * folderId will always be the same as one can only operate on
               * the contents of the currently open folder but in tree view
               * it is possible to operate on files that are deeply nested.
               * In either case, if the path is empty it is because a root
               * level file is being operated on, and the only way that is
               * possible is if the folderId is the id of the root of the
               * gallery sub-section. The only time that folderId will not
               * be available is whilst the listing is still loading or
               * there has been an error so we can just also error here.
               */
              const idOfFolderThatFileIsIn = ArrayUtils.last(file.path)
                .map(({ id }) => id)
                .orElseTry(() => FetchingData.getSuccessValue(folderId))
                .mapError(() => new Error("Current folder is not known"))
                .elseThrow();

              /*
               * `multiple` is not set on the `<input>` so we need not check
               * that multiple files have been selected; the OS will prevent
               * it.
               */
              const newFile = ArrayUtils.head(files)
                .mapError(() => new Error("No files selected"))
                .elseThrow();

              void uploadNewVersion(idOfFolderThatFileIsIn, file, newFile)
                .then(onSuccess)
                .catch(onError);
            }}
            type="file"
          />
        ))
        .orElse(null)}
    </>
  );
};

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
  folderId: FetchingData.Fetched<Id>,
|};

function ActionsMenu({
  refreshListing,
  section,
  folderId,
}: ActionsMenuArgs): Node {
  const [actionsMenuAnchorEl, setActionsMenuAnchorEl] = React.useState(null);
  const { deleteFiles, duplicateFiles } = useGalleryActions();
  const selection = useGallerySelection();
  const theme = useTheme();
  const canOpenAsFolder = useOpen();
  const canEditWithCollabora = useCollaboraEdit();
  const canEditWithOfficeOnline = useOfficeOnlineEdit();

  const [renameOpen, setRenameOpen] = React.useState(false);
  const [moveOpen, setMoveOpen] = React.useState(false);
  const [irodsOpen, setIrodsOpen] = React.useState(false);
  const [exportOpen, setExportOpen] = React.useState(false);

  const openAllowed = computed(() => {
    return selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMap(canOpenAsFolder);
  });

  const duplicateAllowed = (): Result<null> => {
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot duplicate system folders.")]);
    return Result.Ok(null);
  };

  const deleteAllowed = (): Result<null> => {
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot delete system folders.")]);
    return Result.Ok(null);
  };

  const renameAllowed = (): Result<null> => {
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
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot move system folders to iRODS.")]);
    return Result.Ok(null);
  };

  const sharingSnippetsAllowed = (): Result<null> => {
    if (selection.asSet().some((f) => !f.isSnippet))
      return Result.Error([new Error("Only snippets may be shared.")]);
    return Result.Error([new Error("Not yet available.")]);
  };

  const editingAllowed = computed(() =>
    selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMap((file) => {
        if (file.isImage)
          return Result.Error<null>([new Error("Not yet implemented.")]);
        return canEditWithCollabora(file)
          .orElseTry(() => canEditWithOfficeOnline(file))
          .map(() => Result.Error<null>([new Error("Not yet implemented.")]))
          .orElseGet(() =>
            Result.Error<null>([new Error("Cannot edit this item.")])
          );
      })
  );

  const exportAllowed = (): Result<null> => {
    return Result.Ok(null);
  };

  const downloadAllowed = (): Result<null> => {
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
    return Result.Ok(null);
  };

  return (
    <>
      <Button
        variant="contained"
        color="callToAction"
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
          title="Open"
          subheader={openAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<FolderOpenIcon />}
          onClick={() => {
            openAllowed.get().do((open) => {
              open();
              setActionsMenuAnchorEl(null);
            });
          }}
          disabled={openAllowed.get().isError}
          compact
        />
        <NewMenuItem
          title="Edit"
          subheader={editingAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<EditIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled={editingAllowed.get().isError}
        />
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
        <UploadNewVersionMenuItem
          folderId={folderId}
          onSuccess={() => {
            setActionsMenuAnchorEl(null);
            refreshListing();
          }}
          onError={() => {
            setActionsMenuAnchorEl(null);
          }}
        />
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
          display: {
            /*
             * On medium viewports, there isn't enought horizontal space to
             * display the actions menu, this selection label, the views
             * menu, and the sort menu in the picker dialog so we hide the
             * least important. On small viewports there is enough space
             * because the info panel is instead a panel that slides up
             * from the bottom. Once v6 of MUI is stable, it might be worth
             * changing this to a container query rather than using the page
             * width as a rough heuristic for available space. See
             * https://mui.com/system/getting-started/usage/#responsive-values
             */
            xs: "none",
            sm: "initial",
            md: "none",
            lg: "initial",
            xl: "initial",
          },
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
