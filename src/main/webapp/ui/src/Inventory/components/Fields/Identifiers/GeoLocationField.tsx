import HexagonIcon from "@mui/icons-material/Hexagon";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import React, { type ComponentType, lazy, type ReactNode, Suspense, useState } from "react";
import { useTranslation } from "react-i18next";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import type GeoLocationModel from "../../../../stores/models/GeoLocationModel";
import AmberNumberField from "./AmberNumberField";
import PolygonDialog from "./PolygonDialog";

/**
 * The MapViewer component is lazy loaded in so that the code isn't downloaded by the user's browser
 * until they visit an Inventory record that has an Identifier. The conditional logic for whether
 * LazyMapViewer is rendered is not inside this component, and thus the lazy rendering is dependent on
 * the conditional logic of the parent components.
 */
const LazyMapViewer = lazy(() => import("./MapViewer"));

function CustomFieldset({ children }: { children: React.ReactNode }): ReactNode {
  return (
    <Box
      component="fieldset"
      sx={(theme) => ({
        border: theme.borders.card,
        margin: 0,
        borderRadius: theme.spacing(0.5),
        padding: theme.spacing(2),
        paddingTop: 0,
        "& > legend": {
          padding: theme.spacing(0.25, 1),
        },
      })}
    >
      {children}
    </Box>
  );
}

export const COORD_RANGE_X = { min: -180, max: 180 };
export const isOutOfRangeX = (v: number): boolean => v < COORD_RANGE_X.min || v > COORD_RANGE_X.max;
export const COORD_RANGE_Y = { min: -90, max: 90 };
export const isOutOfRangeY = (v: number): boolean => v < COORD_RANGE_Y.min || v > COORD_RANGE_Y.max;

export type PolygonMessages = {
  empty: string;
  incomplete: string;
  complete: string;
};

export const PolygonStateAlert = ({
  polygonEmpty,
  polygonComplete,
  textMessages,
}: {
  polygonEmpty: boolean;
  polygonComplete: boolean;
  textMessages: PolygonMessages;
}): ReactNode => {
  if (polygonEmpty) return <Alert severity="info">{textMessages.empty}</Alert>;
  if (polygonComplete) return <Alert severity="info">{textMessages.complete}</Alert>;
  return <Alert severity="warning">{textMessages.incomplete}</Alert>;
};

type GeoLocationFieldArgs = {
  geoLocation: GeoLocationModel;
  i: number;
  editable: boolean;
  handleUpdateValue: (index: number, key: string, value: string | Date) => void;
  doUpdateIdentifiers: () => void;
};

