// @flow

import React, { useEffect, type Node, type ComponentType } from "react";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import { type Location } from "../../../../stores/definitions/Container";
import * as DragAndDrop from "../DragAndDrop";

type LocationWrapperArgs = {|
  children: Node,
  parentRect: {
    width: number,
    height: number,
  },
  location: Location,
|};

const useStyles = makeStyles()((theme) => ({
  container: {
    position: "absolute",
    borderRadius: 5,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",

    /*
     * This is an arbitrary value to balance the need for the details in the
     * thumbnails to be identifiable whilst minimising how much of the
     * locations image is obscured and how often location markers overlap.
     */
    width: theme.spacing(5),
    height: theme.spacing(5),

    // the wrapper should not be interactable so that the content can determine
    // whether it should be interactable of not
    pointerEvents: "none",
  },
}));

// the stored location point should be rendered as the bottom middle of the
// icon therefore, we need to shift the icon to the left and up.
const iconDimension = 55;
const locationMarkerOffset = {
  x: iconDimension / 2,
  y: iconDimension - 4, // the point of the icon is 4px up from the bottom
};

function LocationWrapper({
  children,
  parentRect,
  location,
}: LocationWrapperArgs): Node {
  const { classes } = useStyles();
  const cellRef = React.useRef<?HTMLDivElement>(null);

  const positionX = () =>
    Math.round((location.coordX / 1000) * parentRect.width) -
    locationMarkerOffset.x;

  const positionY = () =>
    Math.round((location.coordY / 1000) * parentRect.height) -
    locationMarkerOffset.y;

  useEffect(() => {
    if (parentRect.hasOwnProperty("width")) {
      location.setPosition(positionX(), positionY());
      location.setDimensions(55, 55);
    }
  }, [parentRect]);

  return (
    <div
      className={classes.container}
      style={{ left: location.x, top: location.y }}
      ref={cellRef}
    >
      <DragAndDrop.Dropzone location={location}>
        {children}
      </DragAndDrop.Dropzone>
    </div>
  );
}

export default (observer(LocationWrapper): ComponentType<LocationWrapperArgs>);
