import NumberedLocation from "../NumberedLocation";
import LocationModel from "../../../../../stores/models/LocationModel";
import Draggable from "../../../../../components/Draggable";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import React, { useLayoutEffect, useState, useRef } from "react";
import useStores from "../../../../../stores/use-stores";
import { clamp } from "../../../../../util/Util";
import { observer } from "mobx-react-lite";
import { type Point } from "../../../../../util/types";
import ContainerModel from "../../../../../stores/models/ContainerModel";
import { type Location } from "../../../../../stores/definitions/Container";

export type TappedLocationData = {
  location: Location;
  number: number;
  point: {
    left: number;
    top: number;
  };
};

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
  number: number;
  location: Location;
  point: Point;
  onChange: (point: Point) => void;
  img: HTMLElement;
  onClick: (data: TappedLocationData) => void;
  editable: boolean;
  selected: boolean;
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
        const { left, top } = img.getBoundingClientRect();
        const tappedLocationData = {
          location,
          number,
          point: {
            left: point.x + left,
            top: point.y + top,
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

type ContentImageArgs = {
  editable?: boolean;
  onLocationTap?: (data: TappedLocationData) => void;
  onClearSelection?: () => void;
  selected?: TappedLocationData | null;
};

function ContentImage({
  editable = false,
  onLocationTap = () => {},
  onClearSelection = () => {},
  selected = null,
}: ContentImageArgs): React.ReactNode {
  const { searchStore } = useStores();
  const activeResult = searchStore.activeResult;
  if (!activeResult || !(activeResult instanceof ContainerModel))
    throw new Error("ActiveResult must be a Container");
  const [img, setImg] = useState<HTMLElement | null>(null);
  const [tappedLocation, setTappedLocation] =
    useState<TappedLocationData | null>(null);

  useLayoutEffect(() => {
    setTappedLocation(selected);
  }, [selected]);

  // Observe changes to the size of the image
  const [imageDimensions, setImageDimensions] = useState<{
    width: number;
    height: number;
    left: number;
    top: number;
  } | null>(null);
  const resizeObserver = useRef(
    new ResizeObserver((entries) => {
      const { width, height, left, top } =
        entries[0].target.getBoundingClientRect();
      setImageDimensions({ width, height, left, top });
    }),
  );
  const imgRef = useRef<HTMLElement | null>(null);

  useLayoutEffect(() => {
    const currentImgRef = imgRef.current;
    const currentResizeObserver = resizeObserver.current;

    if (currentImgRef) {
      currentResizeObserver.observe(currentImgRef);
    }
    return () => {
      if (currentImgRef) currentResizeObserver.unobserve(currentImgRef);
    };
  }, [img]);

  const newMarker = (event: React.MouseEvent<HTMLImageElement>) => {
    if (!editable) {
      return;
    }

    if (tappedLocation) {
      setTappedLocation(null);
      onClearSelection();
      return;
    }

    const { x, y } = event.currentTarget.getBoundingClientRect();
    const tappedLocationRelativeToViewport = {
      x: event.clientX,
      y: event.clientY,
    };
    if (!imageDimensions) throw new Error("Image dimensions are not known");
    const { width: imageWidth, height: imageHeight } = imageDimensions;
    const newPoint = {
      x: clamp(
        tappedLocationRelativeToViewport.x - x,
        iconDimension / 2,
        imageWidth - iconDimension / 2,
      ),
      y: clamp(
        tappedLocationRelativeToViewport.y - y,
        iconDimension,
        imageHeight,
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
        locations: [...(activeResult.locations ?? []), newLocation],
      });
    }
  };

  const locationMoved = (indexInSorted: number, { x, y }: Point) => {
    if (editable) {
      if (activeResult.sortedLocations == null)
        throw new Error("activeResult does not have locations");
      const locations = activeResult.sortedLocations;
      const location = locations[indexInSorted];
      if (!imageDimensions) throw new Error("Image dimensions are not known");
      const { width: imageWidth, height: imageHeight } = imageDimensions;
      location.coordX = Math.round(
        ((x + locationMarkerOffset.x) * 1000) / imageWidth,
      );
      location.coordY = Math.round(
        ((y + locationMarkerOffset.y) * 1000) / imageHeight,
      );

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
    }: { width: number; height: number },
    location: Location,
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
      <Grid container sx={{ justifyContent: "center", alignItems: "center" }}>
        <Grid
          sx={{
            position: "relative",
            mt: "15px",
            cursor: editable ? "crosshair" : "default",
          }}
        >
          <Box
            component="img"
            src={activeResult.locationsImage || ""}
            sx={{
              padding: 0,
              width: "auto",
              height: "auto",
              maxWidth: "100%",
              userSelect: "none",
            }}
            onLoad={({ target }) => setImg(target as HTMLElement)}
            onClick={newMarker}
            ref={imgRef as React.RefObject<HTMLImageElement>}
          />
          {img &&
            imageDimensions &&
            Array.isArray(activeResult.sortedLocations) &&
            activeResult.sortedLocations
              .map((l: Location) => normalizeCoords(imageDimensions, l))
              .map(
                (
                  {
                    location,
                    point,
                  }: {
                    location: Location;
                    point: { x: number; y: number };
                  },
                  index: number,
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
                ),
              )}
        </Grid>
      </Grid>
    )
  );
}

export default observer(ContentImage);
