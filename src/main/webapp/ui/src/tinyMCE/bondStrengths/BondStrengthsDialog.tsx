import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { useQuery } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import {
  type Bond,
  bondStrengthsQuery,
  type SupportedBondStrengths,
  sortWeakestFirst,
  svgDataUri,
  svgToPngDataUri,
  type UnsupportedBondStrengths,
} from "./bondStrengthsApi";
import type { BondStrengthsDocumentContent } from "./insertIntoDocument";

type Translate = TFunction<["workspace", "common"]>;

type BondRow = {
  label: string;
  weakest: boolean;
  kcalMol: string;
  kjMol: string;
};

type BondStrengthsDialogProps = {
  open: boolean;
  onClose: () => void;
  chemId: number;
  revision?: number;
  /** When given, the dialog offers to put the result into the document. */
  onInsert?: (content: BondStrengthsDocumentContent) => void | Promise<void>;
};

function structureNotSupportedText(t: Translate, result: UnsupportedBondStrengths): string {
  const detail = result.unsupportedDetail;
  const other = () => t("workspace:tinymce.bondStrengths.notSupported.other");
  switch (result.unsupportedReason) {
    case "ELEMENT_NOT_COVERED":
      return detail
        ? t("workspace:tinymce.bondStrengths.notSupported.elementNotCovered", { elements: detail })
        : other();
    case "MULTIPLE_FRAGMENTS":
      return t("workspace:tinymce.bondStrengths.notSupported.multipleFragments");
    case "RADICAL":
      return t("workspace:tinymce.bondStrengths.notSupported.radical");
    case "CHARGED":
      return t("workspace:tinymce.bondStrengths.notSupported.charged");
    case "TOO_LARGE":
      return detail ? t("workspace:tinymce.bondStrengths.notSupported.tooLarge", { limit: detail }) : other();
    case "REACTION":
      return t("workspace:tinymce.bondStrengths.notSupported.reaction");
    default:
      return other();
  }
}

function bondLabel(t: Translate, bond: Bond): string {
  return typeof bond.hydrogenCount === "number" && bond.hydrogenCount > 1
    ? t("workspace:tinymce.bondStrengths.table.equivalentHydrogens", { label: bond.label, count: bond.hydrogenCount })
    : bond.label;
}

function hasValue(bond: Bond): bond is Bond & { bdeKcalMol: number; bdeKjMol: number } {
  return bond.supported && typeof bond.bdeKcalMol === "number" && typeof bond.bdeKjMol === "number";
}

