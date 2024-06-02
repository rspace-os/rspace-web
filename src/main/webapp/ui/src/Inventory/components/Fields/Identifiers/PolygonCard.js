//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import { runInAction } from "mobx";
import Box from "@mui/material/Box";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import {
  type GeoLocation,
  type PolygonPoint,
} from "../../../../stores/definitions/GeoLocation";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import docLinks from "../../../../assets/DocLinks";
import HelpLinkIcon from "../../../../components/HelpLinkIcon";
import FormHelperText from "@mui/material/FormHelperText";
import Grid from "@mui/material/Grid";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import AmberNumberField from "./AmberNumberField";
import AddButton from "../../../../components/AddButton";
import RemoveButton from "../../../../components/RemoveButton";
import { isEmpty } from "./MultipleInputHandler";
import {
  COORD_RANGE_X,
  isOutOfRangeX,
  COORD_RANGE_Y,
  isOutOfRangeY,
  PolygonStateAlert,
  type PolygonMessages,
} from "./GeoLocationField";
import { polygonComplete } from "../../../../stores/models/GeoLocationModel";

const POLYGON_CARD_MESSAGES: PolygonMessages = {
  empty: "An empty Polygon will not be included in the Geolocation.",
  incomplete:
    "All points values need to be completed in order for the Polygon to be used.",
  complete:
    "All points are completed, the Polygon can be included in the Geolocation.",
};

const PolygonEditor = observer(
  ({
    geoLocation,
    editable,
    doUpdateIdentifiers,
  }: {|
    geoLocation: GeoLocation,
    editable: boolean,
    doUpdateIdentifiers: () => void,
  |}): Node => {
    const { geoLocationPolygon, polygonEmpty }: GeoLocation = geoLocation;

    /* in some cases points cannot be removed, or added */
    const canBeRemoved = (i: number): boolean =>
      editable &&
      geoLocationPolygon.length > 4 &&
      i > 0 &&
      i < geoLocationPolygon.length - 1;
    const canBeAdded = (i: number): boolean =>
      editable && i < geoLocationPolygon.length - 1;

    const handleAddPoint = (i: number): void => {
      geoLocationPolygon.splice(i + 1, 0, {
        polygonPoint: { pointLatitude: "", pointLongitude: "" },
      });
      doUpdateIdentifiers();
    };
    const handleRemovePoint = (i: number): void => {
      geoLocationPolygon.splice(i, 1);
      doUpdateIdentifiers();
    };

    const polygonPointLatitudeError = (point: PolygonPoint): boolean =>
      (isEmpty(point.pointLatitude) && !polygonEmpty) ||
      isOutOfRangeY(Number(point.pointLatitude));
    const polygonPointLongitudeError = (point: PolygonPoint): boolean =>
      (isEmpty(point.pointLongitude) && !polygonEmpty) ||
      isOutOfRangeX(Number(point.pointLongitude));

    return geoLocationPolygon
      .map((item) => item.polygonPoint)
      .map((point, i) => (
        <Grid
          key={i}
          container
          direction="row"
          spacing={1}
          sx={{
            alignItems: "center",
            width: "100%",
          }}
        >
          <Grid item md={5}>
            <InputWrapper label={`Point ${i + 1} Latitude`}>
              {editable && i < geoLocationPolygon.length - 1 ? (
                <AmberNumberField
                  inputProps={{ ...COORD_RANGE_Y }}
                  size="small"
                  variant="standard"
                  fullWidth
                  datatestid={`Polygon-point-${i}-latitude`}
                  disabled={false}
                  value={point.pointLatitude ?? ""}
                  placeholder="Enter Point Latitude"
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      point.pointLatitude = value;
                    });
                    doUpdateIdentifiers();
                  }}
                  /* value is required for any polygon point (if at least another value is specified) */
                  error={polygonPointLatitudeError(point)}
                  helperText={
                    polygonPointLatitudeError(point) ? (
                      <>Between &minus;90.0˚ and 90.0˚.</>
                    ) : null
                  }
                />
              ) : (
                /* last point is edited by editing first */
                point.pointLatitude || (
                  <span style={{ color: "#949494" }}>-</span>
                )
              )}
            </InputWrapper>
          </Grid>
          <Grid item md={5}>
            <InputWrapper label={`Point ${i + 1} Longitude`}>
              {editable && i < geoLocationPolygon.length - 1 ? (
                <AmberNumberField
                  inputProps={{ ...COORD_RANGE_X }}
                  size="small"
                  variant="standard"
                  fullWidth
                  datatestid={`Polygon-point-${i + 1}-longitude`}
                  disabled={false}
                  value={point.pointLongitude ?? ""}
                  placeholder="Enter Point Longitude"
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      point.pointLongitude = value;
                    });
                    doUpdateIdentifiers();
                  }}
                  /* value is required for any polygon point (if at least another value is specified) */
                  error={polygonPointLongitudeError(point)}
                  helperText={
                    polygonPointLongitudeError(point) ? (
                      <>Between &minus;180.0˚ and 180.0˚.</>
                    ) : null
                  }
                />
              ) : (
                /* last point is edited by editing first */
                point.pointLongitude || (
                  <span style={{ color: "#949494" }}>-</span>
                )
              )}
            </InputWrapper>
          </Grid>
          <Grid item md={1}>
            {canBeAdded(i) ? (
              <AddButton
                onClick={() => handleAddPoint(i)}
                title={`Add Point after ${i + 1}`}
              />
            ) : (
              <>&nbsp;</>
            )}
          </Grid>
          <Grid item md={1}>
            {canBeRemoved(i) ? (
              <RemoveButton
                onClick={() => handleRemovePoint(i)}
                title={`Remove Point ${i + 1}`}
              />
            ) : (
              <>&nbsp;</>
            )}
          </Grid>
        </Grid>
      ));
  }
);

