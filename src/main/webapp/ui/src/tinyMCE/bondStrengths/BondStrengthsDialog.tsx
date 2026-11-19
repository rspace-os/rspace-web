import ScienceOutlinedIcon from "@mui/icons-material/ScienceOutlined";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import { alpha, ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { useQuery } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import React, { useId, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/chemistry";
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
import type { BondStrengthsDocumentContent, BondStrengthsInsertOutcome } from "./insertIntoDocument";

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
  onInsert?: (content: BondStrengthsDocumentContent) => Promise<BondStrengthsInsertOutcome>;
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
  // The model's typical error is close to 1 kcal/mol, so decimals would suggest precision it does not have.
  const wholeNumber = new Intl.NumberFormat(language, { maximumFractionDigits: 0 });
  return sortWeakestFirst(bonds)
    .filter(hasValue)
    .map((bond) => ({
      label: bondLabel(t, bond),
      weakest: bond.weakest,
      kcalMol: wholeNumber.format(bond.bdeKcalMol),
      kjMol: wholeNumber.format(bond.bdeKjMol),
    }));
}

/**
 * One sentence per reason a bond has no value. Ring and multiple bonds are only counted, because
 * every structure with a ring has them; the rarer cases name the bonds. The document version
 * counts ring and multiple bonds together, to keep the inserted text short.
 */
function noValueNotes(t: Translate, language: string, result: SupportedBondStrengths, forDocument = false): string[] {
  const withoutValue = result.bonds.filter((bond) => !hasValue(bond));
  const labelsFor = (reason: string | null) =>
    withoutValue.filter((bond) => (bond.unsupportedReason ?? null) === reason).map((bond) => bondLabel(t, bond));
  const list = new Intl.ListFormat(language, { style: "long", type: "conjunction" });
  const notes: string[] = [];

  const ringBonds = labelsFor("RING_BOND").length;
  const multipleBonds = labelsFor("MULTIPLE_BOND").length;
  if (forDocument && ringBonds + multipleBonds > 0) {
    notes.push(
      t("workspace:tinymce.bondStrengths.document.skippedBonds", {
        count: ringBonds + multipleBonds,
      }),
    );
  }
  if (!forDocument && ringBonds > 0) {
    notes.push(
      t("workspace:tinymce.bondStrengths.noValue.ringBonds", {
        count: ringBonds,
      }),
    );
  }
  if (!forDocument && multipleBonds > 0) {
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
  imageSrc: string | null,
  chemId: number,
): BondStrengthsDocumentContent {
  return {
    columns: pictureAndColumnText(t).columns,
    imageAlt: t("workspace:tinymce.bondStrengths.document.imageAlt"),
    legend: t("workspace:tinymce.bondStrengths.document.legend"),
    imageSrc,
    forChemicalId: String(chemId),
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
    notes: noValueNotes(t, language, result, true),
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
  const { t } = useTranslation(["workspace", "common"]);
  const { imageAlt, legend, columns } = pictureAndColumnText(t);

  return (
    <Stack spacing={2}>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "minmax(0, 1fr)", md: "minmax(0, 1fr) minmax(0, 1fr)" },
          gap: 2,
          alignItems: "start",
        }}
      >
        {result.imageSvg && (
          // On a wide dialog the picture stays in view while the table scrolls, because the rows are
          // named by the atom numbers shown on it.
          <Box component="figure" sx={{ m: 0, position: { md: "sticky" }, top: 0 }}>
            <Box
              component="img"
              src={svgDataUri(result.imageSvg)}
              alt={imageAlt}
              sx={{ display: "block", maxWidth: "100%", height: "auto", mx: "auto" }}
            />
            {rows.length > 0 && (
              <Typography component="figcaption" variant="caption" sx={{ display: "block" }}>
                {legend}
              </Typography>
            )}
          </Box>
        )}
        <Stack spacing={2}>
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
        </Stack>
      </Box>
      <Typography variant="body2">
        {t("tinymce.bondStrengths.coverage", {
          supported: result.supportedBondCount,
          total: result.totalBondCount,
        })}
      </Typography>
      <AboutCalculation method={result.method} />
    </Stack>
  );
}

/**
 * Provenance for the record. The model, its accuracy and the training data credit are always shown,
 * so every reader sees that the values are predictions and where they come from; the rest is one
 * click away.
 */