const GeoLocationField = ({
  geoLocation,
  i,
  editable,
  handleUpdateValue: _handleUpdateValue,
  doUpdateIdentifiers,
}: GeoLocationFieldArgs) => {
  const { t } = useTranslation("inventory");
  const polygonMessages: PolygonMessages = {
    empty: t("fields.identifiers.geoLocationField.polygon.noneYet"),
    incomplete: t("fields.identifiers.geoLocationField.polygon.incomplete"),
    complete: t("fields.identifiers.geoLocationField.polygon.complete"),
  };

  const PointLatitudeEditor = observer((): ReactNode => {
    const { geoLocationPoint } = geoLocation;
    return (
      <InputWrapper label={t("fields.identifiers.geoLocationField.point.latitude")}>
        <AmberNumberField
          slotProps={{
            htmlInput: {
              ...COORD_RANGE_Y,
            },
          }}
          placeholder={t("fields.identifiers.geoLocationField.point.latitudePlaceholder")}
          size="small"
          variant="standard"
          fullWidth
          data-test-id={`IdentifierRecommendedField-${"geolocation-point-latitude"}-${i}`}
          disabled={false}
          value={geoLocationPoint.pointLatitude}
          onChange={({ target: { value } }) => {
            runInAction(() => {
              geoLocationPoint.pointLatitude = value;
            });
          }}
          onBlur={() => {
            doUpdateIdentifiers();
          }}
          error={
            (geoLocation.pointIncomplete && geoLocationPoint.pointLatitude === "") ||
            isOutOfRangeY(Number(geoLocationPoint.pointLatitude))
          }
          helperText={
            geoLocation.pointIncomplete && geoLocationPoint.pointLatitude === ""
              ? t("fields.identifiers.geoLocationField.addValueToPublish")
              : t("fields.identifiers.geoLocationField.latitudeRange")
          }
        />
      </InputWrapper>
    );
  });

  const PointLongitudeEditor = observer((): ReactNode => {
    const { geoLocationPoint } = geoLocation;
    return (
      <InputWrapper label={t("fields.identifiers.geoLocationField.point.longitude")}>
        <AmberNumberField
          slotProps={{
            htmlInput: {
              ...COORD_RANGE_X,
            },
          }}
          placeholder={t("fields.identifiers.geoLocationField.point.longitudePlaceholder")}
          size="small"
          variant="standard"
          fullWidth
          data-test-id={`IdentifierRecommendedField-${"geolocation-point-longitude"}-${i}`}
          disabled={false}
          value={geoLocationPoint.pointLongitude}
          onChange={({ target: { value } }) => {
            runInAction(() => {
              geoLocationPoint.pointLongitude = value;
            });
          }}
          onBlur={() => {
            doUpdateIdentifiers();
          }}
          error={
            (geoLocation.pointIncomplete && geoLocationPoint.pointLongitude === "") ||
            isOutOfRangeX(Number(geoLocationPoint.pointLongitude))
          }
          helperText={
            geoLocation.pointIncomplete && geoLocationPoint.pointLongitude === ""
              ? t("fields.identifiers.geoLocationField.addValueToPublish")
              : t("fields.identifiers.geoLocationField.longitudeRange")
          }
        />
      </InputWrapper>
    );
  });

  const PointEditor = observer((): ReactNode => {
    return (
      <Grid>
        <CustomFieldset>
          <legend>{t("fields.identifiers.geoLocationField.point.title")}</legend>
          <Grid container direction="row" spacing={1}>
            <Grid size={6}>
              <PointLatitudeEditor />
            </Grid>
            <Grid size={6}>
              <PointLongitudeEditor />
            </Grid>
          </Grid>
        </CustomFieldset>
      </Grid>
    );
  });

  const PlaceEditor = observer((): ReactNode => {
    const { geoLocationPlace } = geoLocation;
    return (
      <Grid>
        <CustomFieldset>
          <legend>{t("fields.identifiers.geoLocationField.place.title")}</legend>
          <InputWrapper label={t("fields.identifiers.geoLocationField.description")}>
            <TextField
              size="small"
              placeholder={t("fields.identifiers.geoLocationField.place.placeholder")}
              variant="standard"
              fullWidth
              slotProps={{
                htmlInput: {
                  "data-test-id": `IdentifierRecommendedField-${"geolocation-place"}-${i}`,
                },
              }}
              disabled={false}
              value={geoLocationPlace}
              onChange={({ target: { value } }) => {
                runInAction(() => {
                  geoLocation.geoLocationPlace = value;
                });
              }}
              onBlur={() => {
                doUpdateIdentifiers();
              }}
              error={false}
              helperText={""}
            />
          </InputWrapper>
        </CustomFieldset>
      </Grid>
    );
  });

  const BoxEditor = observer((): ReactNode => {
    const { geoLocationBox } = geoLocation;
    return (
      <Grid>
        <CustomFieldset>
          <legend>{t("fields.identifiers.geoLocationField.box.title")}</legend>
          <Grid container direction="row" spacing={1}>
            <Grid size={6}>
              <InputWrapper label={t("fields.identifiers.geoLocationField.box.northLatitude")}>
                <AmberNumberField
                  slotProps={{ htmlInput: COORD_RANGE_Y }}
                  size="small"
                  variant="standard"
                  data-test-id={`IdentifierRecommendedField-${"geolocation-box-northbound"}-${i}`}
                  value={geoLocationBox.northBoundLatitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.northBoundLatitude = value;
                    });
                  }}
                  onBlur={() => {
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete && geoLocationBox.northBoundLatitude === "") ||
                    isOutOfRangeY(Number(geoLocationBox.northBoundLatitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete && geoLocationBox.northBoundLatitude === ""
                      ? t("fields.identifiers.geoLocationField.addValueToPublish")
                      : t("fields.identifiers.geoLocationField.latitudeRange")
                  }
                />
              </InputWrapper>
            </Grid>
            <Grid size={6}>
              <InputWrapper label={t("fields.identifiers.geoLocationField.box.westLongitude")}>
                <AmberNumberField
                  slotProps={{ htmlInput: COORD_RANGE_X }}
                  size="small"
                  variant="standard"
                  data-test-id={`IdentifierRecommendedField-${"geolocation-box-westbound"}-${i}`}
                  value={geoLocationBox.westBoundLongitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.westBoundLongitude = value;
                    });
                  }}
                  onBlur={() => {
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete && geoLocationBox.westBoundLongitude === "") ||
                    isOutOfRangeX(Number(geoLocationBox.westBoundLongitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete && geoLocationBox.westBoundLongitude === ""
                      ? t("fields.identifiers.geoLocationField.addValueToPublish")
                      : t("fields.identifiers.geoLocationField.longitudeRange")
                  }
                />
              </InputWrapper>
            </Grid>
            <Grid size={6}>
              <InputWrapper label={t("fields.identifiers.geoLocationField.box.southLatitude")}>
                <AmberNumberField
                  slotProps={{ htmlInput: COORD_RANGE_Y }}
                  size="small"
                  variant="standard"
                  data-test-id={`IdentifierRecommendedField-${"geolocation-box-southbound"}-${i}`}
                  value={geoLocationBox.southBoundLatitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.southBoundLatitude = value;
                    });
                  }}
                  onBlur={() => {
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete && geoLocationBox.southBoundLatitude === "") ||
                    isOutOfRangeY(Number(geoLocationBox.southBoundLatitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete && geoLocationBox.southBoundLatitude === ""
                      ? t("fields.identifiers.geoLocationField.addValueToPublish")
                      : t("fields.identifiers.geoLocationField.latitudeRange")
                  }
                />
              </InputWrapper>
            </Grid>
            <Grid size={6}>
              <InputWrapper label={t("fields.identifiers.geoLocationField.box.eastLongitude")}>
                <AmberNumberField
                  slotProps={{ htmlInput: COORD_RANGE_X }}
                  size="small"
                  variant="standard"
                  data-test-id={`IdentifierRecommendedField-${"geolocation-box-eastbound"}-${i}`}
                  value={geoLocationBox.eastBoundLongitude}
                  onChange={({ target: { value } }) => {
                    runInAction(() => {
                      geoLocationBox.eastBoundLongitude = value;
                    });
                  }}
                  onBlur={() => {
                    doUpdateIdentifiers();
                  }}
                  error={
                    (geoLocation.boxIncomplete && geoLocationBox.eastBoundLongitude === "") ||
                    isOutOfRangeX(Number(geoLocationBox.eastBoundLongitude))
                  }
                  helperText={
                    geoLocation.boxIncomplete && geoLocationBox.eastBoundLongitude === ""
                      ? t("fields.identifiers.geoLocationField.addValueToPublish")
                      : t("fields.identifiers.geoLocationField.longitudeRange")
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
    ({ setOpenPolygonDialog }: { setOpenPolygonDialog: (open: boolean) => void }): ReactNode => {
      const { polygonEmpty } = geoLocation;
      return (
        <Grid onClick={() => setOpenPolygonDialog(true)}>
          <CustomFieldset>
            <legend>{t("fields.identifiers.geoLocationField.polygon.title")}</legend>
            <Grid container direction="row" sx={{ alignItems: "center" }}>
              <IconButton
                title={t("fields.identifiers.geoLocationField.openPolygonDialog")}
                onClick={() => setOpenPolygonDialog(true)}
              >
                <HexagonIcon color="primary" aria-label={t("fields.identifiers.geoLocationField.hexagonIconLabel")} />
              </IconButton>
              <Typography component="span" variant="body2">
                {t("fields.identifiers.geoLocationField.createEditPolygon")}
              </Typography>
            </Grid>
            <PolygonStateAlert
              polygonEmpty={polygonEmpty}
              polygonComplete={geoLocation.geoLocationPolygon.isValid}
              textMessages={polygonMessages}
            />
          </CustomFieldset>
        </Grid>
      );
    },
  );

  const GeoLocationEditor = observer(
    ({ setOpenPolygonDialog }: { setOpenPolygonDialog: (open: boolean) => void }): ReactNode => {
      return (
        <Card variant="outlined">
          <CardContent>
            <Alert severity="info">{t("fields.identifiers.geoLocationField.alert")}</Alert>
            <FormControl sx={{ width: "100%" }}>
              <Stack spacing={1}>
                <PointEditor />
                <PlaceEditor />
                <BoxEditor />
                <PolygonBlock setOpenPolygonDialog={setOpenPolygonDialog} />
              </Stack>
            </FormControl>
          </CardContent>
        </Card>
      );
    },
  );

  const GeoLocationPreview = observer(
    ({ setOpenPolygonDialog }: { setOpenPolygonDialog: (open: boolean) => void }): ReactNode => {
      const { geoLocationPoint, geoLocationPlace, geoLocationBox, polygonEmpty } = geoLocation;
      return (
        <Card variant="outlined">
          <CardMedia>
            <Suspense fallback={<Skeleton variant="rectangular" width="100%" height={200} sx={{ mb: 1 }} />}>
              <LazyMapViewer
                point={geoLocation.geoLocationPoint}
                box={geoLocation.geoLocationBox}
                polygon={geoLocation.geoLocationPolygon}
              />
            </Suspense>
          </CardMedia>
          <Divider orientation="horizontal" variant="middle" />
          <CardContent sx={{ pt: 1 }}>
            <Stack spacing={1}>
              <Box
                component="fieldset"
                sx={{
                  border: "1px solid rgba(0, 0, 0, 0.12)",
                  margin: 0,
                  borderRadius: "4px",
                  padding: "16px",
                  paddingTop: 0,
                }}
              >
                <Box component="legend" sx={{ padding: "2px 8px" }}>
                  {t("fields.identifiers.geoLocationField.point.title")}
                </Box>
                <Grid container direction="row" spacing={1}>
                  <Grid size={6}>
                    <InputWrapper label={t("fields.identifiers.geoLocationField.point.latitude")}>
                      {geoLocationPoint.pointLatitude ? `${String(geoLocationPoint.pointLatitude)}˚` : "-"}
                    </InputWrapper>
                  </Grid>
                  <Grid size={6}>
                    <InputWrapper label={t("fields.identifiers.geoLocationField.point.longitude")}>
                      {geoLocationPoint.pointLongitude ? `${String(geoLocationPoint.pointLongitude)}˚` : "-"}
                    </InputWrapper>
                  </Grid>
                </Grid>
              </Box>
              <Box
                component="fieldset"
                sx={{
                  border: "1px solid rgba(0, 0, 0, 0.12)",
                  margin: 0,
                  borderRadius: "4px",
                  padding: "16px",
                  paddingTop: 0,
                }}
              >
                <Box component="legend" sx={{ padding: "2px 8px" }}>
                  {t("fields.identifiers.geoLocationField.place.title")}
                </Box>
                <InputWrapper label={t("fields.identifiers.geoLocationField.description")}>
                  {geoLocationPlace ? (
                    geoLocationPlace
                  ) : (
                    <Typography variant="inherit" component="em" sx={{ color: "#949494" }}>
                      {t("fields.identifiers.geoLocationField.none")}
                    </Typography>
                  )}
                </InputWrapper>
              </Box>
              <Box
                component="fieldset"
                sx={{
                  border: "1px solid rgba(0, 0, 0, 0.12)",
                  margin: 0,
                  borderRadius: "4px",
                  padding: "16px",
                  paddingTop: 0,
                }}
              >
                <Box component="legend" sx={{ padding: "2px 8px" }}>
                  {t("fields.identifiers.geoLocationField.box.title")}
                </Box>
                <Grid container direction="row" spacing={1}>
                  <Grid size={6}>
                    <InputWrapper label={t("fields.identifiers.geoLocationField.box.northLatitude")}>
                      {geoLocationBox.northBoundLatitude ? `${String(geoLocationBox.northBoundLatitude)}˚` : "-"}
                    </InputWrapper>
                  </Grid>
                  <Grid size={6}>
                    <InputWrapper label={t("fields.identifiers.geoLocationField.box.westLongitude")}>
                      {geoLocationBox.westBoundLongitude ? `${String(geoLocationBox.westBoundLongitude)}˚` : "-"}
                    </InputWrapper>
                  </Grid>
                  <Grid size={6}>
                    <InputWrapper label={t("fields.identifiers.geoLocationField.box.southLatitude")}>
                      {geoLocationBox.southBoundLatitude ? `${String(geoLocationBox.southBoundLatitude)}˚` : "-"}
                    </InputWrapper>
                  </Grid>
                  <Grid size={6}>
                    <InputWrapper label={t("fields.identifiers.geoLocationField.box.eastLongitude")}>
                      {geoLocationBox.eastBoundLongitude ? `${String(geoLocationBox.eastBoundLongitude)}˚` : "-"}
                    </InputWrapper>
                  </Grid>
                </Grid>
              </Box>
              <CustomFieldset>
                <legend>{t("fields.identifiers.geoLocationField.polygon.title")}</legend>
                <Stack direction="row">
                  {!polygonEmpty && (
                    <Button onClick={() => setOpenPolygonDialog(true)} color="callToAction" variant="outlined">
                      {t("fields.identifiers.geoLocationField.polygon.view")}
                    </Button>
                  )}
                  <Typography component="span" variant="body2" sx={{ m: 1 }}>
                    {polygonEmpty
                      ? t("fields.identifiers.geoLocationField.polygon.createFirst")
                      : t("fields.identifiers.geoLocationField.polygon.modifyFirst")}
                  </Typography>
                </Stack>
                <PolygonStateAlert
                  polygonEmpty={polygonEmpty}
                  polygonComplete={geoLocation.geoLocationPolygon.isValid}
                  textMessages={polygonMessages}
                />
              </CustomFieldset>
            </Stack>
          </CardContent>
        </Card>
      );
    },
  );

  const [openPolygonDialog, setOpenPolygonDialog] = useState<boolean>(false);
  return (
    <Grid sx={{ flexGrow: 1 }}>
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

export default observer(GeoLocationField) as ComponentType<GeoLocationFieldArgs>;
