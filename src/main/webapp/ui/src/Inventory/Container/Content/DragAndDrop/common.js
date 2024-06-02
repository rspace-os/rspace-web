//@flow
import { useDndContext } from "@dnd-kit/core";
import {
  type Location,
  type Container,
} from "../../../../stores/definitions/Container";
import { type GlobalId } from "../../../../stores/definitions/BaseRecord";

/*
 * Specifically a string of the form "<coordX>,<coordY>".
 * A string is used so that we can encode both the x and y coordinates within
 * the Dndkit Droppable id.
 */
export opaque type DroppableId = string;

export function mkDroppableId(location: Location): DroppableId {
  return `${location.coordX},${location.coordY}`;
}

function getCoordsOfDroppableId(droppableId: DroppableId): {|
  coordX: number,
  coordY: number,
|} {
  const regexResult = droppableId.match(/(\d+),(\d+)/);
  if (!regexResult) throw new Error("Should never happen.");
  const [, x, y] = regexResult;
  return {
    coordX: parseInt(x, 10),
    coordY: parseInt(y, 10),
  };
}

/**
 * These are helper functions for each of the parts of the Grid and Image view
 * DragAndDrop system.
 */
export function useHelpers(): {|
  /**
   * Quite simply, is the user currently drag-and-dropping an item. This could
   * be because the pointer is held down or because the keyboard-driven
   * drag-and-drop mode has been entered.
   */
  dragAndDropInProgress: boolean,

  numberOfItemsBeingDragged: number,

  /**
   * When dragging-and-dropping multiple items in grid view, the draggable that
   * the user is dragging around defines an origin around which all of the
   * other selected locations are moved. For example, if there are two
   * horizontally adjacent selected locations and the user drags the left one,
   * the right one will be dropped in the cell to the right of the cell into
   * which the left one is dropped. This value checks if this `location` is
   * that origin as we must render it differently to all of the other content
   * being moved.
   */
  thisLocationIsTheOrigin: (Location) => boolean,

  /**
   * if `location`'s globalId is in `relativeCoords` then that means the
   * drag-and-drop move operation is emptying it. As such, it is valid for us
   * to move something else into this Location in the same move operation.
   * Consider the scenario of moving a row of elements one cell to the left:
   * there need only be an empty location to the left of the whole row and all
   * the rest move down one. All of the locations within the list will be
   * within `relativeCoords` and are thus allowed drop zones.
   */
  anItemIsBeingMoveOutOfLocation: (Location) => boolean,

  /**
   * Is the user choosing this location? If they're using a pointer then the
   * cursor is hovered over the dropzone associated with this location or is
   * hovered over another dropzone that is also being moved into but is
   * relative to this dropzone such that one of the items being moved would be
   * dropped here if the user were to relese their finger/mouse button wherever
   * it may be. If they're using the keyboard-driven drag-and-drop mode then
   * the arrow keys have been tapped so that this dropzone is the one that
   * should the user tap enter then it will be passed to the onDragEnd event
   * handler and have one of the items being moved dropped into it.
   */
  isChoosing: (Location) => boolean,
|} {
  const dndContext = useDndContext();
  const relativeCoords: null | $ReadOnlyArray<{|
    x: number,
    y: number,
    globalId: ?GlobalId,
  |}> = dndContext.active?.data.current?.relativeCoords;

  return {
    dragAndDropInProgress: (relativeCoords ?? []).length > 0,

    numberOfItemsBeingDragged: (relativeCoords ?? []).length,

    thisLocationIsTheOrigin: (location) =>
      (relativeCoords ?? []).some(
        ({ globalId, x, y }) =>
          globalId === location.content?.globalId && x === 0 && y === 0
      ),

    anItemIsBeingMoveOutOfLocation: (location) =>
      (relativeCoords ?? []).some(
        ({ globalId }) => globalId === location.content?.globalId
      ),

    isChoosing: (location) => {
      const id = dndContext.over?.id;
      if (!id) return false;
      const [, col, row] = id.match(/(\d+),(\d+)/);
      const overCol = parseInt(col, 10);
      const overRow = parseInt(row, 10);
      if (!relativeCoords) return false;
      return relativeCoords.some(
        ({ x, y }) =>
          overCol + x === location.coordX && overRow + y === location.coordY
      );
    },
  };
}

/**
 * These are helper functions for each of the parts of the Grid and Image view
 * DragAndDrop system that are paramaterised by that Grid/Image container.
 */
export function useContainerHelpers(container: Container): {|
  /**
   * Given a source location, find the associated destination location such
   * that when the move operation completes the source location's content is
   * moved to the destination location.
   */
  getDestinationLocationForSourceLocation: (
    {
      active: null | {
        data: {
          current: null | {
            relativeCoords: $ReadOnlyArray<{|
              x: number,
              y: number,
              globalId: ?GlobalId,
            |}>,
            ...
          },
          ...
        },
        ...
      },
      over: null | { id: DroppableId },
      ...
    },
    Location
  ) => Location,
|} {
  return {
    getDestinationLocationForSourceLocation: (event, sourceLocation) => {
      const relativeCoords: ?$ReadOnlyArray<{|
        x: number,
        y: number,
        globalId: ?GlobalId,
      |}> = event.active?.data.current?.relativeCoords;
      let overLocation: ?Location = null;
      if (event.over?.id) {
        const { coordX, coordY } = getCoordsOfDroppableId(event.over.id);
        overLocation = container.findLocation(coordX, coordY);
      }
      /*
       * Whilst this function does potentially throw a lot of exceptions, they
       * are all very unlikely or impossible to occur to due to the checked
       * that have already happened before this function is called / the way
       * the data model is instantiated.
       */
      if (!overLocation) throw new Error("No destination dropzone selected.");
      if (!sourceLocation.content)
        throw new Error("Selected location cannot be empty when moving.");
      if (!sourceLocation.content.globalId)
        throw new Error(
          "Content of selected location must have a Global ID when moving."
        );
      const g = sourceLocation.content.globalId;
      const relCoords = (relativeCoords ?? []).find(
        ({ globalId }) => g === globalId
      );
      if (!relCoords)
        throw new Error(
          `Could not find relative coordinates for location content with globalId ${g}`
        );
      const { x, y } = relCoords;
      const dest = container.findLocation(
        overLocation.coordX + x,
        overLocation.coordY + y
      );
      if (!dest)
        throw new Error(
          `Could not find location at coordinates ${overLocation.coordX + x},${
            overLocation.coordY + y
          }.`
        );
      return dest;
    },
  };
}
