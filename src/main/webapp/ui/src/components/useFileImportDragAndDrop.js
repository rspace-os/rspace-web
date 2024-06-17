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