type PolygonCardArgs = {|
  editable: boolean,
  geoLocation: GeoLocation,
  doUpdateIdentifiers: () => void,
|};

function PolygonCard({
  editable,
  geoLocation,
  doUpdateIdentifiers,
}: PolygonCardArgs): Node {
  const InPolygonPointEditor = observer((): Node => {
    const { geoLocationInPolygonPoint, inPolygonPointIncomplete }: GeoLocation =
      geoLocation;
    return (
      <Grid
        container
        direction="row"
        spacing={1}
        sx={{
          alignItems: "center",
          width: "100%",
        }}
      >
        <Grid item md={5}>
          <InputWrapper label={`In Polygon Point Latitude`}>
            {editable ? (
              <AmberNumberField
                inputProps={{ ...COORD_RANGE_Y }}
                size="small"
                variant="standard"
                fullWidth
                datatestid={`In-polygon-point-latitude`}
                disabled={false}
                value={geoLocationInPolygonPoint.pointLatitude}
                placeholder="Enter Point Latitude"
                onChange={({ target: { value } }) => {
                  runInAction(() => {
                    geoLocationInPolygonPoint.pointLatitude = value;
                  });
                  doUpdateIdentifiers();
                }}
                /* value is optional */
                error={
                  (isEmpty(geoLocationInPolygonPoint.pointLatitude) &&
                    inPolygonPointIncomplete) ||
                  isOutOfRangeY(Number(geoLocationInPolygonPoint.pointLatitude))
                }
                helperText={
                  (isEmpty(geoLocationInPolygonPoint.pointLatitude) &&
                    inPolygonPointIncomplete) ||
                  isOutOfRangeY(
                    Number(geoLocationInPolygonPoint.pointLatitude)
                  ) ? (
                    <>Between &minus;90.0˚ and 90.0˚.</>
                  ) : null
                }
              />
            ) : (
              geoLocationInPolygonPoint.pointLatitude || (
                <span style={{ color: "#949494" }}>-</span>
              )
            )}
          </InputWrapper>
        </Grid>
        <Grid item md={5}>
          <InputWrapper label={`In Polygon Point Longitude`}>
            {editable ? (
              <AmberNumberField
                inputProps={{ ...COORD_RANGE_X }}
                size="small"
                variant="standard"
                fullWidth
                datatestid={`In-polygon-point-longitude`}
                disabled={false}
                value={geoLocationInPolygonPoint.pointLongitude ?? ""}
                placeholder="Enter Point Longitude"
                onChange={({ target: { value } }) => {
                  runInAction(() => {
                    geoLocationInPolygonPoint.pointLongitude = value;
                  });
                  doUpdateIdentifiers();
                }}
                /* value is optional */
                error={
                  (isEmpty(geoLocationInPolygonPoint.pointLongitude) &&
                    inPolygonPointIncomplete) ||
                  isOutOfRangeX(
                    Number(geoLocationInPolygonPoint.pointLongitude)
                  )
                }
                helperText={
                  (isEmpty(geoLocationInPolygonPoint.pointLongitude) &&
                    inPolygonPointIncomplete) ||
                  isOutOfRangeX(
                    Number(geoLocationInPolygonPoint.pointLongitude)
                  ) ? (
                    <>Between &minus;180.0˚ and 180.0˚.</>
                  ) : null
                }
              />
            ) : (
              geoLocationInPolygonPoint.pointLongitude || (
                <span style={{ color: "#949494" }}>-</span>
              )
            )}
          </InputWrapper>
        </Grid>
      </Grid>
    );
  });

  const { polygonEmpty }: GeoLocation = geoLocation;
  return (
    <Card elevation={0} variant="outlined">
      <CardContent sx={{ pt: 1 }}>
        <FormControl component="fieldset" fullWidth>
          <FormLabel>
            {`Polygon ${editable ? "Editor" : "Configuration"}`}
            <HelpLinkIcon
              link={docLinks.IGSNIdentifiers}
              title="Add a Polygon to your IGSN ID Geolocation"
            />
          </FormLabel>
          <FormHelperText component="div" sx={{ mx: 0, mt: 1 }}>
            You can add a Polygon to a Geolocation associated with an IGSN ID. A
            Polygon is made of 4 or more points that form a closed shape. The
            first and last points have the same coordinates, editing the first
            point will automatically update the last one.
          </FormHelperText>
          <Box sx={{ my: 1 }}>
            <PolygonStateAlert
              polygonEmpty={polygonEmpty}
              polygonComplete={polygonComplete(geoLocation.geoLocationPolygon)}
              textMessages={POLYGON_CARD_MESSAGES}
            />
          </Box>
          <PolygonEditor
            geoLocation={geoLocation}
            editable={editable}
            doUpdateIdentifiers={doUpdateIdentifiers}
          />
          <FormHelperText component="div" sx={{ mx: 0, mt: 1, mb: 0.5 }}>
            Optional: you can specify an In Polygon Point below. This is only
            required if the Polygon covers more than the half of the
            Earth&apos;s surface.
          </FormHelperText>
          <InPolygonPointEditor />
        </FormControl>
      </CardContent>
    </Card>
  );
}

export default (observer(PolygonCard): ComponentType<PolygonCardArgs>);
