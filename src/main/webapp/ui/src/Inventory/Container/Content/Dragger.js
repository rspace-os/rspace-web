//@flow

import React, { type Node, type ComponentType } from "react";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import BorderInnerIcon from "@mui/icons-material/BorderInner";
import ContainerModel from "../../../stores/models/ContainerModel";

const DRAGGER_SIZE = 24;

const useStyles = makeStyles()(() => ({
  dragger: {
    position: "absolute",
    zIndex: "100000",
    cursor: "crosshair",
  },
  selection: {
    position: "absolute",
    borderStyle: "dashed",
    border: "1px solid grey",
    backgroundColor: "rgba(0, 173, 239, 0.6)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    transition: "font-size 0.5s",
    color: "rgb(255,255,255,0.7)",
    webkitTouchCallout: "none" /* iOS Safari */,
    webkitUserSelect: "none" /* Safari */,
    userSelect: "none",
    pointerEvents: "none",
  },
}));

type DraggerArgs = {|
  container: ContainerModel,
  parentRef: {| current: ?HTMLElement |},
|};

function Dragger({ container, parentRef }: DraggerArgs): Node {
  const { classes } = useStyles();
  if (!container.locations)
    throw new Error("Locations of container must be known.");
  const locations = container.locations;

  const selectedLocations = () =>
    locations.filter((loc) => loc.isShallow).length;

  const selectionWidth =
    Math.abs(container.selectionEnd.x - container.selectionStart.x) +
    DRAGGER_SIZE / 2;

  const selectionHeight =
    Math.abs(container.selectionEnd.y - container.selectionStart.y) +
    DRAGGER_SIZE / 2;

  const renderCondition = () =>
    Boolean(parentRef.current) &&
    container &&
    container.selectionMode &&
    Math.abs(container.selectionStart.y - container.selectionEnd.y) >
      DRAGGER_SIZE / 2 &&
    Math.abs(container.selectionStart.x - container.selectionEnd.x) > 0;

  if (!parentRef.current) return <></>;
  const parent = parentRef.current;

  /*
   * offsetTop/offsetLeft is the distance from the top/left of the
   * closest parent with a position style e.g. the gap between the
   * grid and the top of the move dialog's content.
   *
   * scrollLeft is necessary here because parentRef handles the
   * horizontal scrolling of the table whereas vertical scrolling is
   * handled by the page/dialog
   */
  if (!renderCondition()) return null;
  return (
    <>
      <BorderInnerIcon
        className={classes.dragger}
        style={{
          top: parent.offsetTop + container.selectionStart.y - DRAGGER_SIZE / 2,
          left:
            parent.offsetLeft +
            container.selectionStart.x -
            DRAGGER_SIZE / 2 -
            parent.scrollLeft,
        }}
      />
      <BorderInnerIcon
        className={classes.dragger}
        style={{
          top: parent.offsetTop + container.selectionEnd.y - DRAGGER_SIZE / 2,
          left:
            parent.offsetLeft +
            container.selectionEnd.x -
            DRAGGER_SIZE / 2 -
            parent.scrollLeft,
        }}
      />
      <div
        className={classes.selection}
        style={{
          fontSize: Math.min(selectionHeight, selectionWidth) * 0.75,
          top:
            parent.offsetTop +
            Math.min(container.selectionStart.y, container.selectionEnd.y),
          left:
            parent.offsetLeft +
            Math.min(container.selectionStart.x, container.selectionEnd.x) -
            parent.scrollLeft,
          height: selectionHeight,
          width: selectionWidth,
        }}
      >
        {selectedLocations()}
      </div>
    </>
  );
}

export default (observer(Dragger): ComponentType<DraggerArgs>);
