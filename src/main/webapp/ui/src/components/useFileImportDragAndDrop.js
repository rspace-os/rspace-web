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
  stopPropagation = true,
}: {|
  onDrop: ($ReadOnlyArray<File>) => void,
  disabled?: boolean,

  /*
   * There are cases where we might want to have one dropzone nested inside
   * another, for example the Gallery page allows users to drop files into the
   * current folder as well as a specific sub-folder.
   *
   * For this to work correctly, we must stop the events, such as onDragEnter
   * and onDragLeave, from propagating up to the parent dropzone so that the
   * inner dropzone becomes a carved out region of the parent dropzone where
   * the parent's functionality does not get invoked. As such, by default, when
   * this prop is true, we stop the events from propagating.
   *
   * However, there are also times when the events SHOULD be propagated because
   * the inner dropzone should also trigger the outer one. Again as an example,
   * the Gallery page displays a pop-up panel onto which files can be dropped
   * to upload into the current folder. We want the events to propagate up for
   * the parent dropzone's `open` to remain true and for the popup to stay
   * open.
   */
  stopPropagation?: boolean,
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
    if (stopPropagation) e.stopPropagation();
    setOverCount((x) => x + 1);
  }

  function onDragOver(e: DragEvent) {
    e.preventDefault();
    if (stopPropagation) e.stopPropagation();
  }

  function onDragLeave(e: DragEvent) {
    e.preventDefault();
    if (disabled) return;
    if (stopPropagation) e.stopPropagation();
    setOverCount((x) => x - 1);
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setOverCount(0);

    if (disabled) return;
    if (stopPropagation) e.stopPropagation();

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
