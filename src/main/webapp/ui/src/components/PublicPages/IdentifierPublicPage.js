// @flow

/*
 * ====  A POINT ABOUT THE IMPORTS  ===========================================
 *
 *  This is a public page, so the user may not be authenticated. As such, this
 *  module, and any module that is imported, MUST NOT import anything from the
 *  global Inventory stores (i.e. from ../../stores/stores/*). If it does, then
 *  the page will be rendered as a blank screen and there will be an unhelpful
 *  error message on the browser's console saying that webpack export could not
 *  be initialised. For more information, see the README in this directory.
 *
 * ============================================================================
 */
import React, {
  useState,
  useEffect,
  type Node,
  type ComponentType,
  type Element,
} from "react";
import { createRoot } from "react-dom/client";
import { observer } from "mobx-react-lite";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import clsx from "clsx";
import { makeStyles } from "tss-react/mui";
import axios from "@/common/axios";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import IGSNlogo from "../../assets/graphics/IGSNlogo.jpg";
import { capitaliseJustFirstChar } from "../../util/Util";
import MapViewer from "../../Inventory/components/Fields/Identifiers/MapViewer";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Divider from "@mui/material/Divider";
import {
  type Identifier,
  type IdentifierAttrs,
} from "../../stores/definitions/Identifier";
import Description from "../../Inventory/components/Fields/Description";
import Tags from "../../Inventory/components/Fields/Tags";
import { Optional } from "../../util/optional";
import { decodeTagString } from "../../components/Tags/ParseEncodedTagStrings";
import AlwaysNewWindowNavigationContext from "../AlwaysNewWindowNavigationContext";
import { type Tag } from "../../stores/definitions/Tag";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import NoValue from "../NoValue";
import VisuallyHiddenHeading from "../VisuallyHiddenHeading";
import IdentifierModel from "../../stores/models/IdentifierModel";
import { truncateIsoTimestamp } from "../../stores/definitions/Units";

const useStyles = makeStyles()((theme) => ({
  styledDescriptionList: {
    fontSize: "0.85rem",
    margin: theme.spacing(1, 0),
    "& dt": {
      color: theme.palette.text.secondary,
      fontWeight: "600",
    },
    "& dd": {
      margin: 0,
      marginTop: theme.spacing(0.5),
    },
  },
  pageWrapper: { fontFamily: "Arial" },
  header: {
    backgroundColor: "#e3f0ff",
    borderBottom: "2px solid black",
  },
  idWrapper: {
    width: "auto",
    backgroundColor: "#e3f0ff",
    margin: theme.spacing(3, 2, 0, 2),
    padding: theme.spacing(0, 1, 1, 0),
    borderRadius: theme.spacing(0.75),
    border: `2px solid black`,
  },
  block: {
    width: "100%",
    padding: theme.spacing(0.5, 2, 0.5, 2),
    alignItems: "flex-start",
  },
  bottomBordered: {
    paddingBottom: theme.spacing(1),
    borderBottom: `1px dotted ${theme.palette.lightestGrey}`,
  },
  place: {
    width: "100%",
    padding: theme.spacing(0.5, 2, 0.5, 0),
    alignItems: "flex-start",
  },
  logo: {
    backgroundColor: "#fff",
    margin: theme.spacing(0.5, 2, 0.5, 0.5),
    padding: theme.spacing(0.5),
    borderRadius: theme.spacing(0.75),
    border: `2px solid #eee`,
  },
  igsnLogo: {
    padding: theme.spacing(0, 0.5),
    width: "70px",
  },
  primary: { color: theme.palette.primary.main },
  grey: { color: theme.palette.lightestGrey },
  ac: { alignItems: "center" },
  key: { width: "230px", fontWeight: "bold" },
  keyWithMargin: { margin: theme.spacing(1), fontWeight: "bold" },
  bottomSpaced: { marginBottom: theme.spacing(1) },
}));