function AboutCalculation({ method }: { method: SupportedBondStrengths["method"] }) {
  const { t, i18n } = useTranslation(["workspace", "common"]);
  const language = i18n.resolvedLanguage ?? i18n.language;
  const [showAll, setShowAll] = useState(false);
  const titleId = useId();
  const detailsId = useId();
  const error = formatNumber(method.meanAbsoluteErrorKcalMol, language);
  const always: ReadonlyArray<[label: string, value: string | null | undefined]> = [
    [
      t("tinymce.bondStrengths.about.model"),
      t("tinymce.bondStrengths.about.modelValue", {
        name: method.name,
        version: method.version,
      }),
    ],
    [t("tinymce.bondStrengths.about.accuracy"), t("tinymce.bondStrengths.about.accuracyValue", { error })],
    [t("tinymce.bondStrengths.about.trainingData"), method.trainingData],
  ];
  const more: ReadonlyArray<[label: string, value: string | null | undefined]> = [
    [t("tinymce.bondStrengths.about.quantity"), method.quantity],
    [t("tinymce.bondStrengths.about.accuracyNote"), method.accuracyNote],
    [t("tinymce.bondStrengths.about.referenceMethod"), method.referenceMethod],
    [t("tinymce.bondStrengths.about.weights"), method.weightsSha256.slice(0, 12)],
  ];

  return (
    <Paper
      component="section"
      variant="outlined"
      aria-labelledby={titleId}
      sx={(theme) => ({
        p: 2,
        borderLeft: `4px solid ${theme.palette.primary.dark}`,
        backgroundColor: alpha(theme.palette.primary.main, 0.08),
      })}
    >
      <Stack direction="row" spacing={1.5} sx={{ alignItems: "flex-start" }}>
        <ScienceOutlinedIcon sx={{ color: "primary.dark" }} />
        <Stack spacing={1} sx={{ minWidth: 0, flexGrow: 1 }}>
          <Typography id={titleId} variant="subtitle2" component="h3" sx={{ color: "primary.dark" }}>
            {t("tinymce.bondStrengths.about.title")}
          </Typography>
          <Typography variant="body2">{t("tinymce.bondStrengths.method", { error })}</Typography>
          <Box
            component="dl"
            id={detailsId}
            sx={{
              m: 0,
              display: "grid",
              // A minimum width keeps the values from jumping sideways when the longer labels appear.
              gridTemplateColumns: "minmax(max-content, 12em) minmax(0, 1fr)",
              columnGap: 2,
              rowGap: 0.5,
            }}
          >
            {(showAll ? [...always, ...more] : always).map(
              ([label, value]) =>
                value && (
                  <React.Fragment key={label}>
                    <Typography component="dt" variant="body2" sx={{ fontWeight: "bold" }}>
                      {label}
                    </Typography>
                    <Typography component="dd" variant="body2" sx={{ m: 0, overflowWrap: "anywhere" }}>
                      {value}
                    </Typography>
                  </React.Fragment>
                ),
            )}
          </Box>
          <Box>
            <Button
              size="small"
              variant="outlined"
              aria-expanded={showAll}
              aria-controls={detailsId}
              onClick={() => setShowAll((shown) => !shown)}
            >
              {showAll ? t("tinymce.bondStrengths.about.showFewer") : t("tinymce.bondStrengths.about.showAll")}
            </Button>
          </Box>
        </Stack>
      </Stack>
    </Paper>
  );
}

function BondStrengthsResult({ onClose, chemId, revision, onInsert }: Omit<BondStrengthsDialogProps, "open">) {
  const { t, i18n } = useTranslation(["workspace", "common"]);
  const language = i18n.resolvedLanguage ?? i18n.language;
  const { data, isPending, isError } = useQuery(bondStrengthsQuery(chemId, revision));
  const supported = data?.supported ? data : null;
  const [inserting, setInserting] = useState(false);
  const [insertProblem, setInsertProblem] = useState<"noPicture" | "failed" | null>(null);
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
        {insertProblem === "noPicture" && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            {t("tinymce.bondStrengths.dialog.insertedWithoutPicture")}
          </Alert>
        )}
        {insertProblem === "failed" && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {t("tinymce.bondStrengths.dialog.insertFailed")}
          </Alert>
        )}
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
              setInsertProblem(null);
              // Without a picture the table still goes in.
              const picture = supported.imageSvg
                ? svgToPngDataUri(supported.imageSvg).catch(() => null)
                : Promise.resolve(null);
              void picture
                .then((imageSrc) => onInsert(documentContent(t, language, supported, rows, imageSrc, chemId)))
                .then((outcome) => {
                  if (supported.imageSvg && outcome === "insertedWithoutPicture") {
                    setInsertProblem("noPicture");
                  } else {
                    onClose();
                  }
                })
                .catch((error: unknown) => {
                  console.warn("The bond strengths could not be inserted", error);
                  setInsertProblem("failed");
                })
                .finally(() => setInserting(false));
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
  // The editor sidebar and view mode open the dialog under different themes, so it brings its own.
  const theme = useMemo(() => createAccentedTheme(ACCENT_COLOR), []);

  return (
    <ThemeProvider theme={theme}>
      <Dialog open={open} onClose={onClose} aria-labelledby={titleId} maxWidth="md" fullWidth>
        <DialogTitle id={titleId}>{t("tinymce.bondStrengths.dialog.title")}</DialogTitle>
        {/* Dialog only mounts its children while open, so the request is made on open and not before. */}
        <BondStrengthsResult onClose={onClose} {...resultProps} />
      </Dialog>
    </ThemeProvider>
  );
}
