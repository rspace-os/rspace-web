import AddToPhotosIcon from "@mui/icons-material/AddToPhotos";
import ChecklistIcon from "@mui/icons-material/Checklist";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import EditIcon from "@mui/icons-material/Edit";
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import FileUploadIcon from "@mui/icons-material/FileUpload";
import FolderOpenIcon from "@mui/icons-material/FolderOpen";
import LogoutIcon from "@mui/icons-material/Logout";
import OpenWithIcon from "@mui/icons-material/OpenWith";
import ShareIcon from "@mui/icons-material/Share";
import VisibilityIcon from "@mui/icons-material/Visibility";
import Button, { buttonClasses } from "@mui/material/Button";
import CardMedia from "@mui/material/CardMedia";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import { darken, lighten, useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { computed } from "mobx";
import { observer } from "mobx-react-lite";
import React, { Suspense } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { Menu } from "@/components/DialogBoundary";
import { ShareDialog } from "@/components/ShareDialog";
import useWhoAmI from "@/hooks/api/useWhoAmI";
import { ACCENT_COLOR as IRODS_COLOR } from "../../../assets/branding/irods";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import AccentMenuItem from "../../../components/AccentMenuItem";
import { ConfirmationDialog } from "../../../components/ConfirmationDialog";
import EventBoundary from "../../../components/EventBoundary";
import ImageEditingDialog from "../../../components/ImageEditingDialog";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import ExportDialog from "../../../Export/ExportDialog";
import { useDeploymentProperty } from "../../../hooks/api/useDeploymentProperty";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import * as FetchingData from "../../../util/fetchingData";
import { Optional } from "../../../util/optional";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";
import type RsSet from "../../../util/set";
import type { URL } from "../../../util/types";
import type { GallerySection } from "../common";
import {
  useAsposePreviewOfGalleryFile,
  useCollaboraEdit,
  useImagePreviewOfGalleryFile,
  useOfficeOnlineEdit,
  usePdfPreviewOfGalleryFile,
  useShowSnippet,
  useSnapGenePreviewOfGalleryFile,
} from "../primaryActionHooks";
import useFilestoresEndpoint from "../useFilestoresEndpoint";
import { useGalleryActions } from "../useGalleryActions";
import {
  asWritableS3Filestore,
  Filestore,
  type GalleryFile,
  type Id,
  idToString,
  RemoteFile,
} from "../useGalleryListing";
import { useGallerySelection } from "../useGallerySelection";
import { useAsposePreview } from "./CallableAsposePreview";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useSnapGenePreview } from "./CallableSnapGenePreview";
import { useSnippetPreview } from "./CallableSnippetPreview";
import IrodsLogo from "./IrodsLogo.svg";
import MoveDialog from "./MoveDialog";
import MoveToIrods from "./MoveToIrods";
import MoveToS3, { type S3TransferSource } from "./MoveToS3";
import MoveWithinFilestoreDialog from "./MoveWithinFilestoreDialog";
import { useFolderOpen } from "./OpenFolderProvider";
import S3Logo from "./S3Logo.svg";

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
}: {
  /*
   * Called when the selected local file has been uploaded and has successfully
   * replaced the contents of the gallery file.
   */
  onSuccess: () => void;

  /*
   * Called when either there is an error uploading the file or the user has
   * cancelled the operating system's file picker.
   */
  onError: () => void;

  /*
   * The current folder being shown in the UI. It's not clear why this is
   * necessary given that the Id of the selected file should be sufficient to
   * identify it, but the API requires it so pass it we must.
   */
  folderId: FetchingData.Fetched<Id>;
}) => {
  const { uploadNewVersion } = useGalleryActions();
  const { trackEvent } = React.useContext(AnalyticsContext);
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
      .only.toResult(() => new Error("Only one item may be updated with a new version at once."))
      .flatMap((file) => file.canUploadNewVersion);
  });
  return (
    <>
      <AccentMenuItem
        title="Upload New Version"
        subheader={uploadNewVersionAllowed
          .get()
          .map(() => "")
          .orElseGet(([e]) => e.message)}
        avatar={<FileUploadIcon />}
        onKeyDown={(e: React.KeyboardEvent) => {
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
        .only.map<[GalleryFile, string | null]>((file) => [file, file.extension])
        .flatMap(([f, ext]) =>
          Parsers.isNotNull(ext)
            .toOptional()
            .map<[GalleryFile, string | null]>((e) => [f, e]),
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
              const idOfFolderThatFileIsIn = Result.fromNullable(
                file.path.at(-1),
                new Error("Current folder is not known"),
              )
                .map(({ id }) => id)
                .orElseTry(() => FetchingData.getSuccessValue(folderId))
                .mapError(() => new Error("Current folder is not known"))
                .elseThrow();

              /*
               * `multiple` is not set on the `<input>` so we need not check
               * that multiple files have been selected; the OS will prevent
               * it.
               */
              if (!files || files.length === 0) return;
              const newFile = Result.fromNullable(files.item(0), new Error("No files selected")).elseThrow();
              void uploadNewVersion(idOfFolderThatFileIsIn, file, newFile)
                .then(() => {
                  onSuccess();
                  trackEvent(
                    "user:uploads_new_version:file:gallery",
                    Optional.fromNullable(file.version)
                      .map((v) => ({
                        version: v + 1,
                      }))
                      .orElse({}),
                  );
                })
                .catch(onError);
            }}
            type="file"
          />
        ))
        .orElse(null)}
    </>
  );
};
const RenameDialog = ({ open, onClose, file }: { open: boolean; onClose: () => void; file: GalleryFile }) => {
  const [newName, setNewName] = React.useState("");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { rename } = useGalleryActions();
  const { t } = useTranslation("gallery");
  const { t: tCommon } = useTranslation("common");
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
        <DialogTitle>{t("actionsMenu.rename")}</DialogTitle>
        <DialogContent>
          <DialogContentText
            variant="body2"
            sx={{
              mb: 2,
            }}
          >
            {t("actionsMenu.renamePrompt", { name: file.name })}
          </DialogContentText>
          <TextField
            size="small"
            label={t("actionsMenu.renameLabel")}
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
            {tCommon("actions.cancel")}
          </Button>
          <ValidatingSubmitButton
            loading={false}
            onClick={() => {
              void rename(file, newName).then(() => {
                onClose();
                trackEvent("user:renames:file:gallery");
              });
            }}
            validationResult={
              newName.length === 0 ? Result.Error([new Error(t("actionsMenu.nameRequired"))]) : Result.Ok(null)
            }
          >
            {t("actionsMenu.rename")}
          </ValidatingSubmitButton>
        </DialogActions>
      </form>
    </Dialog>
  );
};
type ActionsMenuArgs = {
  refreshListing: () => Promise<void>;
  section: GallerySection | null;
  folderId: FetchingData.Fetched<Id>;
};
function ActionsMenu({ refreshListing, section, folderId }: ActionsMenuArgs): React.ReactNode {
  const [actionsMenuAnchorEl, setActionsMenuAnchorEl] = React.useState<HTMLElement | null>(null);
  const { deleteFiles, duplicateFiles, uploadFiles, download } = useGalleryActions();
  const selection = useGallerySelection();
  const theme = useTheme();
  const { addAlert } = React.useContext(AlertContext);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { t } = useTranslation("gallery");
  const canPreviewAsImage = useImagePreviewOfGalleryFile();
  const canEditWithCollabora = useCollaboraEdit();
  const canEditWithOfficeOnline = useOfficeOnlineEdit();
  const canPreviewAsPdf = usePdfPreviewOfGalleryFile();
  const canPreviewWithAspose = useAsposePreviewOfGalleryFile();
  const canPreviewWithSnapGene = useSnapGenePreviewOfGalleryFile();
  const canPreviewSnippet = useShowSnippet();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreview } = useAsposePreview();
  const { openSnapGenePreview } = useSnapGenePreview();
  const { openFolder } = useFolderOpen();
  const { openSnippetPreview } = useSnippetPreview();
  const fetchedCurrentUser = useWhoAmI();
  const netfilestoresEnabled = useDeploymentProperty("netfilestores.enabled");

  const currentUser = FetchingData.getSuccessValue(fetchedCurrentUser).orElse(null);

  const showNetfileActions = FetchingData.getSuccessValue(netfilestoresEnabled)
    .flatMap(Parsers.isBoolean)
    .flatMap(Parsers.isTrue)
    .orElse(false);

  const [renameOpen, setRenameOpen] = React.useState(false);
  const [moveOpen, setMoveOpen] = React.useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = React.useState(false);
  const [irodsOpen, setIrodsOpen] = React.useState(false);
  const [s3Open, setS3Open] = React.useState(false);
  const [exportOpen, setExportOpen] = React.useState(false);
  const [shareOpen, setShareOpen] = React.useState(false);
  const [imageEditorBlob, setImageEditorBlob] = React.useState<null | Blob>(null);
  const openAllowed = computed(() => {
    return selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMapDiscarding((f) => f.canOpen);
  });
  const editingAllowed = computed(() =>
    selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMap<
        | {
            key: "image";
            downloadHref: () => Promise<URL>;
          }
        | {
            key: "collabora";
            url: string;
          }
        | {
            key: "officeonline";
            url: string;
          }
      >((file) => {
        if (file.isImage && typeof file.downloadHref !== "undefined")
          return Result.Ok({
            key: "image" as const,
            downloadHref: file.downloadHref,
          });
        return canEditWithCollabora(file)
          .map((url) => ({
            key: "collabora" as const,
            url,
          }))
          .orElseTry(() =>
            canEditWithOfficeOnline(file).map<
              | {
                  key: "image";
                  downloadHref: () => Promise<URL>;
                }
              | {
                  key: "collabora";
                  url: string;
                }
              | {
                  key: "officeonline";
                  url: string;
                }
            >((url) => ({
              key: "officeonline" as const,
              url,
            })),
          )
          .mapError(() => new Error("Cannot edit this item."));
      }),
  );
  const viewHidden = computed(() =>
    selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .map((file) => file.canOpen.isOk)
      .orElse(false),
  );
  const viewAllowed = computed(() =>
    selection
      .asSet()
      .only.toResult(() => new Error("Too many items selected."))
      .flatMap((file) =>
        canPreviewAsImage(file)
          .map((downloadHref) => ({
            key: "image" as const,
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
            canPreviewSnippet(file).map(() => ({
              key: "snippet" as const,
              file,
            })),
          )
          .orElseTry(() =>
            canPreviewAsPdf(file).map((downloadHref) => ({
              key: "pdf" as const,
              downloadHref,
            })),
          )
          .orElseTry(() =>
            canPreviewWithSnapGene(file).map(() => ({
              key: "snapgene" as const,
              file,
            })),
          )
          .orElseTry(() =>
            canPreviewWithAspose(file).map(() => ({
              key: "aspose" as const,
              file,
            })),
          ),
      ),
  );
  const duplicateAllowed = computed((): Result<null> => {
    if (selection.size > 50) return Result.Error([new Error("Cannot duplicate more than 50 items at once.")]);
    return Result.all(...selection.asSet().map((f) => f.canDuplicate)).map(() => null);
  });
  const deleteAllowed = computed((): Result<null> => {
    if (selection.size > 50) return Result.Error([new Error("Cannot delete more than 50 items at once.")]);
    return Result.all(...selection.asSet().map((f) => f.canDelete)).map(() => null);
  });
  const renameAllowed = computed((): Result<null> => {
    return selection
      .asSet()
      .only.toResult(() => new Error("Only one item may be renamed at once."))
      .flatMap((file) => file.canRename);
  });
  const moveToIrodsAllowed = computed((): Result<null> => {
    return Result.all(...selection.asSet().map((f) => f.canMoveToIrods)).map(() => null);
  });

  const moveToS3Allowed = computed((): Result<null> => {
    return Result.all(...selection.asSet().map((f) => f.canMoveToS3)).map(() => null);
  });

  const s3TransferSources = computed((): ReadonlyArray<S3TransferSource> | null => {
    const files = selection.asSet().toArray();
    if (files.length === 0) return null;
    const sources = files.flatMap((file) => {
      if (file instanceof RemoteFile && !file.isFolder) {
        const parentFilestore = file.path[0];
        if (
          parentFilestore instanceof Filestore &&
          parentFilestore.filesystemType === "S3" &&
          parentFilestore.id !== null
        ) {
          return [
            {
              sourceFilestoreId: parentFilestore.id,
              sourcePath: file.remotePath,
            } satisfies S3TransferSource,
          ];
        }
      }
      return [];
    });
    return sources.length === files.length ? sources : null;
  });

  // A selection wholly within one writable S3 filestore moves via the filestore dialog, not the local one.
  const s3MoveTarget = computed((): { filestore: Filestore; sources: RsSet<RemoteFile> } | null => {
    const files = selection.asSet();
    if (files.isEmpty) return null;
    if (files.some((f) => !(f instanceof RemoteFile))) return null;
    const sources = files.filterClass(RemoteFile);
    const parents = sources.toArray().map((f) => f.path[0]);
    const filestore = asWritableS3Filestore(parents[0]);
    if (!filestore) return null;
    if (parents.some((p) => p !== filestore)) return null;
    return { filestore, sources };
  });

  const exportAllowed = computed((): Result<null> => {
    if (selection.size > 100) return Result.Error([new Error("Cannot export more than 100 items at once.")]);
    return Result.all(...selection.asSet().map((f) => f.canBeExported)).map(() => null);
  });
  const getShareDialogSelection = (): Result<{
    globalIds: ReadonlyArray<string>;
    names: ReadonlyArray<string>;
  }> => {
    if (fetchedCurrentUser.tag === "loading") {
      return Result.Error([new Error("Loading user information...")]);
    }
    if (fetchedCurrentUser.tag === "error") {
      return Result.Error([new Error("Unable to load user information. Sharing is temporarily unavailable.")]);
    }
    if (selection.isEmpty) return Result.Error([new Error("At least one snippet must be selected.")]);
    if (selection.asSet().some((f) => !f.isSnippet)) return Result.Error([new Error("Only snippets can be shared.")]);
    const selectedFiles = selection.asSet().toArray();
    const globalIds = selectedFiles
      .map((file) => file.globalId)
      .filter((globalId): globalId is string => typeof globalId === "string");
    if (globalIds.length !== selectedFiles.length) {
      // This should never happen, but currently the typing allows for `string | undefined` so it's here as a safeguard
      return Result.Error([new Error("Cannot share snippets that are missing global IDs.")]);
    }

    /*
     * This is a workaround to disable the Share button when the snippet is
     * selected in a shared folder. Shared snippets have a different ID to the
     * original and changing share settings
     */
    for (const file of selectedFiles) {
      const currentFolder = file.path.at(-1);
      if (!currentFolder) {
        continue;
      }
      if (currentFolder.isSharedFolder) {
        /*
         * This is a workaround as we don't currently expose granular permissions
         * in /gallery/getUploadedFiles.
         */
        if (currentUser?.id !== file.ownerId) {
          return Result.Error([new Error("Only owners of the snippet can change its share settings.")]);
        }
      }
    }
    return Result.Ok({
      globalIds,
      names: selectedFiles.map(({ name }) => name),
    });
  };
  const shareAllowed = computed((): Result<null> => {
    return getShareDialogSelection().map(() => null);
  });
  const downloadAllowed = computed((): Result<null> => {
    if (selection.asSet().some((f) => f.isFolder)) return Result.Error([new Error("Cannot download folders.")]);
    if (selection.asSet().some((f) => f.isSnippet)) return Result.Error([new Error("Cannot download snippets.")]);
    return Result.Ok(null);
  });
  const moveAllowed = computed((): Result<null> => {
    if (selection.size > 50) return Result.Error([new Error("Cannot move more than 50 items at once.")]);
    return Result.all(...selection.asSet().map((f) => f.canBeMoved)).map(() => null);
  });
  const logOutAllowed = computed((): Result<Filestore> => {
    return selection
      .asSet()
      .only.toResult(() => new Error("Only one item may be logged out of at once."))
      .flatMapDiscarding((file) => file.canBeLoggedOutOf)
      .flatMap((f: GalleryFile) =>
        f instanceof Filestore ? Result.Ok(f) : Result.Error([new Error("Cannot log out of this item.")]),
      );
  });
  const { logout } = useFilestoresEndpoint();
  return (
    <>
      <Button
        variant="contained"
        color="callToAction"
        size="small"
        disabled={selection.isEmpty || !section}
        aria-haspopup="menu"
        aria-expanded={actionsMenuAnchorEl ? "true" : "false"}
        startIcon={<ChecklistIcon />}
        onClick={(e) => {
          setActionsMenuAnchorEl(e.currentTarget);
        }}
        sx={{
          [`&.${buttonClasses.disabled}`]: {
            backgroundColor: theme.palette.action.disabledBackground,
            color: theme.palette.action.disabled,
          },
        }}
      >
        {t("actionsMenu.actions")}
      </Button>
      {Boolean(section) && (
        <Menu
          open={Boolean(actionsMenuAnchorEl)}
          anchorEl={actionsMenuAnchorEl}
          onClose={() => setActionsMenuAnchorEl(null)}
          slotProps={{
            paper: {
              sx: {
                minWidth: "212px",
                ...(actionsMenuAnchorEl
                  ? {
                      transform: "translate(0px, 4px) !important",
                    }
                  : {}),
              },
            },
            list: {
              disablePadding: true,
              "aria-label": "actions",
            },
          }}
        >
          {openAllowed
            .get()
            .map((file) => (
              <AccentMenuItem
                title={t("actionsMenu.open")}
                avatar={<FolderOpenIcon />}
                onClick={() => {
                  openFolder(file);
                  setActionsMenuAnchorEl(null);
                }}
                compact
                key="open"
              />
            ))
            .orElse(null)}
          {!viewHidden.get() && (
            <AccentMenuItem
              title={t("actionsMenu.view")}
              subheader={viewAllowed
                .get()
                .map(() => "")
                .orElseGet((errors) => {
                  if (errors.length === 1) return errors[0].message;
                  return (
                    <>
                      <span>{t("actionsMenu.cannotView")}</span>
                      <ul>
                        {errors.map((e, i) => (
                          <li key={i}>{e.message}</li>
                        ))}
                      </ul>
                    </>
                  );
                })}
              avatar={<VisibilityIcon />}
              onClick={() => {
                viewAllowed.get().do((viewAction) => {
                  if (viewAction.key === "image")
                    void viewAction.downloadHref().then((downloadHref) => {
                      openImagePreview(downloadHref, {
                        caption: viewAction.caption,
                      });
                    });
                  if (viewAction.key === "pdf")
                    void viewAction.downloadHref().then((downloadHref) => {
                      openPdfPreview(downloadHref);
                    });
                  if (viewAction.key === "aspose") void openAsposePreview(viewAction.file);
                  if (viewAction.key === "snapgene") void openSnapGenePreview(viewAction.file);
                  if (viewAction.key === "snippet") openSnippetPreview(viewAction.file);
                });
                setActionsMenuAnchorEl(null);
              }}
              compact
              disabled={viewAllowed.get().isError}
            />
          )}
          <AccentMenuItem
            title="Edit"
            subheader={editingAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            avatar={<EditIcon />}
            onClick={() => {
              editingAllowed.get().do((action) => {
                void (async () => {
                  if (action.key === "officeonline") {
                    window.open(action.url);
                    trackEvent("user:opens:document:officeonline");
                  }
                  if (action.key === "collabora") {
                    window.open(action.url);
                    trackEvent("user:opens:document:collabora");
                  }
                  if (action.key === "image") {
                    try {
                      const downloadHref = await action.downloadHref();
                      const { data } = await axios.get<Blob>(downloadHref, {
                        responseType: "blob",
                      });
                      setImageEditorBlob(data);
                    } catch (e) {
                      if (e instanceof Error) {
                        addAlert(
                          mkAlert({
                            variant: "error",
                            title: "Failed to download image for editing",
                            message: e.message,
                          }),
                        );
                      }
                    }
                  }
                })();
              });
              setActionsMenuAnchorEl(null);
            }}
            compact
            disabled={editingAllowed.get().isError}
          />
          <AccentMenuItem
            title="Duplicate"
            subheader={duplicateAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            avatar={<AddToPhotosIcon />}
            onClick={() => {
              void duplicateFiles(selection.asSet()).then(() => {
                void refreshListing();
                setActionsMenuAnchorEl(null);
                trackEvent("user:duplicates:file:gallery");
              });
            }}
            compact
            disabled={duplicateAllowed.get().isError}
          />
          <AccentMenuItem
            title="Move"
            subheader={moveAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            avatar={<OpenWithIcon />}
            onClick={() => {
              setMoveOpen(true);
            }}
            compact
            disabled={moveAllowed.get().isError}
            aria-haspopup="dialog"
          />
          {(() => {
            const s3Move = s3MoveTarget.get();
            if (s3Move) {
              return (
                <MoveWithinFilestoreDialog
                  key="move within filestore dialog"
                  open={moveOpen}
                  onClose={() => {
                    setMoveOpen(false);
                    setActionsMenuAnchorEl(null);
                  }}
                  filestore={s3Move.filestore}
                  sources={s3Move.sources}
                  refreshListing={refreshListing}
                />
              );
            }
            return Optional.fromNullable(section)
              .map((s) => (
                <MoveDialog
                  key="move dialog"
                  open={moveOpen}
                  onClose={() => {
                    setMoveOpen(false);
                    setActionsMenuAnchorEl(null);
                  }}
                  section={s}
                  refreshListing={refreshListing}
                />
              ))
              .orElse(null);
          })()}
          <AccentMenuItem
            title="Rename"
            subheader={renameAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
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
          <AccentMenuItem
            title={t("actionsMenu.download")}
            subheader={downloadAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            avatar={<FileDownloadIcon />}
            onClick={() => {
              void download(selection.asSet()).then(() => {
                setActionsMenuAnchorEl(null);
                trackEvent("user:downloads:file:gallery");
              });
            }}
            compact
            disabled={downloadAllowed.get().isError}
          />
          <AccentMenuItem
            title={t("actionsMenu.share")}
            subheader={shareAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            avatar={<ShareIcon />}
            onClick={() => {
              setShareOpen(true);
              trackEvent("user:opens:share_dialog:gallery", {
                count: selection.size,
              });
            }}
            compact
            disabled={shareAllowed.get().isError}
            aria-haspopup="dialog"
          />
          <EventBoundary>
            {getShareDialogSelection()
              .map(({ globalIds, names }) => (
                <ShareDialog
                  key={globalIds.join(",")}
                  open={shareOpen}
                  onClose={() => {
                    setShareOpen(false);
                    setActionsMenuAnchorEl(null);
                  }}
                  globalIds={globalIds}
                  names={names}
                  singularName="snippet"
                  pluralName="snippets"
                  isSnippet={selection.asSet().some((f) => f.isSnippet)}
                />
              ))
              .orElse(null)}
          </EventBoundary>
          <AccentMenuItem
            title={t("actionsMenu.export")}
            subheader={exportAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            avatar={<FileDownloadIcon />}
            onClick={() => {
              setExportOpen(true);
              trackEvent("user:opens:export_dialog:gallery", {
                count: selection.size,
              });
            }}
            compact
            disabled={exportAllowed.get().isError}
          />
          <EventBoundary>
            <Suspense fallback={null}>
              {Result.all(
                ...selection
                  .asSet()
                  .toArray()
                  .map(({ id }) => idToString(id)),
              )
                .map((exportIds) => (
                  <ExportDialog
                    key={exportIds.join(",")}
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
                        .map((f) => (f.isFolder ? "FOLDER" : "MEDIA_FILE")),
                      exportNames: selection
                        .asSet()
                        .toArray()
                        .map(({ name }) => name),
                      exportIds,
                    }}
                    allowFileStores={false}
                  />
                ))
                .orElse(null)}
            </Suspense>
          </EventBoundary>
          {showNetfileActions && (
            <>
              <AccentMenuItem
                title={t("actionsMenu.moveToIrods")}
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
              {Result.all(
                ...selection
                  .asSet()
                  .toArray()
                  .map(({ id }) => idToString(id)),
              )
                .map((selectedIds) => (
                  <MoveToIrods
                    key={selectedIds.join(",")}
                    selectedIds={selectedIds}
                    dialogOpen={irodsOpen}
                    setDialogOpen={(newState) => {
                      setIrodsOpen(newState);
                      if (!newState) {
                        setActionsMenuAnchorEl(null);
                        void refreshListing();
                      }
                    }}
                  />
                ))
                .orElse(null)}
              <AccentMenuItem
                title={t("actionsMenu.moveToS3")}
                subheader={moveToS3Allowed
                  .get()
                  .map(() => "")
                  .orElseGet(([e]) => e.message)}
                backgroundColor={IRODS_COLOR.background}
                avatarBackgroundColor="#ffffff"
                foregroundColor={IRODS_COLOR.contrastText}
                avatar={<CardMedia image={S3Logo} />}
                onClick={() => {
                  setS3Open(true);
                }}
                compact
                disabled={moveToS3Allowed.get().isError}
                aria-haspopup="dialog"
              />
              {(() => {
                const sources = s3TransferSources.get();
                const onClose = (newState: boolean) => {
                  setS3Open(newState);
                  if (!newState) {
                    setActionsMenuAnchorEl(null);
                    void refreshListing();
                  }
                };
                if (sources !== null) {
                  return (
                    <MoveToS3
                      key={sources.map((s) => s.sourcePath).join(",")}
                      transferSources={sources}
                      dialogOpen={s3Open}
                      setDialogOpen={onClose}
                    />
                  );
                }
                return Result.all(
                  ...selection
                    .asSet()
                    .toArray()
                    .map(({ id }) => idToString(id)),
                )
                  .map((selectedIds) => (
                    <MoveToS3
                      key={selectedIds.join(",")}
                      selectedIds={selectedIds}
                      dialogOpen={s3Open}
                      setDialogOpen={onClose}
                    />
                  ))
                  .orElse(null);
              })()}
            </>
          )}
          <Divider aria-orientation="horizontal" />
          {/*
           * We hide the log out option rather than disabling it because it
           * is only available for filestores so doesn't apply in the vast,
           * vast majority of cases.
           */}
          {logOutAllowed
            .get()
            .map((filestore) => (
              <AccentMenuItem
                key={filestore.id}
                title="Log Out"
                subheader={logOutAllowed
                  .get()
                  .map(() => "")
                  .orElseGet(([e]) => e.message)}
                backgroundColor={lighten(theme.palette.warning.light, 0.5)}
                foregroundColor={darken(theme.palette.warning.dark, 0.3)}
                avatar={<LogoutIcon />}
                onClick={() => {
                  void logout(filestore).then(() => {
                    void refreshListing();
                    setActionsMenuAnchorEl(null);
                  });
                }}
                compact
              />
            ))
            .orElse(null)}
          <AccentMenuItem
            title="Delete"
            subheader={deleteAllowed
              .get()
              .map(() => "")
              .orElseGet(([e]) => e.message)}
            backgroundColor={lighten(theme.palette.error.light, 0.5)}
            foregroundColor={darken(theme.palette.error.dark, 0.3)}
            avatar={<DeleteOutlineOutlinedIcon />}
            onClick={() => {
              const files = selection.asSet();
              // S3 deletes are permanent: require typed confirmation. Local deletes are soft, so one tap.
              if (!files.isEmpty && files.every((f) => f instanceof RemoteFile)) {
                setActionsMenuAnchorEl(null);
                setDeleteConfirmOpen(true);
                return;
              }
              void deleteFiles(files).then(() => {
                void refreshListing();
                setActionsMenuAnchorEl(null);
              });
            }}
            compact
            disabled={deleteAllowed.get().isError}
          />
        </Menu>
      )}
      {deleteConfirmOpen && (
        <ConfirmationDialog
          title="Permanently delete?"
          consequences={
            <Typography variant="body1">
              {"This permanently deletes "}
              {selection.size}
              {" item"}
              {selection.size > 1 ? "s" : ""}
              {" from the S3 filestore. This cannot be undone."}
            </Typography>
          }
          variant="warning"
          confirmText="permanently delete"
          confirmTextLabel="Type 'permanently delete' to confirm"
          callback={() => {
            void deleteFiles(selection.asSet()).then(() => {
              void refreshListing();
            });
          }}
          handleCloseDialog={() => {
            setDeleteConfirmOpen(false);
          }}
        />
      )}
      <ImageEditingDialog
        imageFile={imageEditorBlob}
        open={imageEditorBlob !== null}
        close={() => {
          setImageEditorBlob(null);
        }}
        submitButtonLabel="Save as new image"
        submitHandler={(newBlob) => {
          void (async () => {
            try {
              const file = selection
                .asSet()
                .only.toResult(() => new Error("Nothing selected"))
                .elseThrow();
              const newFile = new File(
                [newBlob],
                file.transformFilename((name) => `${name}_edited`),
                {
                  type: newBlob.type,
                },
              );
              const idOfFolderThatFileIsIn = Result.fromNullable(
                file.path.at(-1),
                new Error("Current folder is not known"),
              )
                .map(({ id }) => id)
                .orElseTry(() => FetchingData.getSuccessValue(folderId))
                .mapError(() => new Error("Current folder is not known"))
                .elseThrow();
              await uploadFiles(idOfFolderThatFileIsIn, [newFile], {
                originalImageId: file.id,
              });
              void refreshListing();
              trackEvent("user:edit:image:gallery");
            } catch (e) {
              if (e instanceof Error) {
                addAlert(
                  mkAlert({
                    variant: "error",
                    title: "Failed to process edited image",
                    message: e.message,
                  }),
                );
              }
            } finally {
              setActionsMenuAnchorEl(null);
            }
          })();
        }}
        alt={selection
          .asSet()
          .only.map(
            (file) =>
              file.name +
              file.description.match({
                missing: () => "",
                empty: () => "",
                present: (d) => d,
              }),
          )
          .orElse("")}
      />
      <Typography
        variant="body2"
        role="status"
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
                // just enough contrast for the AA standard,
                // but we don't want it to be too prominent
                color: `hsl(${ACCENT_COLOR.main.hue} 1% 45% / 1)`,
              }
            : {}),
        }}
      >
        {selection.label}
      </Typography>
    </>
  );
}

/**
 * The actions menu is how users operate on existing files and folders. It
 * follows the convention that we are increasingly rolling out across the
 * product of having a single button labelled "Actions" positioned in the top
 * left corner of the main part of the UI, rather than a toolbar with buttons.
 *
 * This component largely just handles the UI of the menu and some of the
 * dialogs opened by the menu items. The actual logic for triggering the
 * actions and performing error handling is in the `useGalleryActions` hook.
 * The most complex logic here is determining which menu items are available
 * based on the current selection, which can include folders, files of various
 * types, and even files in external filestores.
 */
export default observer(ActionsMenu);
