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
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import {
  type Bond,
  bondStrengthsQuery,
  type SupportedBondStrengths,
  sortWeakestFirst,
  svgDataUri,
  type UnsupportedBondStrengths,
} from "./bondStrengthsApi";
import type { BondStrengthsDocumentContent } from "./insertIntoDocument";

type Translate = TFunction<["workspace", "common"]>;

type BondRow = {
  label: string;
  weakest: boolean;
  /** Null when the model gives no value for this bond. */
  values: { kcalMol: string; kjMol: string } | null;
  notAvailable: string;
};

type BondStrengthsDialogProps = {
  open: boolean;
  onClose: () => void;
  chemId: number;
  revision?: number;
  /** When given, the dialog offers to put the result into the document. */
  onInsert?: (content: BondStrengthsDocumentContent) => void;
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

function notAvailableText(t: Translate, reason: Bond["unsupportedReason"]): string {
  switch (reason) {
    case "RING_BOND":
      return t("workspace:tinymce.bondStrengths.notAvailable.ringBond");
    case "MULTIPLE_BOND":
      return t("workspace:tinymce.bondStrengths.notAvailable.multipleBond");
    case "BOND_TYPE_NOT_COVERED":
      return t("workspace:tinymce.bondStrengths.notAvailable.bondTypeNotCovered");
    case "LOW_CONFIDENCE":
      return t("workspace:tinymce.bondStrengths.notAvailable.lowConfidence");
    default:
      return t("workspace:tinymce.bondStrengths.notAvailable.other");
  }
}

function bondRows(t: Translate, language: string, bonds: ReadonlyArray<Bond>): BondRow[] {
  const oneDecimal = new Intl.NumberFormat(language, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return sortWeakestFirst(bonds).map((bond) => ({
    label:
      typeof bond.hydrogenCount === "number" && bond.hydrogenCount > 1
        ? t("workspace:tinymce.bondStrengths.table.equivalentHydrogens", {
            label: bond.label,
            count: bond.hydrogenCount,
          })
        : bond.label,
    weakest: bond.weakest,
    values:
      bond.supported && typeof bond.bdeKcalMol === "number" && typeof bond.bdeKjMol === "number"
        ? { kcalMol: oneDecimal.format(bond.bdeKcalMol), kjMol: oneDecimal.format(bond.bdeKjMol) }
        : null,
    notAvailable: notAvailableText(t, bond.unsupportedReason),
  }));
}

function pictureAndColumnText(t: Translate) {
  return {
    imageAlt: t("workspace:tinymce.bondStrengths.imageAlt"),
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
): BondStrengthsDocumentContent {
  return {
    ...pictureAndColumnText(t),
    imageSrc: result.imageSvg ? svgDataUri(result.imageSvg) : null,
    caption: t("workspace:tinymce.bondStrengths.document.caption", {
      name: result.method.name,
      version: result.method.version,
      error: formatNumber(result.method.meanAbsoluteErrorKcalMol, language),
      date: new Intl.DateTimeFormat(language, { dateStyle: "long" }).format(new Date()),
    }),
    rows: rows.map((row) => {
      const bond = row.weakest
        ? t("workspace:tinymce.bondStrengths.document.weakestBond", { label: row.label })
        : row.label;
      return row.values ? { bond, ...row.values } : { bond, notAvailable: row.notAvailable };
    }),
  };
}

function SupportedResult({ result, rows }: { result: SupportedBondStrengths; rows: ReadonlyArray<BondRow> }) {
  const { t, i18n } = useTranslation(["workspace", "common"]);
  const language = i18n.resolvedLanguage ?? i18n.language;
  const { imageAlt, columns } = pictureAndColumnText(t);

  return (
    <Stack spacing={2}>
      {result.imageSvg && (
        <Box
          component="img"
          src={svgDataUri(result.imageSvg)}
          alt={imageAlt}
          sx={{ display: "block", maxWidth: "100%", height: "auto", mx: "auto" }}
        />
      )}
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
                  {row.weakest && <Chip size="small" label={t("tinymce.bondStrengths.table.weakest")} sx={{ ml: 1 }} />}
                </TableCell>
                {row.values ? (
                  <>
                    <TableCell align="right">{row.values.kcalMol}</TableCell>
                    <TableCell align="right">{row.values.kjMol}</TableCell>
                  </>
                ) : (
                  <TableCell align="right" colSpan={2}>
                    {row.notAvailable}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
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
  const rows = supported ? bondRows(t, language, supported.bonds) : [];

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
        {supported && <SupportedResult result={supported} rows={rows} />}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.close")}</Button>
        {supported && onInsert && (
          <Button
            variant="contained"
            color="callToAction"
            onClick={() => {
              onInsert(documentContent(t, language, supported, rows));
              onClose();
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
