import React from "react";
import { createRoot } from "react-dom/client";
import {
  Filestore,
  GalleryFile,
  LocalGalleryFile,
  RemoteFile,
} from "@/eln/gallery/useGalleryListing";
import { getWorkspaceRecordInformationAjax } from "@/modules/workspace/queries";
import * as ArrayUtils from "@/util/ArrayUtils";
import { IsValid, IsInvalid } from "@/components/ValidatingSubmitButton";
import GalleryEntrypoint from "@/tinyMCE/gallery/GalleryEntrypoint";
import { addFromGallery } from "@/tinyMCE/gallery/utils";
import RsSet from "@/util/set";

declare global {
  interface RSGlobal {
    insertTemplateIntoTinyMCE?: (templateName: string, data: unknown) => void;
    confirm?: (message: string, type: "error" | "info") => void;
  }

  interface Window {
    RS: RSGlobal;
  }
}

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
        const localFiles = ArrayUtils.filterClass(
          LocalGalleryFile,
          files.toArray(),
        );
        localFiles.forEach((file) => {
          const recordId = file.id;

          if (recordId === null) {
            window.RS.confirm?.(
              `Could not insert file "${file.name}"`,
              "error",
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
              window.RS.confirm?.(
                `Could not insert file "${file.name}"`,
                "error",
              );
            }
          })();
        });
        const remoteFiles = ArrayUtils.filterClass(
          RemoteFile,
          files.toArray(),
        );
        remoteFiles.forEach((file) => {
          const json = {
            name: file.name,
            linktype: file.isFolder ? "directory" : "file",
            fileStoreId: file.path[0].id,
            relFilePath: file.remotePath,
            nfsId: file.nfsId,
            nfsType: (file.path[0] as Filestore).filesystemType,
          };
          window.RS.insertTemplateIntoTinyMCE?.(
            "netFilestoreLink",
            json,
          );
        });
        if (
          files.size >
          localFiles.length + remoteFiles.length
        ) {
          throw new Error(
            "Some selected files were of an unsupported type",
          );
        }
        newProps?.onClose?.();
      };

      const handleValidateSelection = (file: GalleryFile) => {
        if (
          !(file instanceof LocalGalleryFile) &&
          !(file instanceof RemoteFile)
        )
          return IsInvalid("Unsupported file type");
        if (file.isSystemFolder)
          return IsInvalid("System Folders cannot be inserted");
        return IsValid();
      };

      root.render(
        <GalleryEntrypoint open={newProps.open} onClose={newProps.onClose} onSubmit={handleSubmit} validateSelection={handleValidateSelection} />,
      );
    }
  }

  if (!document.getElementById("tinymce-gallery")) {
    const div = document.createElement("div");
    div.id = "tinymce-gallery";
    document.body.appendChild(div);
  }
  const galleryRenderer = renderGallery(
    document.getElementById("tinymce-gallery")!,
  );
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
    tooltip: "Insert from RSpace Gallery",
    icon: "image",
    //shortcut: 'ctrl+shift+1',
    onAction: openGalleryAction,
  });

  // Adds a menu item to the insert menu
  editor.ui.registry.addMenuItem("optMediaGallery", {
    text: "From RSpace Gallery",
    icon: "image",
    //shortcut: 'ctrl+shift+1',
    onAction: openGalleryAction,
  });

  // Adds an option to the slash-menu
  if (!window.insertActions) window.insertActions = new Map();
  window.insertActions.set("optMediaGallery", {
    text: "From RSpace Gallery",
    icon: "image",
    action: openGalleryAction,
  });

  editor.addCommand('cmdMediaGallery', openGalleryAction);

  return {
    getMetadata: () => ({
      name: 'RSpace Gallery Plugin',
      url: 'https://www.researchspace.com/',
    })
  };
});
