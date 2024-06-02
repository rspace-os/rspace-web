//@flow

import NumberedLocation from "../NumberedLocation";
import LocationModel from "../../../../../stores/models/LocationModel";
import Draggable from "../../../../../components/Draggable";
import Grid from "@mui/material/Grid";
import React, {
  useLayoutEffect,
  useState,
  useRef,
  type Node,
  type ComponentType,
} from "react";
import useStores from "../../../../../stores/use-stores";
import { makeStyles } from "tss-react/mui";
import { clamp } from "../../../../../util/Util";
import { observer } from "mobx-react-lite";
import { type UseState, type Point } from "../../../../../util/types";
import ContainerModel from "../../../../../stores/models/ContainerModel";
import { type Location } from "../../../../../stores/definitions/Container";
import { pick } from "../../../../../util/unsafeUtils";

export type TappedLocationData = {|
  location: Location,
  number: number,
  point: {|
    left: number,
    top: number,
  |},
|};

// the stored location point should be rendered as the bottom middle of the icon
// therefore, we need to shift the icon to the left and up.
const iconDimension = 48;
const locationMarkerOffset: Point = {
  x: iconDimension / 2,
  y: iconDimension - 4, // the point of the icon is 4px up from the bottom
};

const LocationMarker = ({
  number,
  location,
  point,
  onChange,
  img,
  onClick,
  editable,
  selected,
}: {
  number: number,
  location: Location,
  point: Point,
  onChange: (Point) => void,
  img: HTMLElement,
  onClick: (TappedLocationData) => void,
  editable: boolean,
  selected: boolean,
}) => {
  const [dragging, setDragging] = useState(false);
  return (
    <Draggable
      initial={{
        x: point.x - locationMarkerOffset.x,
        y: point.y - locationMarkerOffset.y,
      }}
      parent={img}
      onChange={onChange}
      onClick={() => {
        const imgPos = pick("left", "top")(img.getBoundingClientRect());
        const tappedLocationData = {
          location,
          number,
          point: {
            left: point.x + imgPos.left,
            top: point.y + imgPos.top,
          },
        };
        onClick(tappedLocationData);
      }}
      draggable={editable}
      onDragStart={() => setDragging(true)}
      onDragEnd={() => setDragging(false)}
    >
      <NumberedLocation
        number={number}
        shadow={dragging}
        selected={selected && editable}
      />
    </Draggable>
  );
};

const useStyles = makeStyles()((theme, { editable }) => ({
  rounded: {
    padding: 0,
    width: "auto",
    height: "auto",
    maxWidth: "100%",
    userSelect: "none",
  },
  imageContainer: {
    position: "relative",
    marginTop: 15,
    cursor: editable ? "crosshair" : "default",
  },
  detailsPopupContent: {
    padding: "0 !important",
  },
}));

type ContentImageArgs = {|
  editable?: boolean,
  onLocationTap?: (TappedLocationData) => void,
  onClearSelection?: () => void,
  selected?: ?TappedLocationData,
|};

