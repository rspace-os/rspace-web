import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActionArea from "@mui/material/CardActionArea";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Checkbox, { checkboxClasses } from "@mui/material/Checkbox";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import Grid from "@mui/material/Grid";
import InputAdornment from "@mui/material/InputAdornment";
import Link from "@mui/material/Link";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import { alpha } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import { ACCENT_COLOR } from "@/assets/branding/pubchem";
import { Dialog } from "@/components/DialogBoundary";
import useChemicalImport, { type ChemicalCompound } from "@/hooks/api/useChemicalImport";
import docLinks from "../../assets/DocLinks";
import AppBar from "../../components/AppBar";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";

function Dl({ children }: { children: React.ReactNode }): React.ReactNode {
  return (
    <Typography
      component="dl"
      sx={{
        display: "grid",
        gridTemplateColumns: "auto 1fr",
        mt: 1,
        mb: 0,
      }}
    >
      {children}
    </Typography>
  );
}
function Dt({ children }: { children: React.ReactNode }): React.ReactNode {
  return (
    <Typography
      component="dt"
      sx={{
        lineHeight: 1.8,
        textTransform: "uppercase",
        fontWeight: 600,
        marginRight: 2,
        fontSize: "0.8rem",
      }}
      role="term"
    >
      {children}
    </Typography>
  );
}
function Dd({ children }: { children: React.ReactNode }): React.ReactNode {
  return (
    <Typography
      component="dd"
      sx={{
        lineHeight: 1.8,
        fontSize: "0.8rem",
      }}
      role="definition"
    >
      {children}
    </Typography>
  );
}
type CompoundCardProps = {
  selected: boolean;
  compound: ChemicalCompound;
  onSelected: (compound: ChemicalCompound, selected: boolean) => void;
};

