import React, { useEffect } from "react";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import IconButton from "@mui/material/IconButton";
import CloseIcon from "@mui/icons-material/Close";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import axios from "@/common/axios";
import { makeStyles } from "tss-react/mui";
import { isUrl } from "@/util/Util";

type MetadataMap = Record<string, string>;

interface ChemCardItem extends Record<string, string | undefined> {
  id?: string;
  imageSrc?: string;
}

interface ChemicalInfo {
  role?: string;
  name?: string;
  formula?: string;
  mass?: string | number;
  exactMass?: string | number;
  formalCharge?: string | number;
  bondCount?: string | number;
  atomCount?: string | number;
  additionalMetadata?: string;
}

interface ChemInfo extends ChemicalInfo {
  reaction?: boolean;
  reactants: ChemicalInfo[];
  products: ChemicalInfo[];
  molecules: ChemicalInfo[];
}

interface ChemInfoResponse {
  data?: ChemInfo;
  error?: unknown;
}

interface ChemCardProps {
  item: ChemCardItem;
  inline?: boolean;
  height?: number | string;
  idx?: number;
  onClose?: (id: string | undefined) => void;
}

function renderFormattedFormula(formula?: string): React.ReactNode {
  if (!formula) {
    return "";
  }

  return formula
    .split(/(\d+)/)
    .filter((part) => part !== "")
    .map((part, index) =>
      /^\d+$/.test(part) ? <sub key={`formula-sub-${index}`}>{part}</sub> : part,
    );
}

function getAdditionalMetadata(additionalMetadata?: string): MetadataMap {
  if (!additionalMetadata) {
    return {};
  }

  try {
    const parsed = JSON.parse(additionalMetadata) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {};
    }

    return Object.fromEntries(
      Object.entries(parsed).map(([key, value]) => [key, String(value)]),
    );
  } catch {
    return {};
  }
}

const useStyles = makeStyles()((theme) => ({
  text: {
    fontSize: "14px !important",
    padding: "2px",
    color: "#1465b7 !important",
  },
  paper: {
    padding: theme.spacing(0),
  },
  cardContent: {
    padding: "0",
    paddingBottom: "0px !important",
  },
  card: {
    margin: "0px",
    borderRadius: "0px",
    marginBottom: "10px",
    zIndex: 1,
    borderTop: "none",
    borderRight: "none",
    borderBottomLeftRadius: "4px",
    backgroundColor: "unset",
  },
  cardHeader: {
    padding: "7px",
    background: "white",
  },
  tableCell: {
    maxWidth: "150px",
    overflowX: "auto",
    whiteSpace: "nowrap",
  },
  table: {
    marginBottom: "0px",
    backgroundColor: "white",
  },
  avatar: {
    maxHeight: "48px",
    maxWidth: "180px",
    width: "auto",
    height: "auto",
  },
  closeButton: {
    margin: "10px",
  },
}));