function DividedPair({
  children,
}: {|
  children: [Element<"dt">, Element<"dd">, Element<"dt">, Element<"dd">],
|}) {
  return (
    <Grid container direction="row" spacing={1} sx={{ mb: 1 }}>
      <Grid item xs={1}></Grid>
      <Grid item>
        <span>
          {children[0]}
          {children[1]}
        </span>
      </Grid>
      <Grid item>
        <Divider orientation="vertical" role="presentation" />
      </Grid>
      <Grid item>
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

/**
 * importing from IdentifierModel would not work
 * as RootStore n/a to unauthenticated user
 * so we replicate some functionality and logic
 *
 */
const RECOMMENDED_FIELDS_LABELS = {
  type: "Type",
  freeType: "Type",
  subjectScheme: "Subject Scheme",
  schemeURI: "Scheme URI",
  valueURI: "Value URI",
  classificationCode: "Classification Code",
};
const subFields = (fValue: any): Array<{ key: string, value: string }> =>
  Object.entries(fValue)
    .filter((item) => item[0] !== "value" && item[0] !== "type")
    .map((item) => {
      return { key: item[0], value: item[1] };
    });

type PolygonPoint = {
  pointLatitude: string,
  pointLongitude: string,
};

type GeoLocationBox = {
  eastBoundLongitude: string,
  northBoundLatitude: string,
  southBoundLatitude: string,
  westBoundLongitude: string,
};

const glPointComplete = (point: PolygonPoint): boolean => {
  return Object.values(point).every((v) => v !== "");
};

const glBoxComplete = (box: GeoLocationBox): boolean => {
  return Object.values(box).every((v) => v !== "");
};

type IdentifierDataGridArgs = {|
  identifier: Identifier,
  record: {
    description: ?string,
    tags: Array<Tag>,
    fields?: Array<{
      name: string,
      type: string,
      id: number,
      content: ?string,
      selectedOptions: ?Array<string>,
    }>,
    extraFields?: Array<{
      name: string,
      id: ?number,
      content: string,
    }>,
  },
|};

export const IdentifierDataGrid = ({
  record,
  identifier,
}: IdentifierDataGridArgs): Node => {
  const { classes } = useStyles();

  const institutionName: string = identifier.publisher.split(" (")[0];

  const anyRecommendedGiven: boolean = [
    identifier.subjects,
    identifier.descriptions,
    identifier.alternateIdentifiers,
    identifier.dates,
    identifier.geoLocations,
  ].some((r) => Array.isArray(r) && r.length > 0); // groups could be returned as null

  return (
    <Grid container className={classes.pageWrapper}>
      <VisuallyHiddenHeading variant="h1">
        {identifier.title}
      </VisuallyHiddenHeading>
      <Grid
        aria-hidden={true}
        container
        className={clsx(classes.block, classes.header)}
        spacing={0}
      >
        <Grid item className={classes.logo}>
          <img
            src={INSTITUTION_LOGO_ADDRESS}
            alt="Institution Logo"
            title="Institution Logo"
          />
        </Grid>
        <Grid>
          <h3 className={classes.primary}>RSpace Public Pages</h3>
          <h2>{institutionName}</h2>
        </Grid>
      </Grid>
      <Grid
        container
        aria-hidden={true}
        direction="row"
        className={clsx(classes.block, classes.idWrapper)}
        spacing={2}
      >
        <Grid item>
          <Grid container direction="column" spacing={0.25}>
            <Grid item>
              <h3>{identifier.title}</h3>
            </Grid>
            <Grid item>
              <Typography variant="body1">
                {identifier.publicUrl ? (
                  <a href={identifier.publicUrl} name="Item landing page">
                    {identifier.doi}
                  </a>
                ) : (
                  <>{identifier.doi}</>
                )}
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid item>
          <Grid
            container
            direction="column"
            spacing={0.5}
            className={classes.ac}
          >
            <Grid item>
              <a
                href={IGSN_BASE_URL}
                name="IGSN Homepage"
                target="_blank"
                rel="noreferrer"
              >
                <img
                  src={IGSNlogo}
                  alt="IGSN Logo"
                  title="IGSN Logo"
                  className={classes.igsnLogo}
                />
              </a>
            </Grid>
            <Grid item>
              <Typography variant="caption">
                {identifier.resourceTypeGeneral === "PhysicalObject"
                  ? "Physical Object"
                  : identifier.resourceTypeGeneral}
              </Typography>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item>
          <h2>General</h2>
        </Grid>
      </Grid>
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item className={classes.key}>
          Name:
        </Grid>
        <Grid item>{identifier.title}</Grid>
      </Grid>
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item className={classes.key}>
          IGSN ID:{" "}
        </Grid>
        <Grid item data-testid="identifier-public-url">
          {identifier.publicUrl ? (
            <a href={identifier.publicUrl} name="DOI - address">
              {identifier.publicUrl}
            </a>
          ) : (
            <em>URL available after publishing</em>
          )}
        </Grid>
      </Grid>
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item className={classes.key}>
          Resource Type:
        </Grid>
        <Grid item data-testid="identifier-resource-type">
          {identifier.resourceType}
        </Grid>
      </Grid>
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item className={classes.key}>
          Creator:{" "}
        </Grid>
        <Grid item>{identifier.creatorName}</Grid>
      </Grid>
      {identifier.creatorAffiliation && (
        <Grid container direction="row" className={classes.block} spacing={1}>
          <Grid item className={classes.key}>
            Creator Affiliation:{" "}
          </Grid>
          <Grid item>{identifier.creatorAffiliation}</Grid>
        </Grid>
      )}
      {identifier.creatorAffiliationIdentifier && (
        <Grid container direction="row" className={classes.block} spacing={1}>
          <Grid item className={classes.key}>
            Creator Affiliation Identifier:{" "}
          </Grid>
          <Grid item>
            <a
              target="_blank"
              rel="noreferrer"
              href={identifier.creatorAffiliationIdentifier}
            >
              {identifier.creatorAffiliationIdentifier}
            </a>
          </Grid>
        </Grid>
      )}
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item className={classes.key}>
          Organisation:{" "}
        </Grid>
        <Grid item>{identifier.publisher}</Grid>
      </Grid>
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item className={classes.key}>
          Publication Year:{" "}
        </Grid>
        <Grid item>{identifier.publicationYear}</Grid>
      </Grid>
      {anyRecommendedGiven && (
        <>
          <Grid container direction="row" className={classes.block} spacing={1}>
            <Grid item>
              <h2>Optional Fields</h2>
            </Grid>
          </Grid>
          {Array.isArray(identifier.subjects) &&
            identifier.subjects.length > 0 && (
              <Grid
                container
                direction="column"
                className={classes.block}
                spacing={1}
                role="group"
                aria-label="subjects"
              >
                <Grid item>
                  <h3>Subjects</h3>
                </Grid>
                {identifier.subjects.map((s) => (
                  <Grid item className={classes.block} key={s.value}>
                    <Grid item sx={{ margin: "8px" }}>
                      {s.value}
                    </Grid>
                    {subFields(s).length > 0 &&
                      subFields(s).map((sf) => (
                        <Grid
                          container
                          direction="row"
                          className={classes.block}
                          spacing={1}
                          key={sf.key}
                        >
                          <Grid item className={classes.key}>
                            {
                              // $FlowExpectedError[invalid-computed-prop]
                              RECOMMENDED_FIELDS_LABELS[sf.key]
                            }
                          </Grid>
                          <Grid item>
                            {sf.value ? (
                              <>{sf.value}</>
                            ) : (
                              <em className={classes.grey}>None</em>
                            )}
                          </Grid>
                        </Grid>
                      ))}
                  </Grid>
                ))}
              </Grid>
            )}
          {Array.isArray(identifier.descriptions) &&
            identifier.descriptions.length > 0 && (
              <Grid
                container
                direction="column"
                className={classes.block}
                spacing={1}
                role="group"
                aria-label="descriptions"
              >
                <Grid item>
                  <h3>Descriptions</h3>
                </Grid>
                {identifier.descriptions.map((d) => (
                  <Grid
                    container
                    direction="row"
                    className={classes.block}
                    spacing={1}
                    key={d.value}
                  >
                    <Grid item className={classes.key}>
                      {capitaliseJustFirstChar(d.type.toLowerCase())}
                    </Grid>
                    <Grid item>{d.value}</Grid>
                  </Grid>
                ))}
              </Grid>
            )}
          {Array.isArray(identifier.alternateIdentifiers) &&
            identifier.alternateIdentifiers.length > 0 && (
              <Grid
                container
                direction="column"
                className={classes.block}
                spacing={1}
                role="group"
                aria-label="alternate-identifiers"
              >
                <Grid item>
                  <h3>Alternate Identifiers</h3>
                </Grid>
                {identifier.alternateIdentifiers.map((id) => (
                  <Grid
                    container
                    direction="row"
                    className={classes.block}
                    spacing={1}
                    key={id.value}
                  >
                    <Grid item sx={{ marginBottom: "8px" }}>
                      {id.value}
                    </Grid>
                    {subFields(id).length > 0 &&
                      subFields(id).map((sf) => (
                        <Grid
                          container
                          direction="row"
                          className={classes.block}
                          spacing={1}
                          key={sf.key}
                        >
                          <Grid item className={classes.key}>
                            {
                              // $FlowExpectedError[invalid-computed-prop]
                              RECOMMENDED_FIELDS_LABELS[sf.key]
                            }
                          </Grid>
                          <Grid item>
                            {sf.value ? <>{sf.value}</> : <em>None</em>}
                          </Grid>
                        </Grid>
                      ))}
                  </Grid>
                ))}
              </Grid>
            )}
          {Array.isArray(identifier.dates) && identifier.dates.length > 0 && (
            <Grid
              container
              direction="column"
              className={classes.block}
              spacing={1}
              role="group"
              aria-label="dates"
            >
              <Grid item>
                <h3>Dates</h3>
              </Grid>
              {identifier.dates.map((d, i) => (
                <Grid
                  container
                  direction="row"
                  className={classes.block}
                  spacing={1}
                  key={d.value.toString() + "-" + i}
                >
                  <Grid item className={classes.key}>
                    {capitaliseJustFirstChar(d.type.toLowerCase())}
                  </Grid>
                  <Grid item>
                    {truncateIsoTimestamp(d.value, "date").orElse(
                      "Invalid date"
                    )}
                  </Grid>
                </Grid>
              ))}
            </Grid>
          )}
          {Array.isArray(identifier.geoLocations) &&
            identifier.geoLocations.length > 0 && (
              <>
                <h3>Geolocations</h3>
                <Grid
                  container
                  direction="row"
                  spacing={1}
                  role="group"
                  aria-label="geoLocations"
                >
                  {identifier.geoLocations.map((gl, i) => (
                    <Grid item key={i}>
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
                                Point
                              </Typography>
                              <dl className={classes.styledDescriptionList}>
                                <DividedPair>
                                  <dt>Latitude</dt>
                                  <dd>{gl.geoLocationPoint.pointLatitude}˚</dd>
                                  <dt>Longitude</dt>
                                  <dd>{gl.geoLocationPoint.pointLongitude}˚</dd>
                                </DividedPair>
                              </dl>
                            </>
                          )}
                          {gl.geoLocationPlace && (
                            <>
                              <Typography component="h4" variant="h6">
                                Place
                              </Typography>
                              <dl className={classes.styledDescriptionList}>
                                <Grid container direction="row" spacing={1}>
                                  <Grid item xs={1}></Grid>
                                  <Grid item xs={11}>
                                    <span>
                                      <dt>Description</dt>
                                      <dd>{gl.geoLocationPlace}</dd>
                                    </span>
                                  </Grid>
                                </Grid>
                              </dl>
                            </>
                          )}
                          {glBoxComplete(gl.geoLocationBox) && (
                            <>
                              <Typography component="h4" variant="h6">
                                Box
                              </Typography>
                              <dl className={classes.styledDescriptionList}>
                                {/* width style is used to align vertical dividers */}
                                <DividedPair>
                                  <dt style={{ minWidth: "140px" }}>
                                    Northbound Latitude
                                  </dt>
                                  <dd>
                                    {gl.geoLocationBox.northBoundLatitude}˚
                                  </dd>
                                  <dt>Westbound Longitude</dt>
                                  <dd>
                                    {gl.geoLocationBox.westBoundLongitude}˚
                                  </dd>
                                </DividedPair>
                                <DividedPair>
                                  <dt style={{ minWidth: "140px" }}>
                                    Southbound Latitude
                                  </dt>
                                  <dd>
                                    {gl.geoLocationBox.southBoundLatitude}˚
                                  </dd>
                                  <dt>Eastbound Longitude</dt>
                                  <dd>
                                    {gl.geoLocationBox.eastBoundLongitude}˚
                                  </dd>
                                </DividedPair>
                              </dl>
                            </>
                          )}
                          {gl.geoLocationPolygon.isValid && (
                            <>
                              <Typography component="h4" variant="h6">
                                Polygon
                              </Typography>
                              <dl className={classes.styledDescriptionList}>
                                {gl.geoLocationPolygon.mapPoints(
                                  (point, index) => (
                                    <DividedPair key={index}>
                                      <dt>Point {index + 1} Latitude</dt>
                                      <dd>{point.pointLatitude}˚</dd>
                                      <dt>Point {index + 1} Longitude</dt>
                                      <dd>{point.pointLongitude}˚</dd>
                                    </DividedPair>
                                  )
                                )}
                              </dl>
                              {glPointComplete(
                                gl.geoLocationInPolygonPoint
                              ) && (
                                <>
                                  <Typography component="h4" variant="h6">
                                    In Polygon Point
                                  </Typography>
                                  <dl className={classes.styledDescriptionList}>
                                    <DividedPair>
                                      <dt>Latitude</dt>
                                      <dd>
                                        {
                                          gl.geoLocationInPolygonPoint
                                            .pointLatitude
                                        }
                                        ˚
                                      </dd>
                                      <dt>Longitude</dt>
                                      <dd>
                                        {
                                          gl.geoLocationInPolygonPoint
                                            .pointLongitude
                                        }
                                        ˚
                                      </dd>
                                    </DividedPair>
                                  </dl>
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
          <Grid container direction="row" className={classes.block} spacing={1}>
            <Grid item>
              <h2>Inventory</h2>
            </Grid>
          </Grid>
          <Grid
            container
            direction="column"
            className={classes.block}
            spacing={2}
          >
            <Grid item>
              <Description
                fieldOwner={{
                  isFieldEditable: () => false,
                  fieldValues: {
                    description: record.description,
                  },
                  setFieldsDirty: () => {},
                  canChooseWhichToEdit: false,
                  setFieldEditable: () => {},
                  noValueLabel: "",
                }}
                onErrorStateChange={() => {}}
              />
            </Grid>
            <Grid item>
              <Tags
                fieldOwner={{
                  isFieldEditable: () => false,
                  fieldValues: {
                    tags: record.tags,
                  },
                  setFieldsDirty: () => {},
                  canChooseWhichToEdit: false,
                  setFieldEditable: () => {},
                  noValueLabel: "",
                }}
              />
            </Grid>
            {typeof record.fields !== "undefined" && (
              <Grid item>
                <Typography variant="h6" component="h3">
                  Custom Fields
                </Typography>
                {record.fields.length === 0 ? (
                  <NoValue label="None" />
                ) : (
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Name</TableCell>
                          <TableCell>Value</TableCell>
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
                            ].includes(f.type)
                          )
                          .map((f) => (
                            <TableRow key={f.id}>
                              <TableCell>{f.name}</TableCell>
                              <TableCell>
                                {(f.selectedOptions?.join(", ") ??
                                  f.content?.toString()) || (
                                  <NoValue label="None" />
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
              <Grid item>
                <Typography variant="h6" component="h3">
                  Extra Fields
                </Typography>
                {record.extraFields.length === 0 ? (
                  <NoValue label="None" />
                ) : (
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Name</TableCell>
                          <TableCell>Value</TableCell>
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
      <Grid container direction="row" className={classes.block} spacing={1}>
        <Grid item>
          <h2>Other Information</h2>
        </Grid>
      </Grid>
      <Grid
        container
        direction="row"
        className={clsx(classes.block, classes.bottomSpaced)}
        spacing={1}
      >
        <Grid item>
          If you wish to obtain more information about this item, please contact
          the research data management department at {institutionName}.
          <br />
          <br />
          This page was generated by {institutionName} using RSpace Public
          Pages.
        </Grid>
      </Grid>
    </Grid>
  );
};

type IdentifierPublicPageArgs = {|
  publicId: string,
|};

const IdentifierPublicPage = ({ publicId }: IdentifierPublicPageArgs): Node => {
  const [fetching, setFetching] = useState(false);
  const [publicData, setPublicData] = useState<?{
    identifiers: Array<Identifier>,
    description: ?string,
    tags: Array<Tag>,
    fields?: Array<{
      id: number,
      name: string,
      type: string,
      content: ?string,
      selectedOptions: ?Array<string>,
      ...
    }>,
    extraFields?: Array<{
      id: ?number,
      name: string,
      content: string,
    }>,
    ...
  }>();
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    async function fetchPublicData(): Promise<void> {
      try {
        setFetching(true);
        // anonymous call
        const { data } = await axios.get<{
          identifiers: Array<IdentifierAttrs>,
          description: ?string,
          tags: Array<{|
            value: string,
            ontologyName: string,
            uri: string,
            ontologyVersion: string,
          |}>,
          fields?: Array<{
            id: number,
            type: string,
            name: string,
            content: ?string,
            selectedOptions: ?Array<string>,
          }>,
          ...
        }>(`/api/inventory/v1/public/view/${publicId}`);
        setPublicData({
          ...data,
          identifiers: data.identifiers.map(
            (x) => new IdentifierModel(x, publicId)
          ),
          tags: data.tags.map((tag) => ({
            value: decodeTagString(tag.value),
            uri:
              tag.uri === ""
                ? Optional.empty<string>()
                : Optional.present(decodeTagString(tag.uri)),
            vocabulary:
              tag.ontologyName === ""
                ? Optional.empty<string>()
                : Optional.present(decodeTagString(tag.ontologyName)),
            version:
              tag.ontologyVersion === ""
                ? Optional.empty<string>()
                : Optional.present(decodeTagString(tag.ontologyVersion)),
          })),
        });
      } catch (e) {
        setErrorMessage(e.response.data.message);
        throw new Error(e);
      } finally {
        setFetching(false);
      }
    }
    void fetchPublicData();
  }, []);

  if (fetching) return null;
  if (!publicData)
    return (
      <>
        <h1>RSpace Public Pages</h1>
        <p>{errorMessage}</p>
      </>
    );

  return (
    <ThemeProvider theme={materialTheme}>
      <AlwaysNewWindowNavigationContext>
        <IdentifierDataGrid
          record={publicData}
          identifier={publicData.identifiers[0]}
        />
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
    root.render(<IdentifierPublicPage publicId={rsPublicId} />);
  }
});

export default (observer(
  IdentifierPublicPage
): ComponentType<IdentifierPublicPageArgs>);
