import { createRoot } from "react-dom/client";
import { IsInvalid, IsValid } from "@/components/ValidatingSubmitButton";
import { type Filestore, type GalleryFile, LocalGalleryFile, RemoteFile } from "@/eln/gallery/useGalleryListing";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { getWorkspaceRecordInformationAjax } from "@/modules/workspace/queries";
import GalleryEntrypoint from "@/tinyMCE/gallery/GalleryEntrypoint";
import { addFromGallery } from "@/tinyMCE/gallery/utils";
import type RsSet from "@/util/set";

declare global {
  interface RSGlobal {
    insertTemplateIntoTinyMCE?: (templateName: string, data: unknown) => void;
    confirm?: (message: string, type: "error" | "info") => void;
  }

  interface Window {
    RS: RSGlobal;
  }
}

// biome-ignore lint/complexity/useArrowFunction: TinyMCE instantiates plugins with `new`, so this factory must be constructable (an arrow function throws "is not a constructor")
parent.tinymce.PluginManager.add("gallery", function (editor) {
  function* renderGallery(domContainer: HTMLElement): Generator<
    {
      open: boolean;
      onClose?: () => void;
    },
    void,
    { open: boolean; onClose?: () => void }
  > {
    const root = createRoot(domContainer);
    while (true) {
      let newProps: { open: boolean; onClose?: () => void } = {
        open: false,
        onClose: () => {},
      };
      newProps = yield newProps;
      const handleSubmit = (files: RsSet<GalleryFile>) => {
        const localFiles = files.toArray().filter((file): file is LocalGalleryFile => file instanceof LocalGalleryFile);
        localFiles.forEach((file) => {
          const recordId = file.id;

          if (recordId === null) {
            window.RS.confirm?.(`Could not insert file "${file.name}"`, "error");
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
              window.RS.confirm?.(`Could not insert file "${file.name}"`, "error");
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
          window.RS.insertTemplateIntoTinyMCE?.("netFilestoreLink", json);
        });
        if (files.size > localFiles.length + remoteFiles.length) {
          throw new Error("Some selected files were of an unsupported type");
        }
        newProps?.onClose?.();
      };

      const handleValidateSelection = (file: GalleryFile) => {
        if (!(file instanceof LocalGalleryFile) && !(file instanceof RemoteFile))
          return IsInvalid("Unsupported file type");
        if (file.isSystemFolder) return IsInvalid("System Folders cannot be inserted");
        return IsValid();
      };

      root.render(
        <I18nRoot namespaces={["common"]}>
          <GalleryEntrypoint
            open={newProps.open}
            onClose={newProps.onClose}
            onSubmit={handleSubmit}
            validateSelection={handleValidateSelection}
          />
        </I18nRoot>,
      );
    }
  }

  if (!document.getElementById("tinymce-gallery")) {
    const div = document.createElement("div");
    div.id = "tinymce-gallery";
    document.body.appendChild(div);
  }
  // biome-ignore lint/style/noNonNullAssertion: initial biome migration
  const galleryRenderer = renderGallery(document.getElementById("tinymce-gallery")!);
  galleryRenderer.next({ open: false });

  const openGalleryAction = () => {
    galleryRenderer.next({
      open: true,
      onClose: () => {
        galleryRenderer.next({ open: false });
      },
    });
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
