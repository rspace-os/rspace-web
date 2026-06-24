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
  const { t } = useTranslation(["apps", "common"]);
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
                "aria-label": "Select compound",
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
              <Dt>{t("tinyMce.pubchem.pubchemId")}</Dt>
              <Dd>{compound.pubchemId}</Dd>
              {compound.cas !== "" && (
                <>
                  <Dt>{t("tinyMce.pubchem.casNumber")}</Dt>
                  <Dd>{compound.cas}</Dd>
                </>
              )}
              <Dt>{t("tinyMce.pubchem.formula")}</Dt>
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
              {t("tinyMce.pubchem.viewOnPubChem")}
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
          alt={`Chemical structure of ${compound.name}`}
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
  title = "Search PubChem",
  submitButtonText = "Select Compounds",
  showPubChemInfo = true,
  allowMultipleSelection = true,
}: CompoundSearchDialogProps): React.ReactNode {
  const titleId = React.useId();
  const resultsId = React.useId();
  const { t } = useTranslation(["apps", "common"]);
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
      : IsInvalid("Please select at least one compound.");
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
      <AppBar variant="dialog" currentPage="PubChem" accessibilityTips={{}} />
      <DialogTitle id={titleId} component="h3">
        {title}
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
              <Typography variant="body2">
                Search PubChem for chemical compounds by name, CAS number, or SMILES string.
              </Typography>
              <Typography variant="body2">
                See{" "}
                <Link href="https://pubchem.ncbi.nlm.nih.gov/" rel="noreferrer">
                  https://pubchem.ncbi.nlm.nih.gov/
                </Link>{" "}
                and our <Link href={docLinks.pubchem}>PubChem integration docs</Link> for more.
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
                  placeholder={searchType === "NAME" ? "Enter a compound name or CAS number" : "Enter a SMILES string"}
                  slotProps={{
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <FormControl variant="standard" size="small">
                            <Select
                              inputProps={{
                                "aria-label": "Search type",
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
                              <MenuItem value="NAME">Name/CAS</MenuItem>
                              <MenuItem value="SMILES">SMILES</MenuItem>
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
              Search Results
            </Typography>
            <Grid container spacing={2}>
              {!hasSearched && (
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    Enter a search term and click Search to find chemical compounds.
                  </Typography>
                </Grid>
              )}
              {hasSearched && results.length === 0 && (
                <Grid size={12}>
                  <Typography variant="body2" color="text.secondary">
                    No compounds found for "{displayedSearchTerm}". Try a different search term.
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
          {submitButtonText}
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
}
