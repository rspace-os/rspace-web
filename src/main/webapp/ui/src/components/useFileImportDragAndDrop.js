//@flow

import React, { type Node } from "react";

/**
 * We use the HTML Drag and Drop API[mdn] to provide support for dragging files
 * from outside of the browser into the RSpace Web application. For drag and
 * drop within a particular webpage, we use the DndKit library as it provides
 * an abstraction over touch and keyboard controls, other accessibility
 * considerations, and generic customisation; for more information see DndKit's
 * Architectural justification[dndkit-architecture].
 *
 * This module provides a custom react hook that, much like DndKit, handles the
 * logic of determining whether the user is currently hovering over the
 * dropzone. It also provides some utility components for ensuring a robust
 * user experience.
 *
 * [mdn]: https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API
 * [dndkit-architecture]: https://docs.dndkit.com/#architecture
 */

/**
 * On any webpage where FileImportDragAndDrop is being used, we recommend
 * disabling the Drag and Drop API at the DOM root and only enabling it for the
 * particular dropzones. To do this, wrap the whole page in this component.
 */
export const DisableDragAndDropByDefault = ({
  children,
}: {|
  children: Node,
|}): Node => (
  <div
    onDragOver={(e) => {
      /*
       * Allow root of page to accept drop events...
       */
      e.preventDefault();
      e.stopPropagation();
    }}
    onDrop={(e) => {
      /*
       * ..and then ignore those drop events. This way, if the user
       * drags a file over the page but misses one of the dropzones
       * the drag action gets cancelled rather than resulting in
       * the file being opened in a new browser tab.
       */
      e.preventDefault();
    }}
  >
    {children}
  </div>
);

export const useFileImportDropZone = ({
  onDrop: onDropProp,
  disabled,
}: {|
  onDrop: ($ReadOnlyArray<File>) => void,
  disabled?: boolean,
|}): ({|
  onDragEnter: (DragEvent) => void,
  onDragOver: (DragEvent) => void,
  onDragLeave: (DragEvent) => void,
  onDrop: (DragEvent) => void,
  over: boolean,
|}) => {
  const [overCount, setOverCount] = React.useState(0);

  function onDragEnter(e: DragEvent) {
    e.preventDefault();
    if (disabled) return;
    e.stopPropagation();
    setOverCount((x) => x + 1);
  }

  function onDragOver(e: DragEvent) {
    e.preventDefault();
    e.stopPropagation();
  }

  function onDragLeave(e: DragEvent) {
    e.preventDefault();
    if (disabled) return;
    e.stopPropagation();
    setOverCount((x) => x - 1);
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setOverCount(0);

    if (disabled) return;
    e.stopPropagation();

    const files: Array<File> = [];
    if (e.dataTransfer?.items) {
      // Use DataTransferItemList interface to access the file(s)
      [...e.dataTransfer.items].forEach((item) => {
        // If dropped items aren't files, reject them
        if (item.kind === "file") {
          const f = item.getAsFile();
          if (f) files.push(f);
        }
      });
    } else {
      // Use DataTransfer interface to access the file(s)
      [...(e.dataTransfer?.files ?? [])].forEach((file) => {
        files.push(file);
      });
    }

    onDropProp(files);
  }

  return {
    onDragEnter,
    onDragOver,
    onDragLeave,
    onDrop,
    over: overCount > 0,
  };
};