export default function ChemCard(props: ChemCardProps) {
  const { classes } = useStyles();
  const [chem, setChem] = React.useState<ChemInfo>({
    reactants: [],
    products: [],
    molecules: [],
  });
  const chemId =
    props.item.id !== undefined ? Number.parseInt(props.item.id, 10) : Number.NaN;
  const additionalMetadata = getAdditionalMetadata(chem.additionalMetadata);

  // fetch chem details
  const fetchChemDetails = (): void => {
    if (Number.isNaN(chemId) || chemId < 0) {
      return;
    }

    const publicView = document.getElementById("public_document_view");
    const publicUrlPrepend = publicView ? "/public/publicView" : "";
    const url = publicUrlPrepend + `/chemical/ajax/getInfo?chemId=${chemId}`;
    axios
      .get<ChemInfoResponse>(url)
      .then((response): void => {
        if (response.data.data) {
          setChem(response.data.data);
        } else {
          console.warn(
            "error when retrieving info for chem elem " + props.item.id,
            response.data.error,
          );
        }
      })
      .catch((error: unknown): void => {
        console.warn(error);
      });
  };

  useEffect(() => {
    if (!Number.isNaN(chemId) && chemId >= 0) {
      fetchChemDetails();
    }
  }, [chemId]);

  const chemInfoTable = (chemical: ChemicalInfo): React.ReactElement => {
    const chemicalMetadata = getAdditionalMetadata(chemical.additionalMetadata);

    return (
      <React.Fragment key={`${chemical.role ?? "chemical"}-${chemical.formula ?? ""}`}>
        <TableRow style={{ background: "rgb(240,240,240" }}>
          <TableCell component="th" scope="row"></TableCell>
          <TableCell component="th" scope="row" align="center">
            {chemical.role}
          </TableCell>
        </TableRow>
        {chemical.name != "" && (
          <TableRow>
            <TableCell component="th" scope="row">
              Name
            </TableCell>
            <TableCell align="right" className={classes.tableCell}>
              {chemical.name}
            </TableCell>
          </TableRow>
        )}
        <TableRow>
          <TableCell component="th" scope="row">
            Formula
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {renderFormattedFormula(chemical.formula)}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Mass
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.mass}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            {/* Monoisotopic mass symbol = Exact mass */}M<sub>mi</sub>
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.exactMass}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Charge
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.formalCharge}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Bonds
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.bondCount}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Atoms
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.atomCount}
          </TableCell>
        </TableRow>
        {Object.entries(chemicalMetadata).map(
          ([k, v]) => (
            <TableRow key={`${chemical.formula ?? chemical.role ?? "chemical"}-${k}`}>
              <TableCell component="th" scope="row">
                {k}
              </TableCell>
              <TableCell align="right" className={classes.tableCell}>
                {isUrl(v) ? (
                  <a href={v} target="_blank" rel="noopener noreferrer">
                    {v}
                  </a>
                ) : (
                  v
                )}
              </TableCell>
            </TableRow>
          ),
        )}
      </React.Fragment>
    );
  };

  if (!Number.isNaN(chemId) && chemId < 0) {
    return (
      <Card
        className={classes.card}
        elevation={props.inline ? 0 : 4}
        style={
          props.inline
            ? {
                height:
                  Number(props.height) < 200
                    ? "200px"
                    : `${Number.parseInt(String(props.height), 10) + 6}px`,
                width: "200px",
                margin: "0px",
                border: "1px solid #eee",
                backgroundColor: "#fafafa",
              }
            : {
                height: "200px",
                width: "200px",
                border: "1px solid #eee",
                backgroundColor: "#fafafa",
              }
        }
      />
    );
  }

  return (
    <Card
      className={classes.card}
      elevation={props.inline ? 0 : 4}
      style={
        props.inline
          ? {
              height:
                Number(props.height) < 200
                  ? "200px"
                  : `${Number.parseInt(String(props.height), 10) + 6}px`,
              overflowY: "auto",
              margin: "0px",
            }
          : {}
      }
    >
      {!props.inline && (
        <CardHeader
          className={classes.cardHeader}
          subheader={
            <img
              className={classes.avatar}
              src={props.item.imageSrc}
              alt="Chemical structure"
              height="48px"
            />
          }
          action={
            <IconButton
              size="small"
              aria-label="close"
              onClick={() => props.onClose?.(props.item.id)}
              className={classes.closeButton}
            >
              <CloseIcon />
            </IconButton>
          }
        />
      )}
      <CardContent className={classes.cardContent}>
        <Table size="small" className={classes.table}>
          <TableBody>
            {chem.reaction && (
              <TableRow>
                <TableCell component="th" scope="row">
                  Formula
                </TableCell>
                <TableCell align="right" className={classes.tableCell}>
                  {renderFormattedFormula(chem.formula)}
                </TableCell>
              </TableRow>
            )}
            {chem.reactants.map((r) => chemInfoTable(r))}
            {chem.products.map((p) => chemInfoTable(p))}
            {chem.molecules.map((m) => chemInfoTable(m))}
            {Object.entries(additionalMetadata).map(
              ([k, v]) => (
                <TableRow key={k}>
                  <TableCell component="th" scope="row">
                    {k}
                  </TableCell>
                  <TableCell align="right" className={classes.tableCell}>
                    {isUrl(v) ? (
                      <a href={v} target="_blank" rel="noopener noreferrer">
                        {v}
                      </a>
                    ) : (
                      v
                    )}
                  </TableCell>
                </TableRow>
              ),
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
