/*
 * ====  A POINT ABOUT THE IMPORTS  ===========================================
 *
 *  This is a public page, so the user may not be authenticated. As such, this
 *  module, and any module that is imported, MUST NOT import anything from the
 *  global Inventory stores (i.e. from ../../stores/stores/*). If it does, then
 *  the page will be rendered as a blank screen and there will be an unhelpful
 *  error message on the browser's console saying that a module export could not
 *  be initialised. For more information, see the README in this directory.
 *
 * ============================================================================
 */

import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import { type Theme, ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import { type ReactElement, type ReactNode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import IGSNlogo from "../../assets/graphics/IGSNlogo.jpg";
import { decodeTagString } from "../../components/Tags/ParseEncodedTagStrings";
import Description from "../../Inventory/components/Fields/Description";
import MapViewer from "../../Inventory/components/Fields/Identifiers/MapViewer";
import Tags from "../../Inventory/components/Fields/Tags";
import type { Identifier, IdentifierAttrs } from "../../stores/definitions/Identifier";
import type { Tag } from "../../stores/definitions/Tag";
import { truncateIsoTimestamp } from "../../stores/definitions/Units";
import IdentifierModel from "../../stores/models/IdentifierModel";
import materialTheme from "../../theme";
import { Optional } from "../../util/optional";
import { capitaliseJustFirstChar } from "../../util/Util";
import AlwaysNewWindowNavigationContext from "../AlwaysNewWindowNavigationContext";
import NoValue from "../NoValue";
import VisuallyHiddenHeading from "../VisuallyHiddenHeading";

const STYLED_DL_SX = (theme: Theme) => ({
  fontSize: "0.85rem",
  margin: theme.spacing(1, 0),
  "& dt": { color: theme.palette.text.secondary, fontWeight: "600" },
  "& dd": { margin: 0, marginTop: theme.spacing(0.5) },
});

const ROW_SX = (theme: Theme) => ({
  width: "100%",
  padding: theme.spacing(0.5, 2, 0.5, 2),
  alignItems: "flex-start",
});

const COLUMN_SX = (theme: Theme) => ({
  ...ROW_SX(theme),
  flexDirection: "column" as const,
});

const LABEL_SX = { width: "230px", fontWeight: "bold" as const };

type DividedPairArgs = {
  children: [ReactElement<"dt">, ReactElement<"dd">, ReactElement<"dt">, ReactElement<"dd">];
};

function DividedPair({ children }: DividedPairArgs) {
  return (
    <Grid container direction="row" spacing={1} sx={{ mb: 1 }}>
      <Grid size={1}></Grid>
      <Grid>
        <span>
          {children[0]}
          {children[1]}
        </span>
      </Grid>
      <Grid>
        <Divider orientation="vertical" role="presentation" />
      </Grid>
      <Grid>
        <span>
          {children[2]}
          {children[3]}
        </span>
      </Grid>
    </Grid>
  );
}

export const INSTITUTION_LOGO_ADDRESS = "/public/banner";

const IGSN_BASE_URL = `https://www.igsn.org/`;
const formatDegrees = (value: string): string => `${value}˚`;

/**
 * importing from IdentifierModel would not work
 * as RootStore n/a to unauthenticated user
 * so we replicate some functionality and logic
 *
 */
const RECOMMENDED_FIELDS_LABELS: Record<string, string> = {
  type: "Type",
  freeType: "Type",
  subjectScheme: "Subject Scheme",
  schemeURI: "Scheme URI",
  valueURI: "Value URI",
  classificationCode: "Classification Code",
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const subFields = (fValue: any): Array<{ key: string; value: string }> =>
  Object.entries(fValue)
    .filter((item) => item[0] !== "value" && item[0] !== "type")
    .map((item) => {
      return { key: item[0], value: String(item[1]) };
    });

type PolygonPoint = {
  pointLatitude: string;
  pointLongitude: string;
};

type GeoLocationBox = {
  eastBoundLongitude: string;
  northBoundLatitude: string;
  southBoundLatitude: string;
  westBoundLongitude: string;
};

const glPointComplete = (point: PolygonPoint): boolean => {
  return Object.values(point).every((v) => v !== "");
};

const glBoxComplete = (box: GeoLocationBox): boolean => {
  return Object.values(box).every((v) => v !== "");
};

type IdentifierDataGridArgs = {
  identifier: Identifier;
  record: {
    description: string | null;
    tags: Array<Tag>;
    fields?: Array<{
      name: string;
      type: string;
      id: number;
      content: string | null;
      selectedOptions: Array<string> | null;
    }>;
    extraFields?: Array<{
      name: string;
      id: number | null;
      content: string;
    }>;
  };
};

export const IdentifierDataGrid = ({ record, identifier }: IdentifierDataGridArgs): ReactNode => {
  const { t } = useTranslation("public");
  const institutionName: string = identifier.publisher.split(" (")[0];

  const anyRecommendedGiven: boolean = [
    identifier.subjects,
    identifier.descriptions,
    identifier.alternateIdentifiers,
    identifier.dates,
    identifier.geoLocations,
  ].some((r) => Array.isArray(r) && r.length > 0); // groups could be returned as null

  return (
    <Grid container sx={{ fontFamily: "Arial" }}>
      <VisuallyHiddenHeading variant="h1">{identifier.title}</VisuallyHiddenHeading>
      <Grid
        aria-hidden={true}
        container
        sx={(theme) => ({
          ...ROW_SX(theme),
          backgroundColor: "#e3f0ff",
          borderBottom: "2px solid black",
        })}
        spacing={0}
      >
        <Grid
          sx={(theme) => ({
            backgroundColor: "#fff",
            margin: theme.spacing(0.5, 2, 0.5, 0.5),
            padding: theme.spacing(0.5),
            borderRadius: theme.spacing(0.75),
            border: "2px solid #eee",
          })}
        >
          <img
            src={INSTITUTION_LOGO_ADDRESS}
            alt={t("images.institutionLogoAlt")}
            title={t("images.institutionLogoAlt")}
            style={{ maxHeight: "78px", maxWidth: "255px" }}
          />
        </Grid>
        <Grid>
          <Typography component="h3" variant="h6" sx={(theme) => ({ color: theme.palette.primary.main })}>
            {t("headings.rspacePublicPages")}
          </Typography>
          <h2>{institutionName}</h2>
        </Grid>
      </Grid>
      <Grid
        container
        aria-hidden={true}
        direction="row"
        sx={(theme) => ({
          width: "auto",
          alignItems: "flex-start",
          backgroundColor: "#e3f0ff",
          margin: theme.spacing(3, 2, 0, 2),
          padding: theme.spacing(0, 1, 1, 0),
          borderRadius: theme.spacing(0.75),
          border: "2px solid black",
        })}
        spacing={2}
      >
        <Grid>
          <Stack spacing={0.25}>
            <h3>{identifier.title}</h3>
            <Typography variant="body1">
              {identifier.publicUrl ? (
                <a href={identifier.publicUrl} title={t("links.itemLandingPage")}>
                  {identifier.doi}
                </a>
              ) : (
                identifier.doi
              )}
            </Typography>
          </Stack>
        </Grid>
        <Grid>
          <Grid container sx={{ flexDirection: "column", alignItems: "center" }} spacing={0.5}>
            <Grid>
              <a href={IGSN_BASE_URL} title={t("links.igsnHomepage")} target="_blank" rel="noreferrer">
                <img
                  src={IGSNlogo}
                  alt={t("images.igsnLogoAlt")}
                  title={t("images.igsnLogoAlt")}
                  style={{ padding: "0 4px", width: "70px" }}
                />
              </a>
            </Grid>
            <Grid>
              <Typography variant="caption">
                {identifier.resourceTypeGeneral === "PhysicalObject"
                  ? t("resourceTypes.physicalObject")
                  : identifier.resourceTypeGeneral}
              </Typography>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid>
          <h2>{t("headings.general")}</h2>
        </Grid>
      </Grid>
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid sx={LABEL_SX}>{t("labels.name")}</Grid>
        <Grid>{identifier.title}</Grid>
      </Grid>
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid sx={LABEL_SX}>{t("labels.igsnId")}</Grid>
        <Grid data-testid="identifier-public-url">
          {identifier.publicUrl ? (
            <a href={identifier.publicUrl} title={t("links.doiAddress")}>
              {identifier.publicUrl}
            </a>
          ) : (
            <em>{t("labels.urlAvailableAfterPublishing")}</em>
          )}
        </Grid>
      </Grid>
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid sx={LABEL_SX}>{t("labels.resourceType")}</Grid>
        <Grid data-testid="identifier-resource-type">{identifier.resourceType}</Grid>
      </Grid>
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid sx={LABEL_SX}>{t("labels.creator")}</Grid>
        <Grid>{identifier.creatorName}</Grid>
      </Grid>
      {identifier.creatorAffiliation && (
        <Grid container direction="row" sx={ROW_SX} spacing={1}>
          <Grid sx={LABEL_SX}>{t("labels.creatorAffiliation")}</Grid>
          <Grid>{identifier.creatorAffiliation}</Grid>
        </Grid>
      )}
      {identifier.creatorAffiliationIdentifier && (
        <Grid container direction="row" sx={ROW_SX} spacing={1}>
          <Grid sx={LABEL_SX}>{t("labels.creatorAffiliationIdentifier")}</Grid>
          <Grid>
            <a target="_blank" rel="noreferrer" href={identifier.creatorAffiliationIdentifier}>
              {identifier.creatorAffiliationIdentifier}
            </a>
          </Grid>
        </Grid>
      )}
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid sx={LABEL_SX}>{t("labels.organisation")}</Grid>
        <Grid>{identifier.publisher}</Grid>
      </Grid>
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid sx={LABEL_SX}>{t("labels.publicationYear")}</Grid>
        <Grid>{identifier.publicationYear}</Grid>
      </Grid>
      {anyRecommendedGiven && (
        <>
          <Grid container direction="row" sx={ROW_SX} spacing={1}>
            <Grid>
              <h2>{t("headings.optionalFields")}</h2>
            </Grid>
          </Grid>
          {Array.isArray(identifier.subjects) && identifier.subjects.length > 0 && (
            <Grid container sx={COLUMN_SX} spacing={1} role="group" aria-label={t("labels.subjects")}>
              <Grid>
                <h3>{t("headings.subjects")}</h3>
              </Grid>
              {identifier.subjects.map((s) => (
                <Grid sx={ROW_SX} key={s.value}>
                  <Grid sx={{ margin: "8px" }}>{s.value}</Grid>
                  {subFields(s).length > 0 &&
                    subFields(s).map((sf) => (
                      <Grid container direction="row" sx={ROW_SX} spacing={1} key={sf.key}>
                        <Grid sx={LABEL_SX}>{RECOMMENDED_FIELDS_LABELS[sf.key]}</Grid>
                        <Grid>
                          {sf.value ? (
                            sf.value
                          ) : (
                            <Typography variant="inherit" component="em" sx={{ color: "#949494" }}>
                              {t("values.none")}
                            </Typography>
                          )}
                        </Grid>
                      </Grid>
                    ))}
                </Grid>
              ))}
            </Grid>
          )}
          {Array.isArray(identifier.descriptions) && identifier.descriptions.length > 0 && (
            <Grid container sx={COLUMN_SX} spacing={1} role="group" aria-label={t("labels.descriptions")}>
              <Grid>
                <h3>{t("headings.descriptions")}</h3>
              </Grid>
              {identifier.descriptions.map((d) => (
                <Grid container direction="row" sx={ROW_SX} spacing={1} key={d.value}>
                  <Grid sx={LABEL_SX}>{capitaliseJustFirstChar(d.type.toLowerCase())}</Grid>
                  <Grid>{d.value}</Grid>
                </Grid>
              ))}
            </Grid>
          )}
          {Array.isArray(identifier.alternateIdentifiers) && identifier.alternateIdentifiers.length > 0 && (
            <Grid container sx={COLUMN_SX} spacing={1} role="group" aria-label={t("labels.alternateIdentifiers")}>
              <Grid>
                <h3>{t("headings.alternateIdentifiers")}</h3>
              </Grid>
              {identifier.alternateIdentifiers.map((id) => (
                <Grid container direction="row" sx={ROW_SX} spacing={1} key={id.value}>
                  <Grid sx={{ marginBottom: "8px" }}>{id.value}</Grid>
                  {subFields(id).length > 0 &&
                    subFields(id).map((sf) => (
                      <Grid container direction="row" sx={ROW_SX} spacing={1} key={sf.key}>
                        <Grid sx={LABEL_SX}>{RECOMMENDED_FIELDS_LABELS[sf.key]}</Grid>
                        <Grid>{sf.value ? sf.value : <em>{t("values.none")}</em>}</Grid>
                      </Grid>
                    ))}
                </Grid>
              ))}
            </Grid>
          )}
          {Array.isArray(identifier.dates) && identifier.dates.length > 0 && (
            <Grid container sx={COLUMN_SX} spacing={1} role="group" aria-label={t("labels.dates")}>
              <Grid>
                <h3>{t("headings.dates")}</h3>
              </Grid>
              {identifier.dates.map((d, i) => (
                <Grid container direction="row" sx={ROW_SX} spacing={1} key={`${d.value.toString()}-${i}`}>
                  <Grid sx={LABEL_SX}>{capitaliseJustFirstChar(d.type.toLowerCase())}</Grid>
                  <Grid>{truncateIsoTimestamp(d.value, "date").orElse(t("values.invalidDate"))}</Grid>
                </Grid>
              ))}
            </Grid>
          )}
          {Array.isArray(identifier.geoLocations) && identifier.geoLocations.length > 0 && (
            <>
              <h3>{t("headings.geolocations")}</h3>
              <Grid container direction="row" spacing={1} role="group" aria-label={t("labels.geoLocations")}>
                {identifier.geoLocations.map((gl, i) => (
                  <Grid key={i}>
                    <Card variant="outlined">
                      <CardMedia>
                        <MapViewer
                          point={gl.geoLocationPoint}
                          box={gl.geoLocationBox}
                          polygon={gl.geoLocationPolygon}
                        />
                      </CardMedia>
                      <CardContent>
                        {glPointComplete(gl.geoLocationPoint) && (
                          <>
                            <Typography component="h4" variant="h6">
                              {t("geolocation.pointHeading")}
                            </Typography>
                            <Box component="dl" sx={STYLED_DL_SX}>
                              <DividedPair>
                                <dt>{t("geolocation.latitude")}</dt>
                                <dd>{formatDegrees(gl.geoLocationPoint.pointLatitude)}</dd>
                                <dt>{t("geolocation.longitude")}</dt>
                                <dd>{formatDegrees(gl.geoLocationPoint.pointLongitude)}</dd>
                              </DividedPair>
                            </Box>
                          </>
                        )}
                        {gl.geoLocationPlace && (
                          <>
                            <Typography component="h4" variant="h6">
                              {t("geolocation.placeHeading")}
                            </Typography>
                            <Box component="dl" sx={STYLED_DL_SX}>
                              <Grid container direction="row" spacing={1}>
                                <Grid size={1}></Grid>
                                <Grid size={11}>
                                  <span>
                                    <dt>{t("geolocation.description")}</dt>
                                    <dd>{gl.geoLocationPlace}</dd>
                                  </span>
                                </Grid>
                              </Grid>
                            </Box>
                          </>
                        )}
                        {glBoxComplete(gl.geoLocationBox) && (
                          <>
                            <Typography component="h4" variant="h6">
                              {t("geolocation.boxHeading")}
                            </Typography>
                            <Box component="dl" sx={STYLED_DL_SX}>
                              {/* width style is used to align vertical dividers */}
                              <DividedPair>
                                <dt style={{ minWidth: "140px" }}>{t("geolocation.northboundLatitude")}</dt>
                                <dd>{formatDegrees(gl.geoLocationBox.northBoundLatitude)}</dd>
                                <dt>{t("geolocation.westboundLongitude")}</dt>
                                <dd>{formatDegrees(gl.geoLocationBox.westBoundLongitude)}</dd>
                              </DividedPair>
                              <DividedPair>
                                <dt style={{ minWidth: "140px" }}>{t("geolocation.southboundLatitude")}</dt>
                                <dd>{formatDegrees(gl.geoLocationBox.southBoundLatitude)}</dd>
                                <dt>{t("geolocation.eastboundLongitude")}</dt>
                                <dd>{formatDegrees(gl.geoLocationBox.eastBoundLongitude)}</dd>
                              </DividedPair>
                            </Box>
                          </>
                        )}
                        {gl.geoLocationPolygon.isValid && (
                          <>
                            <Typography component="h4" variant="h6">
                              {t("geolocation.polygonHeading")}
                            </Typography>
                            <Box component="dl" sx={STYLED_DL_SX}>
                              {gl.geoLocationPolygon.mapPoints((point: PolygonPoint, index: number) => (
                                <DividedPair key={index}>
                                  <dt>{t("geolocation.pointLatitude", { index: index + 1 })}</dt>
                                  <dd>{formatDegrees(point.pointLatitude)}</dd>
                                  <dt>{t("geolocation.pointLongitude", { index: index + 1 })}</dt>
                                  <dd>{formatDegrees(point.pointLongitude)}</dd>
                                </DividedPair>
                              ))}
                            </Box>
                            {glPointComplete(gl.geoLocationInPolygonPoint) && (
                              <>
                                <Typography component="h4" variant="h6">
                                  {t("geolocation.inPolygonPointHeading")}
                                </Typography>
                                <Box component="dl" sx={STYLED_DL_SX}>
                                  <DividedPair>
                                    <dt>{t("geolocation.latitude")}</dt>
                                    <dd>{formatDegrees(gl.geoLocationInPolygonPoint.pointLatitude)}</dd>
                                    <dt>{t("geolocation.longitude")}</dt>
                                    <dd>{formatDegrees(gl.geoLocationInPolygonPoint.pointLongitude)}</dd>
                                  </DividedPair>
                                </Box>
                              </>
                            )}
                          </>
                        )}
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            </>
          )}
        </>
      )}
      {identifier.customFieldsOnPublicPage && (
        <>
          <Grid container direction="row" sx={ROW_SX} spacing={1}>
            <Grid>
              <h2>{t("headings.inventory")}</h2>
            </Grid>
          </Grid>
          <Grid container sx={COLUMN_SX} spacing={2}>
            <Grid>
              <Description
                fieldOwner={{
                  isFieldEditable: () => false,
                  fieldValues: {
                    description: record.description,
                  },
                  setFieldsDirty: () => {},
                  canChooseWhichToEdit: false,
                  setFieldEditable: () => {},
                  noValueLabel: { description: "" },
                }}
                onErrorStateChange={() => {}}
              />
            </Grid>
            <Grid>
              <Tags
                fieldOwner={{
                  isFieldEditable: () => false,
                  fieldValues: {
                    tags: record.tags,
                  },
                  setFieldsDirty: () => {},
                  canChooseWhichToEdit: false,
                  setFieldEditable: () => {},
                  noValueLabel: { tags: "" },
                }}
              />
            </Grid>
            {typeof record.fields !== "undefined" && (
              <Grid>
                <Typography variant="h6" component="h3">
                  {t("headings.customFields")}
                </Typography>
                {record.fields.length === 0 ? (
                  <NoValue label={t("values.none")} />
                ) : (
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>{t("table.name")}</TableCell>
                          <TableCell>{t("table.value")}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {record.fields
                          .filter((f) =>
                            [
                              "choice",
                              "date",
                              "number",
                              "radio",
                              "string",
                              "text",
                              "uri",
                              "time",
                              "reference",
                            ].includes(f.type),
                          )
                          .map((f) => (
                            <TableRow key={f.id}>
                              <TableCell>{f.name}</TableCell>
                              <TableCell>
                                {(f.selectedOptions?.join(", ") ?? f.content?.toString()) || (
                                  <NoValue label={t("values.none")} />
                                )}
                              </TableCell>
                            </TableRow>
                          ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </Grid>
            )}
            {typeof record.extraFields !== "undefined" && (
              <Grid>
                <Typography variant="h6" component="h3">
                  {t("headings.extraFields")}
                </Typography>
                {record.extraFields.length === 0 ? (
                  <NoValue label={t("values.none")} />
                ) : (
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>{t("table.name")}</TableCell>
                          <TableCell>{t("table.value")}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {record.extraFields.map((f) => (
                          /* id is null if previewing public page before saving new extra fields */
                          <TableRow key={(f.id ?? "") + f.name}>
                            <TableCell>{f.name}</TableCell>
                            <TableCell>{f.content}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </Grid>
            )}
          </Grid>
        </>
      )}
      <Grid container direction="row" sx={ROW_SX} spacing={1}>
        <Grid>
          <h2>{t("headings.otherInformation")}</h2>
        </Grid>
      </Grid>
      <Grid
        container
        direction="row"
        sx={(theme) => ({
          ...ROW_SX(theme),
          marginBottom: theme.spacing(1),
        })}
        spacing={1}
      >
        <Grid>
          {t("footer.contactInfo", { institution: institutionName })}
          <br />
          <br />
          {t("footer.generatedBy", { institution: institutionName })}
        </Grid>
      </Grid>
    </Grid>
  );
};

type IdentifierPublicPageArgs = {
  publicId: string;
};

const IdentifierPublicPage = ({ publicId }: IdentifierPublicPageArgs): ReactNode => {
  const { t } = useTranslation("public");
  const [fetching, setFetching] = useState(false);
  const [publicData, setPublicData] = useState<{
    identifiers: Array<Identifier>;
    description: string | null;
    tags: Array<Tag>;
    fields?: Array<{
      id: number;
      name: string;
      type: string;
      content: string | null;
      selectedOptions: Array<string> | null;
    }>;
    extraFields?: Array<{
      id: number | null;
      name: string;
      content: string;
    }>;
  } | null>(null);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    async function fetchPublicData(): Promise<void> {
      try {
        setFetching(true);
        // anonymous call
        const { data } = await axios.get<{
          identifiers: Array<IdentifierAttrs>;
          description: string | null;
          tags: Array<{
            value: string;
            ontologyName: string;
            uri: string;
            ontologyVersion: string;
          }>;
          fields?: Array<{
            id: number;
            type: string;
            name: string;
            content: string | null;
            selectedOptions: Array<string> | null;
          }>;
        }>(`/api/inventory/v1/public/view/${publicId}`);
        setPublicData({
          ...data,
          identifiers: data.identifiers.map((x) => new IdentifierModel(x, publicId)),
          tags: data.tags.map((tag) => ({
            value: decodeTagString(tag.value),
            uri: tag.uri === "" ? Optional.empty<string>() : Optional.present(decodeTagString(tag.uri)),
            vocabulary:
              tag.ontologyName === "" ? Optional.empty<string>() : Optional.present(decodeTagString(tag.ontologyName)),
            version:
              tag.ontologyVersion === ""
                ? Optional.empty<string>()
                : Optional.present(decodeTagString(tag.ontologyVersion)),
          })),
        });
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      } catch (e: any) {
        setErrorMessage(e.response.data.message);
        throw new Error(e);
      } finally {
        setFetching(false);
      }
    }
    void fetchPublicData();
  }, [publicId]);

  if (fetching) return null;
  if (!publicData)
    return (
      <>
        <h1>{t("headings.rspacePublicPages")}</h1>
        <p>{errorMessage}</p>
      </>
    );

  return (
    <ThemeProvider theme={materialTheme}>
      <AlwaysNewWindowNavigationContext>
        <IdentifierDataGrid record={publicData} identifier={publicData.identifiers[0]} />
      </AlwaysNewWindowNavigationContext>
    </ThemeProvider>
  );
};

window.addEventListener("load", (_e) => {
  const domContainer = document.getElementById("identifierPublicPage");
  const location = window.location;
  /**
   * expected path is `/public/inventory/${rsPublicId}`
   * rsPublicId is also included in the fetched response.data
   *
   */
  const rsPublicId = location.pathname.split("/")[3];
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
      <MuiCssLayerProvider>
        <I18nRoot namespaces={["public"]}>
          <IdentifierPublicPage publicId={rsPublicId} />
        </I18nRoot>
      </MuiCssLayerProvider>,
    );
  }
});

export default observer(IdentifierPublicPage);
