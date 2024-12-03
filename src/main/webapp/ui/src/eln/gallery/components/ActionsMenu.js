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
import EditIcon from "@mui/icons-material/Edit";
import FolderOpenIcon from "@mui/icons-material/FolderOpen";
import VisibilityIcon from "@mui/icons-material/Visibility";
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
import Typography from "@mui/material/Typography";
import MoveDialog from "./MoveDialog";
import ExportDialog from "../../../Export/ExportDialog";
import EventBoundary from "../../../components/EventBoundary";
import * as Parsers from "../../../util/parsers";
import * as FetchingData from "../../../util/fetchingData";
import * as ArrayUtils from "../../../util/ArrayUtils";
import {
  useOpen,
  useImagePreviewOfGalleryFile,
  useCollaboraEdit,
  useOfficeOnlineEdit,
  usePdfPreviewOfGalleryFile,
  useAsposePreviewOfGalleryFile,
} from "../primaryActionHooks";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useAsposePreview } from "./CallableAsposePreview";
import axios from "axios";
import ImageEditingDialog from "../../../components/ImageEditingDialog";
import { doNotAwait } from "../../../util/Util";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import CardMedia from "@mui/material/CardMedia";

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

  const uploadNewVersionAllowed = computed((): Result<null> => {
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
  });

  return (
    <>
      <NewMenuItem
        title="Upload New Version"
        subheader={uploadNewVersionAllowed
          .get()
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
        disabled={uploadNewVersionAllowed.get().isError}
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
  refreshListing: () => Promise<void>,
  section: GallerySection,
  folderId: FetchingData.Fetched<Id>,
|};

function ActionsMenu({
  refreshListing,
  section,
  folderId,
}: ActionsMenuArgs): Node {
  const [actionsMenuAnchorEl, setActionsMenuAnchorEl] = React.useState(null);
  const { deleteFiles, duplicateFiles, uploadFiles } = useGalleryActions();
  const selection = useGallerySelection();
  const theme = useTheme();
  const { addAlert } = React.useContext(AlertContext);
  const canOpenAsFolder = useOpen();
  const canPreviewAsImage = useImagePreviewOfGalleryFile();
  const canEditWithCollabora = useCollaboraEdit();
  const canEditWithOfficeOnline = useOfficeOnlineEdit();
  const canPreviewAsPdf = usePdfPreviewOfGalleryFile();
  const canPreviewWithAspose = useAsposePreviewOfGalleryFile();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreview } = useAsposePreview();

  const [renameOpen, setRenameOpen] = React.useState(false);
  const [moveOpen, setMoveOpen] = React.useState(false);
  const [irodsOpen, setIrodsOpen] = React.useState(false);
  const [exportOpen, setExportOpen] = React.useState(false);
  const [imageEditorBlob, setImageEditorBlob] = React.useState<null | Blob>(
    null
  );

  const openAllowed = computed(() => {
    return selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMap(canOpenAsFolder);
  });

  const editingAllowed = computed(() =>
    selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMap<
        | {| key: "image", downloadHref: ?string |}
        | {| key: "document", url: string |}
      >((file) => {
        if (file.isImage)
          return Result.Ok({ key: "image", downloadHref: file.downloadHref });
        return canEditWithCollabora(file)
          .orElseTry(() => canEditWithOfficeOnline(file))
          .map<
            Result<
              | {| key: "image", downloadHref: ?string |}
              | {| key: "document", url: string |}
            >
          >((url) =>
            Result.Ok({
              key: "document",
              url,
            })
          )
          .orElseGet(() =>
            Result.Error<_>([new Error("Cannot edit this item.")])
          );
      })
  );

  const viewHidden = computed(() =>
    selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .map((file) => canOpenAsFolder(file).isOk)
      .orElse(false)
  );

  const viewAllowed = computed(() =>
    selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMap((file) =>
        canPreviewAsImage(file)
          .map((downloadHref) => ({
            key: "image",
            downloadHref,
            caption: [
              file.description.match({
                missing: () => "",
                empty: () => "",
                present: (desc) => desc,
              }),
              file.name,
            ],
          }))
          .orElseTry(() =>
            canPreviewAsPdf(file).map((downloadHref) => ({
              key: "pdf",
              downloadHref,
            }))
          )
          .orElseTry(() =>
            canPreviewWithAspose(file).map(() => ({ key: "aspose", file }))
          )
          .mapError(() => new Error("Cannot view this item."))
      )
  );

  const duplicateAllowed = computed((): Result<null> => {
    if (selection.size > 50)
      return Result.Error([
        new Error("Cannot duplicate more than 50 items at once."),
      ]);
    return Result.all(...selection.asSet().map((f) => f.canDuplicate)).map(
      () => null
    );
  });

  const deleteAllowed = computed((): Result<null> => {
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot delete system folders.")]);
    if (selection.size > 50)
      return Result.Error([
        new Error("Cannot delete more than 50 items at once."),
      ]);
    return Result.Ok(null);
  });

  const renameAllowed = computed((): Result<null> => {
    return selection
      .asSet()
      .only.toResult(() => new Error("Only one item may be renamed at once."))
      .flatMap((file) => {
        if (file.isSystemFolder)
          return Result.Error([new Error("Cannot rename system folders.")]);
        return Result.Ok(null);
      });
  });

  const moveToIrodsAllowed = computed((): Result<null> => {
    if (selection.asSet().some((f) => f.isSystemFolder))
      return Result.Error([new Error("Cannot move system folders to iRODS.")]);
    return Result.Ok(null);
  });

  const exportAllowed = computed((): Result<null> => {
    if (selection.size > 100)
      return Result.Error([
        new Error("Cannot export more than 100 itemes at once."),
      ]);
    return Result.Ok(null);
  });

  const downloadAllowed = computed((): Result<null> => {
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
  });

  const moveAllowed = computed((): Result<null> => {
    if (selection.size > 50)
      return Result.Error([
        new Error("Cannot move more than 50 items at once."),
      ]);
    return Result.Ok(null);
  });

  return (
    <>
      <Button
        variant="contained"
        color="callToAction"
        size="small"
        disabled={selection.isEmpty}
        aria-haspopup="menu"
        aria-expanded={actionsMenuAnchorEl ? "true" : "false"}
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
          "aria-label": "actions",
        }}
        /*
         * We don't use `keepMounted` here as otherwise every time the user
         * changes the selection the menu would have to re-render. The response
         * time for the UI to update after a selection change is already pretty
         * long (>250ms) as the listing and info panel have to be completely
         * re-rendered and so keeping the menu out of the DOM whenever possible
         * helps in keeping the user interface as responsive as possible.
         */
      >
        {openAllowed
          .get()
          .map((open) => (
            <NewMenuItem
              title="Open"
              backgroundColor={COLOR.background}
              foregroundColor={COLOR.contrastText}
              avatar={<FolderOpenIcon />}
              onClick={() => {
                open();
                setActionsMenuAnchorEl(null);
              }}
              compact
              key="open"
            />
          ))
          .orElse(null)}
        {!viewHidden.get() && (
          <NewMenuItem
            title="View"
            subheader={viewAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            backgroundColor={COLOR.background}
            foregroundColor={COLOR.contrastText}
            avatar={<VisibilityIcon />}
            onClick={() => {
              viewAllowed.get().do((viewAction) => {
                if (viewAction.key === "image")
                  openImagePreview(viewAction.downloadHref, {
                    caption: viewAction.caption,
                  });
                if (viewAction.key === "pdf")
                  openPdfPreview(viewAction.downloadHref);
                if (viewAction.key === "aspose")
                  void openAsposePreview(viewAction.file);
              });
              setActionsMenuAnchorEl(null);
            }}
            compact
            disabled={viewAllowed.get().isError}
          />
        )}
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
            editingAllowed.get().do((action) => {
              if (action.key === "document") window.open(action.url);
              if (action.key === "image") {
                if (!action.downloadHref) return;
                void axios
                  .get<Blob>(action.downloadHref, {
                    responseType: "blob",
                  })
                  .then(({ data }) => {
                    setImageEditorBlob(data);
                  });
              }
            });
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled={editingAllowed.get().isError}
        />
        <NewMenuItem
          title="Duplicate"
          subheader={duplicateAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<AddToPhotosIcon />}
          onClick={() => {
            void duplicateFiles(selection.asSet()).then(() => {
              void refreshListing();
              setActionsMenuAnchorEl(null);
            });
          }}
          compact
          disabled={duplicateAllowed.get().isError}
        />
        <NewMenuItem
          title="Move"
          subheader={moveAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<OpenWithIcon />}
          onClick={() => {
            setMoveOpen(true);
          }}
          compact
          disabled={moveAllowed.get().isError}
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
          subheader={renameAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<DriveFileRenameOutlineIcon />}
          onClick={() => {
            setRenameOpen(true);
          }}
          compact
          disabled={renameAllowed.get().isError}
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
                void refreshListing();
              }}
              file={file}
            />
          ))
          .orElse(null)}
        <UploadNewVersionMenuItem
          folderId={folderId}
          onSuccess={() => {
            setActionsMenuAnchorEl(null);
            void refreshListing();
          }}
          onError={() => {
            setActionsMenuAnchorEl(null);
          }}
        />
        <NewMenuItem
          title="Download"
          subheader={downloadAllowed
            .get()
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
          disabled={downloadAllowed.get().isError}
        />
        <NewMenuItem
          title="Export"
          subheader={exportAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<FileDownloadIcon />}
          onClick={() => {
            setExportOpen(true);
          }}
          compact
          disabled={exportAllowed.get().isError}
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
                .toArray()
                .map(() => "MEDIA_FILE"),
              exportNames: selection
                .asSet()
                .toArray()
                .map(({ name }) => name),
              exportIds: selection
                .asSet()
                .toArray()
                .map(({ id }) => idToString(id)),
            }}
            allowFileStores={false}
          />
        </EventBoundary>
        <NewMenuItem
          title="Move to iRODS"
          subheader={moveToIrodsAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={IRODS_COLOR.background}
          foregroundColor={IRODS_COLOR.contrastText}
          avatar={<CardMedia image={IrodsLogo} />}
          onClick={() => {
            setIrodsOpen(true);
          }}
          compact
          disabled={moveToIrodsAllowed.get().isError}
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
              void refreshListing();
            }
          }}
        />
        <Divider aria-orientation="horizontal" />
        <NewMenuItem
          title="Delete"
          subheader={deleteAllowed
            .get()
            .map(() => "")
            .orElseGet(([e]) => e.message)}
          backgroundColor={lighten(theme.palette.error.light, 0.5)}
          foregroundColor={darken(theme.palette.error.dark, 0.3)}
          avatar={<DeleteOutlineOutlinedIcon />}
          onClick={() => {
            void deleteFiles(selection.asSet()).then(() => {
              void refreshListing();
              setActionsMenuAnchorEl(null);
            });
          }}
          compact
          disabled={deleteAllowed.get().isError}
        />
      </StyledMenu>
      <ImageEditingDialog
        imageFile={imageEditorBlob}
        open={imageEditorBlob !== null}
        close={() => {
          setImageEditorBlob(null);
        }}
        submitButtonLabel="Save as new image"
        submitHandler={doNotAwait(async (newBlob) => {
          try {
            const file = selection
              .asSet()
              .only.toResult(() => new Error("Nothing selected"))
              .elseThrow();
            const newFile = new File(
              [newBlob],
              file.transformFilename((name) => name + "_edited"),
              {
                type: newBlob.type,
              }
            );
            const idOfFolderThatFileIsIn = ArrayUtils.last(file.path)
              .map(({ id }) => id)
              .orElseTry(() => FetchingData.getSuccessValue(folderId))
              .mapError(() => new Error("Current folder is not known"))
              .elseThrow();
            await uploadFiles(file.path, idOfFolderThatFileIsIn, [newFile]);
            void refreshListing();
          } catch (e) {
            addAlert(
              mkAlert({
                variant: "error",
                title: "Failed to process edited image",
                message: e.message,
              })
            );
          } finally {
            setActionsMenuAnchorEl(null);
          }
        })}
        alt={selection
          .asSet()
          .only.map(
            (file) =>
              file.name +
              file.description.match({
                missing: () => "",
                empty: () => "",
                present: (d) => d,
              })
          )
          .orElse("")}
      />
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