/** The bonds that have a value, weakest first. */
function bondRows(t: Translate, language: string, bonds: ReadonlyArray<Bond>): BondRow[] {
  const oneDecimal = new Intl.NumberFormat(language, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return sortWeakestFirst(bonds)
    .filter(hasValue)
    .map((bond) => ({
      label: bondLabel(t, bond),
      weakest: bond.weakest,
      kcalMol: oneDecimal.format(bond.bdeKcalMol),
      kjMol: oneDecimal.format(bond.bdeKjMol),
    }));
}

/**
 * One sentence per reason a bond has no value. Ring and multiple bonds are only counted, because
 * every structure with a ring has them; the rarer cases name the bonds.
 */
function noValueNotes(t: Translate, language: string, result: SupportedBondStrengths): string[] {
  const withoutValue = result.bonds.filter((bond) => !hasValue(bond));
  const labelsFor = (reason: string | null) =>
    withoutValue.filter((bond) => (bond.unsupportedReason ?? null) === reason).map((bond) => bondLabel(t, bond));
  const list = new Intl.ListFormat(language, { style: "long", type: "conjunction" });
  const notes: string[] = [];

  const ringBonds = labelsFor("RING_BOND").length;
  if (ringBonds > 0) {
    notes.push(
      t("workspace:tinymce.bondStrengths.noValue.ringBonds", {
        count: ringBonds,
      }),
    );
  }
  const multipleBonds = labelsFor("MULTIPLE_BOND").length;
  if (multipleBonds > 0) {
    notes.push(
      t("workspace:tinymce.bondStrengths.noValue.multipleBonds", {
        count: multipleBonds,
      }),
    );
  }
  const notCovered = labelsFor("BOND_TYPE_NOT_COVERED");
  if (notCovered.length > 0) {
    notes.push(
      t("workspace:tinymce.bondStrengths.noValue.bondTypeNotCovered", {
        bonds: list.format(notCovered),
      }),
    );
  }
  const lowConfidence = labelsFor("LOW_CONFIDENCE");
  if (lowConfidence.length > 0) {
    const limit = result.method.crossCheck?.maxDifferenceKcalMol;
    notes.push(
      typeof limit === "number"
        ? t("workspace:tinymce.bondStrengths.noValue.lowConfidence", {
            bonds: list.format(lowConfidence),
            limit: formatNumber(limit, language),
          })
        : t("workspace:tinymce.bondStrengths.noValue.lowConfidenceNoLimit", {
            bonds: list.format(lowConfidence),
          }),
    );
  }
  const known = new Set(["RING_BOND", "MULTIPLE_BOND", "BOND_TYPE_NOT_COVERED", "LOW_CONFIDENCE"]);
  const other = withoutValue
    .filter((bond) => !known.has(bond.unsupportedReason ?? ""))
    .map((bond) => bondLabel(t, bond));
  if (other.length > 0) {
    notes.push(
      t("workspace:tinymce.bondStrengths.noValue.other", {
        bonds: list.format(other),
      }),
    );
  }
  return notes;
}

function pictureAndColumnText(t: Translate) {
  return {
    imageAlt: t("workspace:tinymce.bondStrengths.imageAlt"),
    legend: t("workspace:tinymce.bondStrengths.legend"),
    columns: {
      bond: t("workspace:tinymce.bondStrengths.table.bond"),
      kcalMol: t("workspace:tinymce.bondStrengths.table.kcalMol"),
      kjMol: t("workspace:tinymce.bondStrengths.table.kjMol"),
    },
  };
}

function documentContent(
  t: Translate,
  language: string,
  result: SupportedBondStrengths,
  rows: ReadonlyArray<BondRow>,
  notes: ReadonlyArray<string>,
  imageSrc: string | null,
): BondStrengthsDocumentContent {
  return {
    ...pictureAndColumnText(t),
    imageSrc,
    caption: t("workspace:tinymce.bondStrengths.document.caption", {
      name: result.method.name,
      version: result.method.version,
      error: formatNumber(result.method.meanAbsoluteErrorKcalMol, language),
      date: new Intl.DateTimeFormat(language, { dateStyle: "long" }).format(new Date()),
    }),
    rows: rows.map((row) => ({
      bond: row.weakest ? t("workspace:tinymce.bondStrengths.document.weakestBond", { label: row.label }) : row.label,
      kcalMol: row.kcalMol,
      kjMol: row.kjMol,
    })),
    notes,
  };
}

function SupportedResult({
  result,
  rows,
  notes,
}: {
  result: SupportedBondStrengths;
  rows: ReadonlyArray<BondRow>;
  notes: ReadonlyArray<string>;
}) {
  const { t, i18n } = useTranslation(["workspace", "common"]);
  const language = i18n.resolvedLanguage ?? i18n.language;
  const { imageAlt, legend, columns } = pictureAndColumnText(t);

  return (
    <Stack spacing={2}>
      {result.imageSvg && (
        <Box component="figure" sx={{ m: 0 }}>
          <Box
            component="img"
            src={svgDataUri(result.imageSvg)}
            alt={imageAlt}
            sx={{ display: "block", maxWidth: "100%", height: "auto", mx: "auto" }}
          />
          <Typography component="figcaption" variant="caption" sx={{ display: "block" }}>
            {legend}
          </Typography>
        </Box>
      )}
      {rows.length === 0 && <Alert severity="info">{t("tinymce.bondStrengths.noValues")}</Alert>}
      {rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label={t("tinymce.bondStrengths.table.label")}>
            <TableHead>
              <TableRow>
                <TableCell>{columns.bond}</TableCell>
                <TableCell align="right">{columns.kcalMol}</TableCell>
                <TableCell align="right">{columns.kjMol}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.label}>
                  <TableCell component="th" scope="row" sx={row.weakest ? { fontWeight: "bold" } : undefined}>
                    {row.label}
                    {row.weakest && (
                      <Chip size="small" label={t("tinymce.bondStrengths.table.weakest")} sx={{ ml: 1 }} />
                    )}
                  </TableCell>
                  <TableCell align="right">{row.kcalMol}</TableCell>
                  <TableCell align="right">{row.kjMol}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {notes.length > 0 && (
        <Box component="ul" sx={{ m: 0, pl: 3 }}>
          {notes.map((note) => (
            <Typography key={note} component="li" variant="body2">
              {note}
            </Typography>
          ))}
        </Box>
      )}
      <Box>
        <Typography variant="body2">
          {t("tinymce.bondStrengths.coverage", {
            supported: result.supportedBondCount,
            total: result.totalBondCount,
          })}
        </Typography>
        <Typography variant="body2">
          {t("tinymce.bondStrengths.method", {
            name: result.method.name,
            version: result.method.version,
            error: formatNumber(result.method.meanAbsoluteErrorKcalMol, language),
          })}
        </Typography>
        <Typography variant="caption">
          {t("tinymce.bondStrengths.weights", {
            hash: result.method.weightsSha256.slice(0, 12),
          })}
        </Typography>
      </Box>
    </Stack>
  );
}

function BondStrengthsResult({ onClose, chemId, revision, onInsert }: Omit<BondStrengthsDialogProps, "open">) {
  const { t, i18n } = useTranslation(["workspace", "common"]);
  const language = i18n.resolvedLanguage ?? i18n.language;
  const { data, isPending, isError } = useQuery(bondStrengthsQuery(chemId, revision));
  const supported = data?.supported ? data : null;
  const [inserting, setInserting] = useState(false);
  const rows = supported ? bondRows(t, language, supported.bonds) : [];
  const notes = supported ? noValueNotes(t, language, supported) : [];

  return (
    <>
      <DialogContent>
        {isPending && (
          <Stack direction="row" spacing={2} role="status" sx={{ alignItems: "center" }}>
            <CircularProgress size={20} aria-hidden />
            <Typography>{t("tinymce.bondStrengths.dialog.loading")}</Typography>
          </Stack>
        )}
        {isError && <Alert severity="error">{t("tinymce.bondStrengths.dialog.error")}</Alert>}
        {data && !data.supported && <Alert severity="info">{structureNotSupportedText(t, data)}</Alert>}
        {supported && <SupportedResult result={supported} rows={rows} notes={notes} />}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.close")}</Button>
        {supported && rows.length > 0 && onInsert && (
          <Button
            variant="contained"
            color="callToAction"
            loading={inserting}
            onClick={() => {
              setInserting(true);
              // Without a picture the table still goes in.
              const picture = supported.imageSvg
                ? svgToPngDataUri(supported.imageSvg).catch(() => null)
                : Promise.resolve(null);
              void picture
                .then((imageSrc) => onInsert(documentContent(t, language, supported, rows, notes, imageSrc)))
                .finally(onClose);
            }}
          >
            {t("tinymce.bondStrengths.dialog.insert")}
          </Button>
        )}
      </DialogActions>
    </>
  );
}

export default function BondStrengthsDialog({ open, onClose, ...resultProps }: BondStrengthsDialogProps) {
  const { t } = useTranslation(["workspace", "common"]);
  const titleId = useId();

  return (
    <Dialog open={open} onClose={onClose} aria-labelledby={titleId} maxWidth="sm" fullWidth>
      <DialogTitle id={titleId}>{t("tinymce.bondStrengths.dialog.title")}</DialogTitle>
      {/* Dialog only mounts its children while open, so the request is made on open and not before. */}
      <BondStrengthsResult onClose={onClose} {...resultProps} />
    </Dialog>
  );
}