function ContentImage({
  editable = false,
  onLocationTap = () => {},
  onClearSelection = () => {},
  selected = null,
}: ContentImageArgs): Node {
  const { searchStore } = useStores();
  const activeResult = searchStore.activeResult;
  if (!activeResult || !(activeResult instanceof ContainerModel))
    throw new Error("ActiveResult must be a Container");
  const { classes } = useStyles({ editable });
  const [img, setImg] = useState(null);
  const [tappedLocation, setTappedLocation]: UseState<?TappedLocationData> =
    useState(null);

  useLayoutEffect(() => {
    setTappedLocation(selected);
  }, [selected]);

  // Observe changes to the size of the image
  const [imageDimensions, setImageDimensions] = useState(null);
  const resizeObserver = useRef(
    new ResizeObserver((entries) => {
      setImageDimensions(
        pick(
          "width",
          "height",
          "left",
          "top"
        )(entries[0].target.getBoundingClientRect())
      );
    })
  );
  const imgRef = useRef<?HTMLElement>(null);

  useLayoutEffect(() => {
    if (imgRef.current) {
      resizeObserver.current.observe(imgRef.current);
    }
    return () => {
      if (imgRef.current) resizeObserver.current.unobserve(imgRef.current);
    };
  }, [img, resizeObserver]);

  const newMarker = (event: {
    target: HTMLElement,
    clientX: number,
    clientY: number,
    ...
  }) => {
    if (!editable) {
      return;
    }

    if (tappedLocation) {
      setTappedLocation(null);
      onClearSelection();
      return;
    }

    const imgTopLeftCornerRelativeToViewport = pick(
      "x",
      "y"
    )(event.target.getBoundingClientRect());
    const tappedLocationRelativeToViewport = {
      x: event.clientX,
      y: event.clientY,
    };
    if (!imageDimensions) throw new Error("Image dimensions are not known");
    const { width: imageWidth, height: imageHeight } = imageDimensions;
    const newPoint = {
      x: clamp(
        tappedLocationRelativeToViewport.x -
          imgTopLeftCornerRelativeToViewport.x,
        iconDimension / 2,
        imageWidth - iconDimension / 2
      ),
      y: clamp(
        tappedLocationRelativeToViewport.y -
          imgTopLeftCornerRelativeToViewport.y,
        iconDimension,
        imageHeight
      ),
    };
    const newLocation = new LocationModel({
      parentContainer: activeResult,
      coordX: Math.round((newPoint.x * 1000) / imageDimensions.width),
      coordY: Math.round((newPoint.y * 1000) / imageDimensions.height),
      content: null,
      id: null,
    });
    if (activeResult) {
      activeResult.updateLocationsCount(1);
      activeResult.setAttributesDirty({
        locations: [...activeResult.locations, newLocation],
      });
    }
  };

  const locationMoved = (indexInSorted: number, { x, y }: Point) => {
    if (editable) {
      const location = activeResult.sortedLocations[indexInSorted];
      if (!imageDimensions) throw new Error("Image dimensions are not known");
      const { width: imageWidth, height: imageHeight } = imageDimensions;
      location.coordX = Math.round(
        ((x + locationMarkerOffset.x) * 1000) / imageWidth
      );
      location.coordY = Math.round(
        ((y + locationMarkerOffset.y) * 1000) / imageHeight
      );
      const locations = activeResult.sortedLocations;

      locations[indexInSorted] = location;
      activeResult.setAttributesDirty({
        locations,
      });
    }
  };

  const normalizeCoords = (
    {
      width: imageWidth,
      height: imageHeight,
    }: {| width: number, height: number |},
    location: Location
  ) => {
    return {
      location,
      point: {
        x: Math.round((location.coordX / 1000) * imageWidth),
        y: Math.round((location.coordY / 1000) * imageHeight),
      },
    };
  };

  return (
    Boolean(activeResult.locationsImage) && (
      <Grid container justifyContent="center" alignItems="center">
        <Grid item className={classes.imageContainer}>
          <img
            src={activeResult.locationsImage}
            className={classes.rounded}
            onLoad={({ target }) => setImg(target)}
            onClick={newMarker}
            ref={imgRef}
          />
          {img &&
            imageDimensions &&
            activeResult.sortedLocations
              .map((l: Location) => normalizeCoords(imageDimensions, l))
              .map(
                (
                  {
                    location,
                    point,
                  }: {|
                    location: Location,
                    point: {| x: number, y: number |},
                  |},
                  index: number
                ) => (
                  <LocationMarker
                    number={index + 1}
                    key={index}
                    onChange={(newPoint) => locationMoved(index, newPoint)}
                    location={location}
                    point={point}
                    img={img}
                    onClick={(tappedLocationData) => {
                      setTappedLocation(tappedLocationData);
                      onLocationTap(tappedLocationData);
                    }}
                    editable={editable}
                    selected={tappedLocation?.number === index + 1}
                  />
                )
              )}
        </Grid>
      </Grid>
    )
  );
}

export default (observer(ContentImage): ComponentType<ContentImageArgs>);
