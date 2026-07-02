import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import type { ComponentType, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import docLinks from "../../../../assets/DocLinks";
import AddButton from "../../../../components/AddButton";
import HelpLinkIcon from "../../../../components/HelpLinkIcon";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import RemoveButton from "../../../../components/RemoveButton";
import type { GeoLocation, PolygonPoint } from "../../../../stores/definitions/GeoLocation";
import AmberNumberField from "./AmberNumberField";
import {
  COORD_RANGE_X,
  COORD_RANGE_Y,
  isOutOfRangeX,
  isOutOfRangeY,
  type PolygonMessages,
  PolygonStateAlert,
} from "./GeoLocationField";
import { isEmpty } from "./MultipleInputHandler";

const PolygonEditor = observer(
  ({
    geoLocation,
    editable,
    doUpdateIdentifiers,
  }: {
    geoLocation: GeoLocation;
    editable: boolean;
    doUpdateIdentifiers: () => void;
  }): ReactNode => {
    const { t } = useTranslation("inventory");
    const { geoLocationPolygon, polygonEmpty }: GeoLocation = geoLocation;

    /* in some cases points cannot be removed, or added */
    const canBeRemoved = (i: number): boolean =>
      editable && geoLocationPolygon.length > 4 && i > 0 && i < geoLocationPolygon.length - 1;
    const canBeAdded = (i: number): boolean => editable && i < geoLocationPolygon.length - 1;

    const handleAddPoint = (i: number): void => {
      geoLocationPolygon.addAnotherPoint(i);
      doUpdateIdentifiers();
    };
    const handleRemovePoint = (i: number): void => {
      geoLocationPolygon.removePoint(i);
      doUpdateIdentifiers();
    };

    const polygonPointLatitudeError = (point: PolygonPoint): boolean =>
      (isEmpty(point.pointLatitude) && !polygonEmpty) || isOutOfRangeY(Number(point.pointLatitude));
    const polygonPointLongitudeError = (point: PolygonPoint): boolean =>
      (isEmpty(point.pointLongitude) && !polygonEmpty) || isOutOfRangeX(Number(point.pointLongitude));

    return geoLocationPolygon.mapPoints((point, i) => (
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
        <Grid
          size={{
            md: 5,
          }}
        >
          <InputWrapper label={t("fields.identifiers.polygonCard.pointLatitude", { pointNumber: i + 1 })}>
            {editable && i < geoLocationPolygon.length - 1 ? (
              <AmberNumberField
                slotProps={{ htmlInput: { ...COORD_RANGE_Y } }}
                size="small"
                variant="standard"
                fullWidth
                data-test-id={`Polygon-point-${i}-latitude`}
                disabled={false}
                value={point.pointLatitude ?? ""}
                placeholder={t("fields.identifiers.polygonCard.enterPointLatitude")}
                onChange={({ target: { value } }) => {
                  geoLocationPolygon.set(i, "pointLatitude", value);
                  doUpdateIdentifiers();
                }}
                /* value is required for any polygon point (if at least another value is specified) */
                error={polygonPointLatitudeError(point)}
                helperText={
                  polygonPointLatitudeError(point) ? t("fields.identifiers.geoLocationField.latitudeRange") : null
                }
              />
            ) : (
              /* last point is edited by editing first */
              point.pointLatitude || (
                <Typography variant="inherit" component="span" sx={{ color: "#949494" }}>
                  {"-"}
                </Typography>
              )
            )}
          </InputWrapper>
        </Grid>
        <Grid
          size={{
            md: 5,
          }}
        >
          <InputWrapper label={t("fields.identifiers.polygonCard.pointLongitude", { pointNumber: i + 1 })}>
            {editable && i < geoLocationPolygon.length - 1 ? (
              <AmberNumberField
                slotProps={{ htmlInput: { ...COORD_RANGE_X } }}
                size="small"
                variant="standard"
                fullWidth
                data-test-id={`Polygon-point-${i + 1}-longitude`}
                disabled={false}
                value={point.pointLongitude ?? ""}
                placeholder={t("fields.identifiers.polygonCard.enterPointLongitude")}
                onChange={({ target: { value } }) => {
                  geoLocationPolygon.set(i, "pointLongitude", value);
                  doUpdateIdentifiers();
                }}
                /* value is required for any polygon point (if at least another value is specified) */
                error={polygonPointLongitudeError(point)}
                helperText={
                  polygonPointLongitudeError(point) ? t("fields.identifiers.geoLocationField.longitudeRange") : null
                }
              />
            ) : (
              /* last point is edited by editing first */
              point.pointLongitude || (
                <Typography variant="inherit" component="span" sx={{ color: "#949494" }}>
                  {"-"}
                </Typography>
              )
            )}
          </InputWrapper>
        </Grid>
        <Grid
          size={{
            md: 1,
          }}
        >
          {canBeAdded(i) ? (
            <AddButton
              onClick={() => handleAddPoint(i)}
              title={t("fields.identifiers.polygonCard.addPointAfter", { pointNumber: i + 1 })}
            />
          ) : (
            " "
          )}
        </Grid>
        <Grid
          size={{
            md: 1,
          }}
        >
          {canBeRemoved(i) ? (
            <RemoveButton
              onClick={() => handleRemovePoint(i)}
              title={t("fields.identifiers.polygonCard.removePoint", { pointNumber: i + 1 })}
            />
          ) : (
            " "
          )}
        </Grid>
      </Grid>
    ));
  },
);

type PolygonCardArgs = {
  editable: boolean;
  geoLocation: GeoLocation;
  doUpdateIdentifiers: () => void;
};

function PolygonCard({ editable, geoLocation, doUpdateIdentifiers }: PolygonCardArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const polygonCardMessages: PolygonMessages = {
    empty: t("fields.identifiers.polygonCard.state.empty"),
    incomplete: t("fields.identifiers.polygonCard.state.incomplete"),
    complete: t("fields.identifiers.polygonCard.state.complete"),
  };
  const InPolygonPointEditor = observer((): ReactNode => {
    const { geoLocationInPolygonPoint, inPolygonPointIncomplete }: GeoLocation = geoLocation;
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
        <Grid
          size={{
            md: 5,
          }}
        >
          <InputWrapper label={t("fields.identifiers.polygonCard.inPolygonPointLatitude")}>
            {editable ? (
              <AmberNumberField
                slotProps={{ htmlInput: { ...COORD_RANGE_Y } }}
                size="small"
                variant="standard"
                fullWidth
                data-test-id={`In-polygon-point-latitude`}
                disabled={false}
                value={geoLocationInPolygonPoint.pointLatitude}
                placeholder={t("fields.identifiers.polygonCard.enterPointLatitude")}
                onChange={({ target: { value } }) => {
                  runInAction(() => {
                    geoLocationInPolygonPoint.pointLatitude = value;
                  });
                  doUpdateIdentifiers();
                }}
                /* value is optional */
                error={
                  (isEmpty(geoLocationInPolygonPoint.pointLatitude) && inPolygonPointIncomplete) ||
                  isOutOfRangeY(Number(geoLocationInPolygonPoint.pointLatitude))
                }
                helperText={
                  (isEmpty(geoLocationInPolygonPoint.pointLatitude) && inPolygonPointIncomplete) ||
                  isOutOfRangeY(Number(geoLocationInPolygonPoint.pointLatitude))
                    ? t("fields.identifiers.geoLocationField.latitudeRange")
                    : null
                }
              />
            ) : (
              geoLocationInPolygonPoint.pointLatitude || (
                <Typography variant="inherit" component="span" sx={{ color: "#949494" }}>
                  {"-"}
                </Typography>
              )
            )}
          </InputWrapper>
        </Grid>
        <Grid
          size={{
            md: 5,
          }}
        >
          <InputWrapper label={t("fields.identifiers.polygonCard.inPolygonPointLongitude")}>
            {editable ? (
              <AmberNumberField
                slotProps={{ htmlInput: { ...COORD_RANGE_X } }}
                size="small"
                variant="standard"
                fullWidth
                data-test-id={`In-polygon-point-longitude`}
                disabled={false}
                value={geoLocationInPolygonPoint.pointLongitude ?? ""}
                placeholder={t("fields.identifiers.polygonCard.enterPointLongitude")}
                onChange={({ target: { value } }) => {
                  runInAction(() => {
                    geoLocationInPolygonPoint.pointLongitude = value;
                  });
                  doUpdateIdentifiers();
                }}
                /* value is optional */
                error={
                  (isEmpty(geoLocationInPolygonPoint.pointLongitude) && inPolygonPointIncomplete) ||
                  isOutOfRangeX(Number(geoLocationInPolygonPoint.pointLongitude))
                }
                helperText={
                  (isEmpty(geoLocationInPolygonPoint.pointLongitude) && inPolygonPointIncomplete) ||
                  isOutOfRangeX(Number(geoLocationInPolygonPoint.pointLongitude))
                    ? t("fields.identifiers.geoLocationField.longitudeRange")
                    : null
                }
              />
            ) : (
              geoLocationInPolygonPoint.pointLongitude || (
                <Typography variant="inherit" component="span" sx={{ color: "#949494" }}>
                  {"-"}
                </Typography>
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
            {editable
              ? t("fields.identifiers.polygonCard.editorTitle")
              : t("fields.identifiers.polygonCard.configurationTitle")}
            <HelpLinkIcon link={docLinks.IGSNIdentifiers} title={t("fields.identifiers.polygonCard.helpTitle")} />
          </FormLabel>
          <FormHelperText component="div" sx={{ mx: 0, mt: 1 }}>
            {t("fields.identifiers.polygonCard.polygonDescription")}
          </FormHelperText>
          <Box sx={{ my: 1 }}>
            <PolygonStateAlert
              polygonEmpty={polygonEmpty}
              polygonComplete={geoLocation.geoLocationPolygon.isValid}
              textMessages={polygonCardMessages}
            />
          </Box>
          <PolygonEditor geoLocation={geoLocation} editable={editable} doUpdateIdentifiers={doUpdateIdentifiers} />
          <FormHelperText component="div" sx={{ mx: 0, mt: 1, mb: 0.5 }}>
            {t("fields.identifiers.polygonCard.inPolygonPointDescription")}
          </FormHelperText>
          <InPolygonPointEditor />
        </FormControl>
      </CardContent>
    </Card>
  );
}

export default observer(PolygonCard) as ComponentType<PolygonCardArgs>;
