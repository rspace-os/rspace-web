//@flow

import React, { type Node, useState } from "react";
import "leaflet/dist/leaflet.css";
import {
  boxComplete,
  pointComplete,
} from "../../../../stores/models/GeoLocationModel";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import FormGroup from "@mui/material/FormGroup";
import Switch from "@mui/material/Switch";
import {
  MapContainer,
  TileLayer,
  Polygon,
  Circle,
  Rectangle,
} from "react-leaflet";
import { useTheme } from "@mui/material/styles";
import {
  type GeoLocationBox,
  type GeoLocationPolygon,
  type PolygonPoint,
} from "../../../../stores/definitions/GeoLocation";

type MapViewerArgs = {|
  point: PolygonPoint,
  box: GeoLocationBox,
  polygon: GeoLocationPolygon,
|};

/**
 * Displays a map with markers and regions for the point, box, and polygon geo location data.
 * Beneath the map are three switches, coloured to match the respective regions on the map
 * that they toggle. Whilst the information about which coloured region is describing point,
 * box, or polygon, because the user can toggle the regions on and off a colour blind user
 * will still be able to identify which region is which.
 *
 * The map data itself is sourced from open street maps by way of the library, leaflet. It does
 * not appear that open street maps has any usage limits nor restrictions on use for commercial
 * products unlike many of alternatives that were considered such as maptiler.
 *
 * An attempt was made to write a jest tests for this component, but it proved impossible
 * to do so because the react-leaflet library uses odd syntax that the jest runtime cannot
 * work with. There is some discussion online on how this can be resolved, but those steps
 * alone did not prove fruitful. For example, https://github.com/PaulLeCam/react-leaflet/issues/977
 */
export default function MapViewer({
  point,
  box,
  polygon,
}: MapViewerArgs): Node {
  const theme = useTheme();

  const [showPoint, setShowPoint] = useState<boolean>(pointComplete(point));
  const [showBox, setShowBox] = useState<boolean>(boxComplete(box));
  const [showPolygon, setShowPolygon] = useState<boolean>(polygon.isValid);

  const {
    eastBoundLongitude,
    northBoundLatitude,
    southBoundLatitude,
    westBoundLongitude,
  } = box;
  const boxToPolygon = [
    { latitude: northBoundLatitude, longitude: westBoundLongitude },
    { latitude: northBoundLatitude, longitude: eastBoundLongitude },
    { latitude: southBoundLatitude, longitude: eastBoundLongitude },
    { latitude: southBoundLatitude, longitude: westBoundLongitude },
    { latitude: northBoundLatitude, longitude: westBoundLongitude },
  ];

  /*
   * To center the map as closely as possible on all three parts of the geo location data,
   * we sum their latitudes and longitudes weighted by the number of points. Where no data
   * has yet been set, we default to (0,0)
   */

  let latitudeCenter = 0;
  let latitudeDataPoints = 0;
  if (pointComplete(point)) {
    if (!isNaN(parseFloat(point.pointLatitude))) {
      latitudeCenter += parseFloat(point.pointLatitude);
      latitudeDataPoints++;
    }
  }
  if (boxComplete(box)) {
    if (
      !isNaN(parseFloat(box.northBoundLatitude)) &&
      !isNaN(parseFloat(box.southBoundLatitude))
    ) {
      latitudeCenter +=
        0.5 * parseFloat(box.northBoundLatitude) +
        0.5 * parseFloat(box.southBoundLatitude);
      latitudeDataPoints++;
    }
  }
  if (polygon.isValid) {
    const sum = polygon
      .map(({ polygonPoint }) => parseFloat(polygonPoint.pointLatitude))
      .reduce((acc, lat) => acc + lat, 0);
    latitudeCenter += sum * (1 / polygon.length);
    latitudeDataPoints++;
  }
  latitudeCenter /= latitudeDataPoints;

  let longitudeCenter = 0;
  let longitudeDataPoints = 0;
  if (pointComplete(point)) {
    if (!isNaN(parseFloat(point.pointLongitude))) {
      longitudeCenter += parseFloat(point.pointLongitude);
      longitudeDataPoints++;
    }
  }
  if (boxComplete(box)) {
    if (
      !isNaN(parseFloat(box.eastBoundLongitude)) &&
      !isNaN(parseFloat(box.westBoundLongitude))
    ) {
      longitudeCenter +=
        0.5 * parseFloat(box.eastBoundLongitude) +
        0.5 * parseFloat(box.westBoundLongitude);
      longitudeDataPoints++;
    }
  }
  if (polygon.isValid) {
    const sum = polygon
      .map(({ polygonPoint }) => parseFloat(polygonPoint.pointLongitude))
      .reduce((acc, lat) => acc + lat, 0);
    longitudeCenter += sum * (1 / polygon.length);
    longitudeDataPoints++;
  }
  longitudeCenter /= longitudeDataPoints;

  return (
    <>
      <MapContainer
        center={[latitudeCenter || 0, longitudeCenter || 0]}
        zoom={15}
        scrollWheelZoom={false}
        style={{ height: "200px" }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {showPoint && (
          <Circle
            center={[point.pointLatitude, point.pointLongitude]}
            pathOptions={{
              fillColor: theme.palette.primary.main,
              fillOpacity: 1.0,
              stroke: false,
            }}
            radius={50}
          />
        )}
        {showBox && (
          <Rectangle
            pathOptions={{
              color: theme.palette.secondary.main,
              fillOpacity: 0.5,
              stroke: false,
            }}
            bounds={boxToPolygon.map(({ latitude, longitude }) => [
              latitude,
              longitude,
            ])}
          />
        )}
        {showPolygon && (
          /*
           * Note that this code assumes that the region described by the polygon is the one that it
           * encloses. If `geoLocation.geoLocationInPolygonPoint` is outside the enclosed region
           * then the polygon is actually describing the region on the outside but the map will not
           * show this.
           */
          <Polygon
            pathOptions={{
              color: theme.palette.tertiary.main,
              fillOpacity: 0.5,
              stroke: false,
            }}
            positions={polygon.map(
              ({
                polygonPoint: { pointLatitude: lat, pointLongitude: long },
              }) => [lat, long]
            )}
          />
        )}
      </MapContainer>

      <FormGroup>
        <Grid container direction="row" spacing={2} sx={{ px: 2 }}>
          <Grid item>
            <FormControlLabel
              control={
                <Switch
                  color="primary"
                  checked={showPoint}
                  disabled={!pointComplete(point)}
                  onChange={() => setShowPoint(!showPoint)}
                  inputProps={{ "aria-label": "show GL Point" }}
                />
              }
              label="Point"
            />
          </Grid>
          <Grid item>
            <FormControlLabel
              control={
                <Switch
                  color="secondary"
                  checked={showBox}
                  disabled={!boxComplete(box)}
                  onChange={() => setShowBox(!showBox)}
                  inputProps={{ "aria-label": "show GL box" }}
                />
              }
              label="Box"
            />
          </Grid>
          <Grid item>
            <FormControlLabel
              control={
                <Switch
                  color="tertiary"
                  checked={showPolygon}
                  disabled={!polygon.isValid}
                  onChange={() => setShowPolygon(!showPolygon)}
                  inputProps={{ "aria-label": "show GL Polygon" }}
                />
              }
              label="Polygon"
            />
          </Grid>
        </Grid>
      </FormGroup>
    </>
  );
}
