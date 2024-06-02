// @flow

import React, {
  type ComponentType,
  type Node,
  useState,
  Suspense,
  lazy,
} from "react";
import { observer } from "mobx-react-lite";
import GeoLocationModel, {
  polygonComplete,
} from "../../../../stores/models/GeoLocationModel";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import Alert from "@mui/material/Alert";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import FormControl from "@mui/material/FormControl";
import PolygonDialog from "./PolygonDialog";
import Typography from "@mui/material/Typography";
import IconButton from "@mui/material/IconButton";
import HexagonIcon from "@mui/icons-material/Hexagon";
import Card from "@mui/material/Card";
import CardMedia from "@mui/material/CardMedia";
import CardContent from "@mui/material/CardContent";
import Divider from "@mui/material/Divider";
import { withStyles } from "Styles";
import Skeleton from "@mui/material/Skeleton";
import Button from "@mui/material/Button";
import AmberNumberField from "./AmberNumberField";
import { runInAction } from "mobx";

/**
 * The MapViewer component is lazy loaded in so that the code isn't downloaded by the user's browser
 * until they visit an Inventory record that has an Identifier. The conditional logic for whether
 * LazyMapViewer is rendered is not inside this component, and thus the lazy rendering is dependent on
 * the conditional logic of the parent components.
 */
const LazyMapViewer = lazy(() => import("./MapViewer"));

const CustomFieldset = withStyles<{| children: Node |}, { root: string }>(
  (theme) => ({
    root: {
      border: theme.borders.card,
      margin: 0,
      borderRadius: theme.spacing(0.5),
      padding: theme.spacing(2),
      paddingTop: 0,
      "& > legend": {
        padding: theme.spacing(0.25, 1),
      },
    },
  })
)(({ classes, children }) => (
  <fieldset className={classes.root}>{children}</fieldset>
));

export const COORD_RANGE_X = { min: -180, max: 180 };
export const isOutOfRangeX = (v: number): boolean =>
  v < COORD_RANGE_X.min || v > COORD_RANGE_X.max;
export const COORD_RANGE_Y = { min: -90, max: 90 };
export const isOutOfRangeY = (v: number): boolean =>
  v < COORD_RANGE_Y.min || v > COORD_RANGE_Y.max;

export type PolygonMessages = {
  empty: string,
  incomplete: string,
  complete: string,
};

const POLYGON_FIELD_MESSAGES: PolygonMessages = {
  empty: "No Polygon set yet.",
  incomplete: "An incomplete Polygon is currently set.",
  complete: "A complete Polygon is currently set.",
};

export const PolygonStateAlert = ({
  polygonEmpty,
  polygonComplete,
  textMessages,
}: {
  polygonEmpty: boolean,
  polygonComplete: boolean,
  textMessages: PolygonMessages,
}): Node => {
  if (polygonEmpty) return <Alert severity="info">{textMessages.empty}</Alert>;
  if (polygonComplete)
    return <Alert severity="info">{textMessages.complete}</Alert>;
  return <Alert severity="warning">{textMessages.incomplete}</Alert>;
};

type GeoLocationFieldArgs = {|
  geoLocation: GeoLocationModel,
  i: number,
  editable: boolean,
  handleUpdateValue: (number, string, string | Date) => void,
  doUpdateIdentifiers: () => void,
|};

