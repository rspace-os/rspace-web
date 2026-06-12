import CloseIcon from "@mui/icons-material/Close";
import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import IconButton from "@mui/material/IconButton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import React, { useEffect } from "react";
import axios from "@/common/axios";
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
    .map((part, index) => (/^\d+$/.test(part) ? <sub key={`formula-sub-${index}`}>{part}</sub> : part));
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

    return Object.fromEntries(Object.entries(parsed).map(([key, value]) => [key, String(value)]));
  } catch {
    return {};
  }
}

const cardSx = {
  m: 0,
  borderRadius: 0,
  mb: "10px",
  zIndex: 1,
  borderTop: "none",
  borderRight: "none",
  borderBottomLeftRadius: "4px",
  backgroundColor: "unset",
} as const;

const tableCellSx = {
  maxWidth: "150px",
  overflowX: "auto",
  whiteSpace: "nowrap",
} as const;

/** A label/value row: a `th` label and a right-aligned value cell. */
function PropertyRow({ label, value }: { label: React.ReactNode; value: React.ReactNode }): React.ReactElement {
  return (
    <TableRow>
      <TableCell component="th" scope="row">
        {label}
      </TableCell>
      <TableCell align="right" sx={tableCellSx}>
        {value}
      </TableCell>
    </TableRow>
  );
}

/** Renders arbitrary metadata key/value pairs, linkifying URL values. */
function MetadataRows({ metadata, keyPrefix }: { metadata: MetadataMap; keyPrefix: string }): React.ReactNode {
  return Object.entries(metadata).map(([key, value]) => (
    <PropertyRow
      key={`${keyPrefix}-${key}`}
      label={key}
      value={
        isUrl(value) ? (
          <a href={value} target="_blank" rel="noopener noreferrer">
            {value}
          </a>
        ) : (
          value
        )
      }
    />
  ));
}

export default function ChemCard(props: ChemCardProps) {
  const [chem, setChem] = React.useState<ChemInfo>({
    reactants: [],
    products: [],
    molecules: [],
  });
  const chemId = props.item.id !== undefined ? Number.parseInt(props.item.id, 10) : Number.NaN;
  const additionalMetadata = getAdditionalMetadata(chem.additionalMetadata);
  const inlineHeight = Number(props.height) < 200 ? "200px" : `${Number.parseInt(String(props.height), 10) + 6}px`;

  // fetch chem details
  useEffect(() => {
    if (Number.isNaN(chemId) || chemId < 0) {
      return;
    }

    const publicView = document.getElementById("public_document_view");
    const publicUrlPrepend = publicView ? "/public/publicView" : "";
    const url = `${publicUrlPrepend}/chemical/ajax/getInfo?chemId=${chemId}`;
    axios
      .get<ChemInfoResponse>(url)
      .then((response): void => {
        if (response.data.data) {
          setChem(response.data.data);
        } else {
          console.warn(
            // biome-ignore lint/style/useTemplate: initial biome migration
            "error when retrieving info for chem elem " + props.item.id,
            response.data.error,
          );
        }
      })
      .catch((error: unknown): void => {
        console.warn(error);
      });
  }, [chemId]);

  const chemInfoTable = (chemical: ChemicalInfo): React.ReactElement => {
    const chemicalMetadata = getAdditionalMetadata(chemical.additionalMetadata);
    const keyPrefix = chemical.formula ?? chemical.role ?? "chemical";
    const properties: ReadonlyArray<{
      label: React.ReactNode;
      value: React.ReactNode;
    }> = [
      { label: "Formula", value: renderFormattedFormula(chemical.formula) },
      { label: "Mass", value: chemical.mass },
      // Monoisotopic mass symbol = Exact mass
      {
        label: (
          <>
            M<sub>mi</sub>
          </>
        ),
        value: chemical.exactMass,
      },
      { label: "Charge", value: chemical.formalCharge },
      { label: "Bonds", value: chemical.bondCount },
      { label: "Atoms", value: chemical.atomCount },
    ];

    return (
      <React.Fragment key={`${chemical.role ?? "chemical"}-${chemical.formula ?? ""}`}>
        <TableRow sx={{ background: "rgb(240,240,240" }}>
          <TableCell component="th" scope="row"></TableCell>
          <TableCell component="th" scope="row" align="center">
            {chemical.role}
          </TableCell>
        </TableRow>
        {/** biome-ignore lint/suspicious/noDoubleEquals: initial biome migration */}
        {chemical.name != "" && <PropertyRow label="Name" value={chemical.name} />}
        {properties.map((property, index) => (
          <PropertyRow key={`${keyPrefix}-prop-${index}`} label={property.label} value={property.value} />
        ))}
        <MetadataRows metadata={chemicalMetadata} keyPrefix={keyPrefix} />
      </React.Fragment>
    );
  };

  if (!Number.isNaN(chemId) && chemId < 0) {
    return (
      <Card
        sx={{
          ...cardSx,
          width: "200px",
          border: "1px solid #eee",
          backgroundColor: "#fafafa",
          height: props.inline ? inlineHeight : "200px",
          ...(props.inline ? { margin: "0px" } : {}),
        }}
        elevation={props.inline ? 0 : 4}
      />
    );
  }

  return (
    <Card
      sx={{
        ...cardSx,
        ...(props.inline
          ? {
              height: inlineHeight,
              overflowY: "auto",
              margin: "0px",
            }
          : {}),
      }}
      elevation={props.inline ? 0 : 4}
    >
      {!props.inline && (
        <CardHeader
          sx={{
            p: "7px",
            background: "white",
          }}
          subheader={
            <Box
              component="img"
              sx={{
                maxHeight: "48px",
                maxWidth: "180px",
                width: "auto",
                height: "auto",
              }}
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
              sx={{ m: "10px" }}
            >
              <CloseIcon />
            </IconButton>
          }
        />
      )}
      <CardContent
        sx={{
          p: 0,
          paddingBottom: "0px !important",
        }}
      >
        <Table
          size="small"
          sx={{
            mb: 0,
            backgroundColor: "white",
          }}
        >
          <TableBody>
            {chem.reaction && <PropertyRow label="Formula" value={renderFormattedFormula(chem.formula)} />}
            {chem.reactants.map((r) => chemInfoTable(r))}
            {chem.products.map((p) => chemInfoTable(p))}
            {chem.molecules.map((m) => chemInfoTable(m))}
            <MetadataRows metadata={additionalMetadata} keyPrefix="metadata" />
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
