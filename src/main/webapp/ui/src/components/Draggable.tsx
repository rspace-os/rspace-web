import React from "react";
import { clamp } from "../util/Util";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import { type Point } from "../util/types";

const getParentDiv = (node: Element): HTMLElement => {
  const div = node.closest("div");
  if (div && div instanceof HTMLElement) return div;
  throw Error("No parent of the given node is a 'div' tag.");
};

type DraggableArgs = {
  children: React.ReactNode;
  draggable: boolean;
  initial: Point;
  onChange: (newPoint: Point) => void;
  onClick: () => void;
  parent: HTMLElement;
  onDragStart: () => void;
  onDragEnd: () => void;
};

const useStyles = makeStyles<{ draggable: boolean; position: Point }>()(
  (theme, { draggable, position }) => ({
    container: {
      position: "absolute",
      left: position.x,
      top: position.y,
      touchAction: "none",
      cursor: draggable ? "move" : "default",
    },
  })
);

/*
 * General purpose wrapper for draggable components.
 * Parent must have CSS property "display: relative"
 */
function Draggable({
  children,
  draggable,
  initial,
  onChange,
  onClick,
  onDragStart,
  onDragEnd,
  parent,
}: DraggableArgs): React.ReactNode {
  const [dragging, setDragging] = React.useState(false);
  const [position, setPosition] = React.useState(initial);
  const { classes } = useStyles({ position, draggable });

  // wherever the user clicks inside this component, that precise point should
  // be the handle i.e. there shouldn't be a jolt where a corner becomes the anchor
  const [cursorOffsetInsideDiv, setCursorOffsetInsideDiv] = React.useState({
    x: 0,
    y: 0,
  });

  // Tap and hold to start dragging, just tapping is selection.
  // This stores the timeout during that hold process
  const [initiating, setInitiating] = React.useState<NodeJS.Timeout | null>(
    null
  );

  React.useEffect(() => {
    if (!dragging) {
      setPosition(initial);
    }
  }, [initial]);

  const onPointerDown = (_event: React.PointerEvent<HTMLDivElement>) => {
    const event = { ..._event };
    const startDragging = () => {
      setInitiating(null);
      if (draggable) {
        onDragStart();
        setDragging(true);
      }
    };
    setInitiating(setTimeout(startDragging, 250));

    const clickedPoint = {
      x: event.pageX,
      y: event.pageY,
    };
    if (!(event.target instanceof HTMLElement))
      throw new Error("event.target is not a HTML Element");
    const parentDiv = getParentDiv(event.target);
    const gbcr = parentDiv.getBoundingClientRect();
    const divTopRightCorner = {
      x: gbcr.left,
      y: gbcr.top,
    };
    setCursorOffsetInsideDiv({
      x: clickedPoint.x - divTopRightCorner.x,
      y: clickedPoint.y - divTopRightCorner.y,
    });
    parentDiv.setPointerCapture(event.pointerId);
  };

  const onPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (dragging) {
      const newPointOnViewport = {
        x: event.pageX,
        y: event.pageY,
      };
      const gbcr = parent.getBoundingClientRect();
      const parentTopLeftCorner = {
        x: gbcr.left,
        y: gbcr.top,
      };

      const dimensionsOfTheParent = {
        width: gbcr.width,
        height: gbcr.height,
      };
      if (!(event.target instanceof HTMLElement))
        throw new Error("event.target is not a HTML Element");
      const parentDiv = getParentDiv(event.target);
      const dimensionsOfThisDiv = {
        width: parentDiv.offsetWidth,
        height: parentDiv.offsetHeight,
      };

      const x =
        newPointOnViewport.x - parentTopLeftCorner.x - cursorOffsetInsideDiv.x;
      const y =
        newPointOnViewport.y - parentTopLeftCorner.y - cursorOffsetInsideDiv.y;

      setPosition({
        x: clamp(x, 0, dimensionsOfTheParent.width - dimensionsOfThisDiv.width),
        y: clamp(
          y,
          0,
          dimensionsOfTheParent.height - dimensionsOfThisDiv.height
        ),
      });
    }
  };

  const onPointerUp = () => {
    if (initiating) {
      clearTimeout(initiating);
      onClick();
    } else {
      setDragging(false);
      onDragEnd();
      onChange({
        x: position.x,
        y: position.y,
      });
    }
  };

  const onPointerCancel = () => {
    setPosition(initial);
  };

  return (
    <div
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      className={classes.container}
    >
      {children}
    </div>
  );
}

export default observer(Draggable);