const GeoLocationField = ({
  geoLocation,
  i,
  editable,
  handleUpdateValue,
  doUpdateIdentifiers,
}: GeoLocationFieldArgs) => {
  const PointLatitudeEditor = observer((): Node => {
    const { geoLocationPoint }: GeoLocationModel = geoLocation;
    return (
      <InputWrapper label="Latitude">
        <AmberNumberField
          inputProps={{
            ...COORD_RANGE_Y,
          }}
          placeholder="e.g. 51.478"
          size="small"
          variant="standard"
          fullWidth
          datatestid={`IdentifierRecommendedField-${"geolocation-point-latitude"}-${i}`}
          disabled={false}
          value={geoLocationPoint.pointLatitude}
          onChange={({ target: { value } }) => {
            runInAction(() => {
              geoLocationPoint.pointLatitude = value;
            });
            doUpdateIdentifiers();
          }}
          error={
            (geoLocation.pointIncomplete &&
              geoLocationPoint.pointLatitude === "") ||
            isOutOfRangeY(Number(geoLocationPoint.pointLatitude))
          }
          helperText={
            geoLocation.pointIncomplete &&
            geoLocationPoint.pointLatitude === "" ? (
              "Add value to enable publishing."
            ) : (
              <>Between &minus;90.0˚ and 90.0˚.</>
            )
          }
        />
      </InputWrapper>
    );
  });

  const PointLongitudeEditor = observer((): Node => {
    const { geoLocationPoint }: GeoLocationModel = geoLocation;
    return (
      <InputWrapper label="Longitude">
        <AmberNumberField
          inputProps={{
            ...COORD_RANGE_X,
          }}
          placeholder="e.g. 0.0"
          size="small"
          variant="standard"
          fullWidth
          datatestid={`IdentifierRecommendedField-${"geolocation-point-longitude"}-${i}`}
          disabled={false}
          value={geoLocationPoint.pointLongitude}
          onChange={({ target: { value } }) => {
            runInAction(() => {
              geoLocationPoint.pointLongitude = value;
            });
            doUpdateIdentifiers();
          }}
          error={
            (geoLocation.pointIncomplete &&
              geoLocationPoint.pointLongitude === "") ||
            isOutOfRangeX(Number(geoLocationPoint.pointLongitude))
          }
          helperText={
            geoLocation.pointIncomplete &&
            geoLocationPoint.pointLongitude === "" ? (
              "Add value to enable publishing."
            ) : (
              <>Between &minus;180.0˚ and 180.0˚.</>
            )
          }
        />
      </InputWrapper>
    );
  });

  const PointEditor = observer((): Node => {
    return (
      <Grid item>
        <CustomFieldset>
          <legend>Point</legend>
          <Grid container direction="row" spacing={1}>
            <Grid item xs={6}>
              <PointLatitudeEditor />
            </Grid>
            <Grid item xs={6}>
              <PointLongitudeEditor />
            </Grid>
          </Grid>
        </CustomFieldset>
      </Grid>
    );
  });

  const PlaceEditor = observer((): Node => {
    const { geoLocationPlace }: GeoLocationModel = geoLocation;
    return (
      <Grid item>
        <CustomFieldset>
          <legend>Place</legend>
          <InputWrapper label="Description">
            <TextField
              size="small"
              placeholder="e.g. Royal Observatory, Greenwich"
              variant="standard"
              fullWidth
              datatestid={`IdentifierRecommendedField-${"geolocation-place"}-${i}`}
              disabled={false}
              value={geoLocationPlace}
              onChange={({ target: { value } }) => {
                handleUpdateValue(i, "geoLocationPlace", value);
              }}
              error={false}
              helperText={""}
            />
          </InputWrapper>
        </CustomFieldset>
      </Grid>
    );
  });

  const BoxEditor = observer((): Node => {
    const { geoLocationBox }: GeoLocationModel = geoLocation;
    return (
      <Grid item>
        <CustomFieldset>
          <legend>Box</legend>
          <Grid container direction="row" spacing={1}>
            <Grid item xs={6}>
              <InputWrapper label="North Latitude">
                <AmberNumberField
                  inputProps={COORD_RANGE_Y}
                  size="small"
                  variant="standard"
                  datatestid={`IdentifierRecommendedField-${"geolocation-box-northbound"}-${i}`}
                  value={geoLocationBox.northBoundLatitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.northBoundLatitude = value;
                    });
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete &&
                      geoLocationBox.northBoundLatitude === "") ||
                    isOutOfRangeY(Number(geoLocationBox.northBoundLatitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete &&
                    geoLocationBox.northBoundLatitude === "" ? (
                      "Add value to enable publishing."
                    ) : (
                      <>Between &minus;90.0˚ and 90.0˚.</>
                    )
                  }
                />
              </InputWrapper>
            </Grid>
            <Grid item xs={6}>
              <InputWrapper label="West Longitude">
                <AmberNumberField
                  inputProps={COORD_RANGE_X}
                  size="small"
                  variant="standard"
                  datatestid={`IdentifierRecommendedField-${"geolocation-box-westbound"}-${i}`}
                  value={geoLocationBox.westBoundLongitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.westBoundLongitude = value;
                    });
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete &&
                      geoLocationBox.westBoundLongitude === "") ||
                    isOutOfRangeX(Number(geoLocationBox.westBoundLongitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete &&
                    geoLocationBox.westBoundLongitude === "" ? (
                      "Add value to enable publishing."
                    ) : (
                      <>Between &minus;180.0˚ and 180.0˚.</>
                    )
                  }
                />
              </InputWrapper>
            </Grid>
            <Grid item xs={6}>
              <InputWrapper label="South Latitude">
                <AmberNumberField
                  inputProps={COORD_RANGE_Y}
                  size="small"
                  variant="standard"
                  datatestid={`IdentifierRecommendedField-${"geolocation-box-southbound"}-${i}`}
                  value={geoLocationBox.southBoundLatitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.southBoundLatitude = value;
                    });
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete &&
                      geoLocationBox.southBoundLatitude === "") ||
                    isOutOfRangeY(Number(geoLocationBox.southBoundLatitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete &&
                    geoLocationBox.southBoundLatitude === "" ? (
                      "Add value to enable publishing."
                    ) : (
                      <>Between &minus;90.0˚ and 90.0˚.</>
                    )
                  }
                />
              </InputWrapper>
            </Grid>
            <Grid item xs={6}>
              <InputWrapper label="East Longitude">
                <AmberNumberField
                  inputProps={COORD_RANGE_X}
                  size="small"
                  variant="standard"
                  datatestid={`IdentifierRecommendedField-${"geolocation-box-eastbound"}-${i}`}
                  value={geoLocationBox.eastBoundLongitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.eastBoundLongitude = value;
                    });
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete &&
                      geoLocationBox.eastBoundLongitude === "") ||
                    isOutOfRangeX(Number(geoLocationBox.eastBoundLongitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete &&
                    geoLocationBox.eastBoundLongitude === "" ? (
                      "Add value to enable publishing."
                    ) : (
                      <>Between &minus;180.0˚ and 180.0˚.</>
                    )
                  }
                />
              </InputWrapper>
            </Grid>
          </Grid>
        </CustomFieldset>
      </Grid>
    );
  });

  const PolygonBlock = observer(
    ({
      setOpenPolygonDialog,
    }: {
      setOpenPolygonDialog: (boolean) => void,
    }): Node => {
      const { polygonEmpty }: GeoLocationModel = geoLocation;
      return (
        <Grid item onClick={() => setOpenPolygonDialog(true)}>
          <CustomFieldset>
            <legend>Polygon</legend>
            <Grid container direction="row" alignItems="center">
              <IconButton
                title={"Open Polygon Dialog"}
                onClick={() => setOpenPolygonDialog(true)}
              >
                <HexagonIcon color="primary" aria-label="hexagon icon" />
              </IconButton>
              <Typography component="span" variant="body2">
                Create or Edit a Geolocation Polygon
              </Typography>
            </Grid>
            <PolygonStateAlert
              polygonEmpty={polygonEmpty}
              polygonComplete={polygonComplete(geoLocation.geoLocationPolygon)}
              textMessages={POLYGON_FIELD_MESSAGES}
            />
          </CustomFieldset>
        </Grid>
      );
    }
  );

  const GeoLocationEditor = observer(
    ({
      setOpenPolygonDialog,
    }: {
      setOpenPolygonDialog: (boolean) => void,
    }): Node => {
      return (
        <Card variant="outlined">
          <CardContent>
            <Alert severity="info">
              At least one of four elements must be completed (Point, Place,
              Box, Polygon). Point, Box and Polygon cannot be completed
              partially.
            </Alert>
            <FormControl sx={{ width: "100%" }}>
              <Grid container direction="column" spacing={1}>
                <PointEditor />
                <PlaceEditor />
                <BoxEditor />
                <PolygonBlock setOpenPolygonDialog={setOpenPolygonDialog} />
              </Grid>
            </FormControl>
          </CardContent>
        </Card>
      );
    }
  );

  const GeoLocationPreview = observer(
    ({
      setOpenPolygonDialog,
    }: {
      setOpenPolygonDialog: (boolean) => void,
    }): Node => {
      const {
        geoLocationPoint,
        geoLocationPlace,
        geoLocationBox,
        polygonEmpty,
      }: GeoLocationModel = geoLocation;
      return (
        <Card variant="outlined">
          <CardMedia>
            <Suspense
              fallback={
                <Skeleton
                  variant="rectangular"
                  width="100%"
                  height={200}
                  sx={{ mb: 1 }}
                />
              }
            >
              <LazyMapViewer
                point={geoLocation.geoLocationPoint}
                box={geoLocation.geoLocationBox}
                polygon={geoLocation.geoLocationPolygon}
              />
            </Suspense>
          </CardMedia>
          <Divider orientation="horizontal" variant="middle" />
          <CardContent sx={{ pt: 1 }}>
            <Grid container direction="column" spacing={1}>
              <Grid item>
                <fieldset
                  style={{
                    border: "1px solid rgba(0, 0, 0, 0.12)",
                    margin: 0,
                    borderRadius: "4px",
                    padding: 16,
                    paddingTop: 0,
                  }}
                >
                  <legend style={{ padding: "2px 8px" }}>Point</legend>
                  <Grid container direction="row" spacing={1}>
                    <Grid item xs={6}>
                      <InputWrapper label="Latitude">
                        {geoLocationPoint.pointLatitude
                          ? String(geoLocationPoint.pointLatitude) + "˚"
                          : "-"}
                      </InputWrapper>
                    </Grid>
                    <Grid item xs={6}>
                      <InputWrapper label="Longitude">
                        {geoLocationPoint.pointLongitude
                          ? String(geoLocationPoint.pointLongitude) + "˚"
                          : "-"}
                      </InputWrapper>
                    </Grid>
                  </Grid>
                </fieldset>
              </Grid>
              <Grid item>
                <fieldset
                  style={{
                    border: "1px solid rgba(0, 0, 0, 0.12)",
                    margin: 0,
                    borderRadius: "4px",
                    padding: 16,
                    paddingTop: 0,
                  }}
                >
                  <legend style={{ padding: "2px 8px" }}>Place</legend>
                  <InputWrapper label="Description">
                    {geoLocationPlace ? (
                      geoLocationPlace
                    ) : (
                      <em style={{ color: "#949494" }}>None</em>
                    )}
                  </InputWrapper>
                </fieldset>
              </Grid>
              <Grid item>
                <fieldset
                  style={{
                    border: "1px solid rgba(0, 0, 0, 0.12)",
                    margin: 0,
                    borderRadius: "4px",
                    padding: 16,
                    paddingTop: 0,
                  }}
                >
                  <legend style={{ padding: "2px 8px" }}>Box</legend>
                  <Grid container direction="row" spacing={1}>
                    <Grid item xs={6}>
                      <InputWrapper label="North Latitude">
                        {geoLocationBox.northBoundLatitude
                          ? String(geoLocationBox.northBoundLatitude) + "˚"
                          : "-"}
                      </InputWrapper>
                    </Grid>
                    <Grid item xs={6}>
                      <InputWrapper label="West Longitude">
                        {geoLocationBox.westBoundLongitude
                          ? String(geoLocationBox.westBoundLongitude) + "˚"
                          : "-"}
                      </InputWrapper>
                    </Grid>
                    <Grid item xs={6}>
                      <InputWrapper label="South Latitude">
                        {geoLocationBox.southBoundLatitude
                          ? String(geoLocationBox.southBoundLatitude) + "˚"
                          : "-"}
                      </InputWrapper>
                    </Grid>
                    <Grid item xs={6}>
                      <InputWrapper label="East Longitude">
                        {geoLocationBox.eastBoundLongitude
                          ? String(geoLocationBox.eastBoundLongitude) + "˚"
                          : "-"}
                      </InputWrapper>
                    </Grid>
                  </Grid>
                </fieldset>
              </Grid>
              <Grid item>
                <CustomFieldset>
                  <legend>Polygon</legend>
                  <Grid container direction="row">
                    {!polygonEmpty && (
                      <Button
                        onClick={() => setOpenPolygonDialog(true)}
                        color="primary"
                        variant="outlined"
                      >
                        View Polygon
                      </Button>
                    )}
                    <Typography component="span" variant="body2" sx={{ m: 1 }}>
                      {polygonEmpty
                        ? "To create a Polygon, press Edit first."
                        : "To modify it, press Edit first."}
                    </Typography>
                  </Grid>
                  <PolygonStateAlert
                    polygonEmpty={polygonEmpty}
                    polygonComplete={polygonComplete(
                      geoLocation.geoLocationPolygon
                    )}
                    textMessages={POLYGON_FIELD_MESSAGES}
                  />
                </CustomFieldset>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      );
    }
  );

  const [openPolygonDialog, setOpenPolygonDialog] = useState<boolean>(false);
  return (
    <Grid item sx={{ flexGrow: 1 }}>
      {editable ? (
        <GeoLocationEditor setOpenPolygonDialog={setOpenPolygonDialog} />
      ) : (
        <GeoLocationPreview setOpenPolygonDialog={setOpenPolygonDialog} />
      )}
      {openPolygonDialog && (
        <PolygonDialog
          open={openPolygonDialog}
          setOpen={setOpenPolygonDialog}
          editable={editable}
          geoLocation={geoLocation}
          doUpdateIdentifiers={doUpdateIdentifiers}
        />
      )}
    </Grid>
  );
};

export default (observer(
  GeoLocationField
): ComponentType<GeoLocationFieldArgs>);
