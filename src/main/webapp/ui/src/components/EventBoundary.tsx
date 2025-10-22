//@flow

import React from "react";

/**
 * All of the DOM events that happen inside of a dialog shouldn't propagate
 * outside as the menu will take them to be events that it should respond to by
 * providing keyboard navigation. See ../../../../QuirksOfMaterialUi.md,
 * section "Dialogs inside Menus", for more information.
 */
export default function EventBoundary({
  children,
}: {
  children: React.ReactNode;
}): React.ReactNode {
  return (
    /*
     * The eslint suppression is required because `div`s should not ordinarilly
     * have event handlers as they cannot be focussed. However, in this case, we
     * are just using the event listeners on the `div` to prevent the events from
     * further down the DOM from propagating up; the user need not interact with
     * the `div` itself for this to work.
     */

     
    <div
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      onMouseDown={(e) => {
        e.stopPropagation();
      }}
      onClick={(e) => {
        e.stopPropagation();
      }}
    >
      {children}
    </div>
  );
}