function CompoundCard({ selected, compound, onSelected }: CompoundCardProps): React.ReactNode {
  const nameId = React.useId();
  const { t } = useTranslation(["workspace", "common"]);
  return (
    <Card
      aria-labelledby={nameId}
      role="region"
      sx={(theme) => ({
        border: `2px solid ${selected ? theme.palette.callToAction.main : theme.palette.primary.main}`,
        backgroundColor: selected ? `${alpha(theme.palette.callToAction.background, 0.15)}` : "initial",
        boxShadow: selected ? "none" : `hsl(${ACCENT_COLOR.main.hue} 66% 20% / 20%) 0px 2px 8px 0px`,
        "&:hover": {
          border: window.matchMedia("(prefers-contrast: more)").matches
            ? "2px solid black !important"
            : `2px solid ${theme.palette.callToAction.main} !important`,
          backgroundColor: `${alpha(theme.palette.callToAction.background, 0.05)} !important`,
        },
        [`& .${checkboxClasses.root}`]: {
          color: selected ? theme.palette.callToAction.main : theme.palette.primary.main,
        },
      })}
    >
      <CardActionArea
        onClick={() => onSelected(compound, !selected)}
        sx={{
          display: "flex",
          alignItems: "stretch",
        }}
      >
        <Box>
          <Checkbox
            checked={selected}
            onChange={(e) => onSelected(compound, e.target.checked)}
            slotProps={{
              input: {
                "aria-label": t("tinymce.pubchem.dialog.selectCompoundLabel"),
              },
            }}
          />
        </Box>
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <CardContent
            sx={{
              flex: "1 0 auto",
              pt: 1,
              pl: 0.5,
            }}
          >
            <Typography component="div" variant="h6" id={nameId}>
              {compound.name}
            </Typography>
            <Dl>
              <Dt>{t("tinymce.pubchem.pubchemId")}</Dt>
              <Dd>{compound.pubchemId}</Dd>
              {compound.cas !== "" && (
                <>
                  <Dt>{t("tinymce.pubchem.casNumber")}</Dt>
                  <Dd>{compound.cas}</Dd>
                </>
              )}
              <Dt>{t("tinymce.pubchem.formula")}</Dt>
              <Dd>{compound.formula}</Dd>
            </Dl>
          </CardContent>
          <CardActions
            sx={{
              pl: 0,
            }}
          >
            <Link
              href={compound.pubchemUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={(e) => {
                e.stopPropagation();
              }}
            >
              {t("tinymce.pubchem.viewOnPubChem")}
            </Link>
          </CardActions>
        </Box>
        <CardMedia
          component="img"
          sx={{
            width: 151,
            m: 1,
            borderRadius: "3px",
            objectFit: "scale-down",
            alignSelf: "flex-start",
          }}
          image={compound.pngImage}
          alt={t("tinymce.pubchem.dialog.chemicalStructureAlt", { name: compound.name })}
        />
      </CardActionArea>
    </Card>
  );
}
export interface CompoundSearchDialogProps {
  open: boolean;
  onClose: () => void;
  onCompoundsSelected: (compounds: ChemicalCompound[]) => void;
  title?: string;
  submitButtonText?: string;
  showPubChemInfo?: boolean;
  allowMultipleSelection?: boolean;
}

/**
 * Reusable dialog for searching and selecting chemical compounds from PubChem.
 * This component handles the search UI and compound selection, but delegates
 * the handling of selected compounds to the parent via onCompoundsSelected.
 */
export default function CompoundSearchDialog({
  open,
  onClose,
  onCompoundsSelected,
  title,
  submitButtonText,
  showPubChemInfo = true,
  allowMultipleSelection = true,
}: CompoundSearchDialogProps): React.ReactNode {
  const titleId = React.useId();
  const resultsId = React.useId();
  const { t } = useTranslation(["workspace", "common"]);
  const { search } = useChemicalImport();
  const [searchTerm, setSearchTerm] = React.useState("");
  const [searchType, setSearchType] = React.useState<"NAME" | "SMILES">("NAME");
  const [results, setResults] = React.useState<ReadonlyArray<ChemicalCompound>>([]);
  const [selectedCompounds, setSelectedCompounds] = React.useState<Record<string, boolean>>({});
  const [hasSearched, setHasSearched] = React.useState(false);
  const [displayedSearchTerm, setDisplayedSearchTerm] = React.useState("");
  const validationResult = React.useMemo(() => {
    return Object.values(selectedCompounds).some(Boolean)
      ? IsValid()
      : IsInvalid(t("tinymce.pubchem.dialog.validation.selectCompound"));
  }, [selectedCompounds]);
  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    void search({
      searchTerm,
      searchType,
    })
      .then((newResults) => {
        setResults(newResults);
        // Auto-select when there's exactly one result.
        setSelectedCompounds(Object.fromEntries(newResults.map((c) => [c.pubchemId, newResults.length === 1])));
        setHasSearched(true);
        setDisplayedSearchTerm(searchTerm);
      })
      .catch(() => {
        /*
         * `useChemicalImport.search` already reports the failure to the user via
         * an alert toast. Swallow the rejected promise here so a handled API
         * error does not bubble up as an unhandled rejection in the UI or tests.
         */
      });
  }
  React.useEffect(() => {
    if (!open) {
      setSearchTerm("");
      setSearchType("NAME");
      setResults([]);
      setSelectedCompounds({});
      setHasSearched(false);
      setDisplayedSearchTerm("");
    }
  }, [open]);
  function handleCompoundSelection(compound: ChemicalCompound, checked: boolean) {
    if (allowMultipleSelection) {
      setSelectedCompounds((prev) => ({
        ...prev,
        [compound.pubchemId]: checked,
      }));
    } else {
      // Single selection mode - uncheck all others
      setSelectedCompounds({
        [compound.pubchemId]: checked,
      });
    }
  }
  function handleSubmit() {
    onCompoundsSelected(results.filter((r) => selectedCompounds[r.pubchemId]));
    onClose();
  }
  return (
    <Dialog open={open} onClose={onClose} aria-labelledby={titleId} maxWidth="sm" fullWidth>
      <AppBar variant="dialog" currentPage={t("tinymce.pubchem.dialog.title")} accessibilityTips={{}} />
      <DialogTitle id={titleId} component="h3">
        {title ?? t("tinymce.pubchem.dialog.title")}
      </DialogTitle>
      <DialogContent>
        <Stack
          spacing={2}
          sx={{
            flexWrap: "nowrap",
          }}
        >
          {showPubChemInfo && (
            <Box>
              <Typography variant="body2">{t("tinymce.pubchem.dialog.intro.searchDescription")}</Typography>
              <Typography variant="body2">
                {t("tinymce.pubchem.dialog.intro.moreInfoPrefix")}{" "}
                <Link href="https://pubchem.ncbi.nlm.nih.gov/" rel="noreferrer">
                  {"https://pubchem.ncbi.nlm.nih.gov/"}
                </Link>{" "}
                {t("tinymce.pubchem.dialog.intro.moreInfoMiddle")}{" "}
                <Link href={docLinks.pubchem}>{t("tinymce.pubchem.dialog.intro.docsLink")}</Link>{" "}
                {t("tinymce.pubchem.dialog.intro.moreInfoSuffix")}
              </Typography>
            </Box>
          )}
          <form onSubmit={handleSearch}>
            <Grid container spacing={1} direction="row">
              <Grid
                sx={{
                  flexGrow: 1,
                }}
              >
                <TextField
                  value={searchTerm}
                  onChange={(e) => {
                    setSearchTerm(e.target.value);
                    if (hasSearched && results.length === 0) {
                      setHasSearched(false);
                    }
                  }}
                  variant="outlined"
                  fullWidth
                  placeholder={
                    searchType === "NAME"
                      ? t("tinymce.pubchem.dialog.searchPlaceholders.nameCas")
                      : t("tinymce.pubchem.dialog.searchPlaceholders.smiles")
                  }
                  slotProps={{
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <FormControl variant="standard" size="small">
                            <Select
                              inputProps={{
                                "aria-label": t("tinymce.pubchem.dialog.searchTypeLabel"),
                                name: "search-type",
                              }}
                              value={searchType}
                              onChange={(e) => setSearchType(e.target.value)}
                              sx={{
                                "&::before, &::after": {
                                  display: "none",
                                },
                                ":hover": {
                                  backgroundColor: "rgba(0, 0, 0, 0.05)",
                                },
                              }}
                            >
                              <MenuItem value="NAME">{t("tinymce.pubchem.dialog.searchTypes.nameCas")}</MenuItem>
                              <MenuItem value="SMILES">{t("tinymce.pubchem.dialog.searchTypes.smiles")}</MenuItem>
                            </Select>
                          </FormControl>
                        </InputAdornment>
                      ),
                    },
                  }}
                />
              </Grid>
              <Grid>
                <Button type="submit">{t("common:actions.search")}</Button>
              </Grid>
            </Grid>
          </form>
          <section aria-labelledby={resultsId} aria-live="polite">
            <Typography id={resultsId} variant="h6" component="h4">
              {t("tinymce.pubchem.dialog.searchResults")}
            </Typography>
            <Grid container spacing={2}>
              {!hasSearched && (
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    {t("tinymce.pubchem.dialog.emptyState.initial")}
                  </Typography>
                </Grid>
              )}
              {hasSearched && results.length === 0 && (
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    {t("tinymce.pubchem.dialog.emptyState.noneFound", { searchTerm: displayedSearchTerm })}
                  </Typography>
                </Grid>
              )}
              {results.map((compound) => (
                <Grid key={compound.pubchemId} size={12}>
                  <CompoundCard
                    selected={Boolean(selectedCompounds[compound.pubchemId])}
                    compound={compound}
                    onSelected={handleCompoundSelection}
                  />
                </Grid>
              ))}
            </Grid>
          </section>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onClose()}>{t("common:actions.cancel")}</Button>
        <ValidatingSubmitButton validationResult={validationResult} loading={false} onClick={handleSubmit}>
          {submitButtonText ?? t("tinymce.pubchem.dialog.submitButton")}
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
}
