import React from "react";

/**
 * We use the HTML Drag and Drop API[mdn] to provide support for dragging files
 * from outside of the browser into the RSpace Web application. For drag and
 * drop within a particular webpage, we use the DndKit library as it provides
 * an abstraction over touch and keyboard controls, other accessibility
 * considerations, and generic customisation; for more information see DndKit's
 * Architectural justification[dndkit-architecture].
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
}: {
  children: React.ReactNode;
}): React.ReactNode => (
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
    /*
     * Also prevent the user from accidently dragging any content on the page
     * and then initiating the drag event, with the content unintentionally
     * dropped onto a dropzone intended for files from outside the browser.
     */
    onDragStart={(e) => {
      e.preventDefault();
      e.stopPropagation();
    }}
  >
    {children}
  </div>
);

/**
 * This custom react hook, much like DndKit, handles the logic of determining
 * whether the user is currently hovering over the dropzone. It also provides
 * some utility components for ensuring a robust user experience.
 *
 * @arg onDrop   - The function to call when the user drops files onto the dropzone.
 * @arg disabled - If true, the dropzone will not accept files.
 * @returns An object with event handlers for onDragEnter, onDragOver,
 *          onDragLeave, and onDrop, which should be attached to the dropzone
 *          element. The object also contains a boolean value 'over' which is
 *          true when the user is hovering over the dropzone.
 */
export const useFileImportDropZone = ({
  onDrop: onDropProp,
  disabled,
}: {
  onDrop: (files: ReadonlyArray<File>) => void;
  disabled?: boolean;
}): {
  onDragEnter: (e: React.DragEvent) => void;
  onDragOver: (e: React.DragEvent) => void;
  onDragLeave: (e: React.DragEvent) => void;
  onDrop: (e: React.DragEvent) => void;
  over: boolean;
} => {
  const [overCount, setOverCount] = React.useState(0);

  function onDragEnter(e: React.DragEvent) {
    e.preventDefault();
    if (disabled) return;
    e.stopPropagation();
    setOverCount((x) => x + 1);
  }

  function onDragOver(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
  }

  function onDragLeave(e: React.DragEvent) {
    e.preventDefault();
    if (disabled) return;
    e.stopPropagation();
    setOverCount((x) => x - 1);
  }

  function onDrop(e: React.DragEvent) {
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
