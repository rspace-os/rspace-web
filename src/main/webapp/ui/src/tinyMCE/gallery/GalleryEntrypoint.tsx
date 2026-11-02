import { IsInvalid, IsValid } from "@/components/ValidatingSubmitButton";
import { type Filestore, type GalleryFile, LocalGalleryFile, RemoteFile } from "@/eln/gallery/useGalleryListing";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { getWorkspaceRecordInformationAjax } from "@/modules/workspace/queries";
import { type Alert, mkAlert } from "@/stores/contexts/Alert";
import GalleryPickerRoot from "@/tinyMCE/gallery/GalleryPickerRoot";
import { addFromGallery } from "@/tinyMCE/gallery/utils";
import { getPluginDialogRoot } from "@/tinyMCE/pluginDialogRoot";
import type RsSet from "@/util/set";

declare global {
  interface RSGlobal {
    insertTemplateIntoTinyMCE?: (templateName: string, data: unknown) => void;
  }

  interface Window {
    RS?: RSGlobal;
  }
}

// biome-ignore lint/complexity/useArrowFunction: TinyMCE instantiates plugins with `new`, so this factory must be constructable (an arrow function throws "is not a constructor")
parent.tinymce.PluginManager.add("gallery", function (editor) {
  const root = getPluginDialogRoot("tinymce-gallery");
  let addAlert: ((alert: Alert) => void) | null = null;

  const handleValidateSelection = (file: GalleryFile) => {
    if (!(file instanceof LocalGalleryFile) && !(file instanceof RemoteFile)) return IsInvalid("Unsupported file type");
    if (file.isSystemFolder) return IsInvalid("System Folders cannot be inserted");
    return IsValid();
  };

  const renderDialog = (props: { open: boolean; onClose?: () => void }) => {
    const handleSubmit = (files: RsSet<GalleryFile>) => {
      const localFiles = files.toArray().filter((file): file is LocalGalleryFile => file instanceof LocalGalleryFile);
      localFiles.forEach((file) => {
        const recordId = file.id;

        if (recordId === null) {
          addAlert?.(
            mkAlert({
              message: i18n.t("gallery:insertFileFailed", {
                fileName: file.name,
              }),
              variant: "error",
            }),
          );
          return;
        }

        void (async () => {
          try {
            const recordInformation = await getWorkspaceRecordInformationAjax({
              recordId,
            });
            addFromGallery(recordInformation);
          } catch (e) {
            console.error(e);
            addAlert?.(
              mkAlert({
                message: i18n.t("gallery:insertFileFailed", {
                  fileName: file.name,
                }),
                variant: "error",
              }),
            );
          }
        })();
      });
      const remoteFiles = files.toArray().filter((file): file is RemoteFile => file instanceof RemoteFile);
      remoteFiles.forEach((file) => {
        const json = {
          name: file.name,
          linktype: file.isFolder ? "directory" : "file",
          fileStoreId: file.path[0].id,
          relFilePath: file.remotePath,
          nfsId: file.nfsId,
          nfsType: (file.path[0] as Filestore).filesystemType,
        };
        window.RS?.insertTemplateIntoTinyMCE?.("netFilestoreLink", json);
      });
      if (files.size > localFiles.length + remoteFiles.length) {
        throw new Error("Some selected files were of an unsupported type");
      }
      props.onClose?.();
    };

    root.render(
      <I18nRoot namespaces={["common"]}>
        <GalleryPickerRoot
          open={props.open}
          onClose={props.onClose}
          onSubmit={handleSubmit}
          validateSelection={handleValidateSelection}
          onAlertReady={(fn) => {
            addAlert = fn;
          }}
        />
      </I18nRoot>,
    );
  };

  const openGalleryAction = () => {
    renderDialog({ open: true, onClose: () => renderDialog({ open: false }) });
  };

  // Add a button to the toolbar
  editor.ui.registry.addButton("btnMediaGallery", {
    tooltip: i18n.t("workspace:tinymce.gallery.insertFromGallery"),
    icon: "image",
    //shortcut: 'ctrl+shift+1',
    onAction: openGalleryAction,
  });

  // Adds a menu item to the insert menu
  editor.ui.registry.addMenuItem("optMediaGallery", {
    text: i18n.t("workspace:tinymce.gallery.fromGallery"),
    icon: "image",
    //shortcut: 'ctrl+shift+1',
    onAction: openGalleryAction,
  });

  // Adds an option to the slash-menu
  if (!window.insertActions) window.insertActions = new Map();
  window.insertActions.set("optMediaGallery", {
    text: i18n.t("workspace:tinymce.gallery.fromGallery"),
    icon: "image",
    action: openGalleryAction,
  });

  editor.addCommand("cmdMediaGallery", openGalleryAction);

  return {
    getMetadata: () => ({
      name: i18n.t("workspace:tinymce.gallery.pluginName"),
      url: "https://www.researchspace.com/",
    }),
  };
});
