import React, { type ReactNode } from "react";
import useStores from "../../../../stores/use-stores";
import { useDraggable } from "@dnd-kit/core";
import Badge from "@mui/material/Badge";
import {
  type Location,
  type Container,
} from "../../../../stores/definitions/Container";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { type GlobalId } from "../../../../stores/definitions/BaseRecord";
import { useHelpers } from "./common";

/**
 * When dragging-and-dropping the content of multiple cells at once,
 * DndContext must contain the coordinates of all of the selected locations
 * relative to the one that the user is actually moving according to the
 * dnd-kit library. From that, we can calculate if all of the required cells
 * are empty and allow the move operation. This function calculates the
 * relative coordinated of all of the selceted locations to the passed
 * location.
 *
 * @param container The grid Container within which the move operation is being
 *                  performed.
 * @param location  The location that the user is actually
 *                  dragging-and-dropping.
 */
function calculateRelativeCoords(
  container: Container,
  location: Location
): ReadonlyArray<{ x: number; y: number; globalId: GlobalId | null }> {
  return (container.selectedLocations ?? []).map((l) => ({
    globalId: l.content?.globalId ?? null,
    x: l.coordX - location.coordX,
    y: l.coordY - location.coordY,
  }));
}

/**
 * If some of the selected locations are empty then drag-and-drop is disabled.
 * There is not a hard technical reason for this, but just to keep things
 * simple, and could be allowed.
 *
 * @param container The grid Container within which the move operation is being
 *                  performed.
 */
function areAnyOfTheSelectedLocationsEmpty(container: Container): boolean {
  if (!container.selectedLocations) return true;
  return !container.selectedLocations.every((l) => Boolean(l.content));
}

type DraggableProps = {
  /**
   * The grid or visual container within which items are being
   * dragged-and-dropped.
   */
  container: Container;

  /**
   * This Location from which its content can be dragged. This location MUST
   * have a content, i.e. it cannot be empty.
   */
  location: Location;

  /**
   * The InventoryRecord that can be dragged from this `location` to a new
   * location i.e. `location.content`. We pass it separately from `location` to
   * convince Flow that we've checked that the content is not null.
   */
  content: InventoryRecord;

  /**
   * The value of the HTML tabIndex attribute that is to be set on the
   * draggable HTMLDivElement. For the keyboard-driven drag-and-drop mode to
   * work, this MUST be set to 0 when tapping Tab is intended to bring focus to
   * the location, where thereafter Space can start the drag-and-drop.
   */
  tabIndex?: number;

  /**
   * Should the current element have focus? The browser provides some default
   * behaviour with tabIndex: if an element has a tabIndex of 0 then it is
   * entered into the tab order and so will become focussed when the user
   * presses tab. This prop MUST be set to true by the parent element listening
   * to change to `document.activeElement`. Moreover, our JS code may at times
   * change the focussed draggable element through other keyboard events (e.g.
   * arrow keys), which MUST also result in a change to this prop.
   *
   * This prop is used by this component to ensure that DndKit's DOM nodes have
   * focus when necessary so that the keyboard-driven drag-and-drop mode works
   * correctly.
   */
  hasFocus: boolean;

  /**
   * The Node that renders the `content`. It will be wrapped in Badge when
   * dragging to indicate the total number of items that are being
   * simultaneously moved.
   */
  children: ReactNode;
};

/**
 * This component defines a region that the user can drag and thereby instigate
 * a drag-and-drop operation. It is used to wrap the rendering of a location's
 * content so that the user may move that content to a different location.
 */
export function Draggable({
  container,
  location,
  content,
  tabIndex,
  hasFocus,
  children,
}: DraggableProps): ReactNode {
  const { moveStore } = useStores();
  const {
    thisLocationIsTheOrigin,
    dragAndDropInProgress,
    numberOfItemsBeingDragged,
  } = useHelpers();

  /*
   * Drag-and-drop is disabled within the move dialog as it could get quite
   * confusing if one move operation is happening inside another. Having said
   * that, there may be times when this may be useful and so this decision may
   * be worth revisiting. For example, when moving an item from container A to
   * container B, whilst in the move dialog the user may find the intended
   * destination location in B is currently occupied and that item ought to be
   * in a different location of B. It would certainly be quicker to move it to
   * where it ought to be inside the move dialog using drag-and-drop rather
   * than having to cancel the current move operation, move it to where it
   * should be, and then re-attempt the A to B move.
   */
  const disabled =
    areAnyOfTheSelectedLocationsEmpty(container) || moveStore.isMoving;

  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    disabled,
    id: content.globalId || '',
    data: {
      location,
      content,
      relativeCoords: calculateRelativeCoords(container, location),
    },
  });
  const style = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0) scale(1.5)`,
        zIndex: 1, // just needs to be rendered above Nodes later in the DOM
        position: "relative" as const,
        boxShadow: `hsl(0deg, 100%, 20%, 20%) 0px 2px 8px 0px`,
      }
    : {};

  const cursor = () => {
    if (disabled) return "crosshair";
    if (transform) return "grabbing";
    if (location.selected) return "grab";
    return "crosshair";
  };

  /*
   * This is what allows the user to tap Space to enter the keyboard-driven
   * drag-and-drop mode. When they use the Arrow keys to move the selection
   * over a particular table cell, this snippet moves the focus to the
   * draggable within the table cell (which is a requirement of the Dndkit
   * library).
   */
  const ref = React.useRef<HTMLElement | null>(null);
  React.useEffect(() => {
    if (hasFocus) ref.current?.focus();
  }, [hasFocus]);

  return (
    <div
      ref={(node) => {
        setNodeRef(node);
        ref.current = node;
      }}
      {...listeners}
      {...attributes}
      style={{
        ...style,
        cursor: cursor(),
      }}
      tabIndex={tabIndex ?? -1}
    >
      <Badge
        color="secondary"
        component="div"
        badgeContent={
          dragAndDropInProgress && thisLocationIsTheOrigin(location)
            ? numberOfItemsBeingDragged
            : 0
        }
        style={{ display: "contents" }}
      >
        {children}
      </Badge>
    </div>
  );
}