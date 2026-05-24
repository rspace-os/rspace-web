import React, { useEffect } from "react";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import { type Location } from "../../../../stores/definitions/Container";
import * as DragAndDrop from "../DragAndDrop";

type LocationWrapperArgs = {
  children: React.ReactNode;
  parentRect: {
    width: number;
    height: number;
  };
  location: Location;
};

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
}: LocationWrapperArgs): React.ReactNode {
  const cellRef = React.useRef<HTMLDivElement | null>(null);

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
    <Box
      component="div"
      sx={(theme) => ({
        position: "absolute",
        borderRadius: 5,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: theme.spacing(5),
        height: theme.spacing(5),
        pointerEvents: "none",
      })}
      style={{ left: location.x, top: location.y }}
      ref={cellRef}
    >
      <DragAndDrop.Dropzone location={location}>
        {children}
      </DragAndDrop.Dropzone>
    </Box>
  );
}

export default observer(LocationWrapper);
