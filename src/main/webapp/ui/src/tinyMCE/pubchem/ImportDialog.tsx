import React from "react";
import { Dialog } from "../../components/DialogBoundary";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import Link from "@mui/material/Link";
import DialogContent from "@mui/material/DialogContent";
import Box from "@mui/material/Box";
import TextField from "@mui/material/TextField";
import Grid from "@mui/material/Grid";
import InputAdornment from "@mui/material/InputAdornment";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import FormControl from "@mui/material/FormControl";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import CardActions from "@mui/material/CardActions";
import Checkbox from "@mui/material/Checkbox";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
} from "../../components/ValidatingSubmitButton";
import useChemicalImport, {
  type ChemicalCompound,
} from "@/hooks/api/useChemicalImport";
import { TwoColumnDl } from "@/components/DescriptionList";
import { type Theme, alpha } from "@mui/material/styles";
import styled from "@emotion/styled";
import { ACCENT_COLOR } from "@/assets/branding/pubchem";
import { CardActionArea } from "@mui/material";

type CompoundCardProps = {
  selected: boolean;
  compound: ChemicalCompound;
  onSelected: (compound: ChemicalCompound, selected: boolean) => void;
  className?: string;
};

type StyledProps = {
  selected: boolean;
  theme?: Theme;
};

const CompoundCard = styled(
  ({ selected, compound, onSelected, className }: CompoundCardProps) => {
    const nameId = React.useId();
    return (
      <Card className={className} aria-labelledby={nameId}>
        <CardActionArea
          onClick={() => onSelected(compound, !selected)}
          sx={{ display: "flex", alignItems: "stretch" }}
        >
          <Box>
            <Checkbox
              checked={selected}
              inputProps={{
                "aria-label": "Select compound",
              }}
              onChange={(e) => onSelected(compound, e.target.checked)}
            />
          </Box>
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              flexGrow: 1,
            }}
          >
            <CardContent sx={{ flex: "1 0 auto", pt: 1, pl: 0.5 }}>
              <Typography component="div" variant="h6" id={nameId}>
                {compound.name}
              </Typography>
              <TwoColumnDl sx={{ mt: 1, mb: 0 }}>
                <Typography component="dt" variant="overline">
                  PubChem ID
                </Typography>
                <Typography component="dd" variant="subtitle2">
                  {compound.pubchemId}
                </Typography>
                <Typography component="dt" variant="overline">
                  SMILES
                </Typography>
                <Typography
                  component="dd"
                  variant="subtitle2"
                  sx={{ wordBreak: "break-all" }}
                >
                  {compound.smiles}
                </Typography>
                <Typography component="dt" variant="overline">
                  Formula
                </Typography>
                <Typography component="dd" variant="subtitle2">
                  {compound.formula}
                </Typography>
                {compound.cas !== null && (
                  <>
                    <Typography component="dt" variant="overline">
                      CAS Number
                    </Typography>
                    <Typography component="dd" variant="subtitle2">
                      {compound.cas}
                    </Typography>
                  </>
                )}
              </TwoColumnDl>
            </CardContent>
            <CardActions sx={{ pl: 0 }}>
              <Link
                href={compound.pubchemUrl}
                target="_blank"
                rel="noopener noreferrer"
                onClick={(e) => {
                  e.stopPropagation();
                }}
              >
                View on PubChem
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
)(({ selected, theme }: StyledProps) => {
  if (!theme) return {};

  return {
    border: `2px solid ${
      selected ? theme.palette.callToAction.main : theme.palette.primary.main
    }`,
    backgroundColor: selected
      ? `${alpha(theme.palette.callToAction.background, 0.15)}`
      : "initial",
    boxShadow: selected
      ? "none"
      : `hsl(${ACCENT_COLOR.main.hue} 66% 20% / 20%) 0px 2px 8px 0px`,
    "&:hover": {
      border: window.matchMedia("(prefers-contrast: more)").matches
        ? "2px solid black !important"
        : `2px solid ${theme.palette.callToAction.main} !important`,
      backgroundColor: `${alpha(
        theme.palette.callToAction.background,
        0.05
      )} !important`,
    },
    "& .MuiCheckbox-root": {
      color: selected
        ? theme.palette.callToAction.main
        : theme.palette.primary.main,
    },
  };
});

/**
 * This dialog is opened by the TinyMCE plugin, allowing the users to browse
 * chemistry files on PubChem and importing into their document.
 */
export default function ImportDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}): React.ReactNode {
  const titleId = React.useId();
  const resultsId = React.useId();
  const { search } = useChemicalImport();
  const [searchTerm, setSearchTerm] = React.useState("");
  const [searchType, setSearchType] = React.useState<"NAME" | "SMILES">("NAME");
  const [results, setResults] = React.useState<ReadonlyArray<ChemicalCompound>>(
    []
  );
  const [selectedCompounds, setSelectedCompounds] = React.useState<
    Record<string, boolean>
  >({});
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  const validationResult = React.useMemo(() => {
    return Object.values(selectedCompounds).some((isSelected) => isSelected)
      ? IsValid()
      : IsInvalid("Please select at least one compound.");
  }, [selectedCompounds]);

  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    void search({ searchTerm, searchType }).then(setResults);
  }

  React.useEffect(() => {
    if (open) {
      setSelectedCompounds(
        Object.fromEntries(
          results.map((c) => [c.pubchemId, results.length === 1])
        )
      );
    }
  }, [open, results]);

  React.useEffect(() => {
    if (!open) {
      setSearchTerm("");
      setSearchType("NAME");
      setResults([]);
      setSelectedCompounds({});
      setIsSubmitting(false);
    }
  }, [open]);

  function handleCompoundSelection(
    compound: ChemicalCompound,
    checked: boolean
  ) {
    setSelectedCompounds((prev) => ({
      ...prev,
      [compound.pubchemId]: checked,
    }));
  }

  function handleSubmit() {
    setIsSubmitting(true);
    // Get the selected compounds
    const selected = Object.entries(selectedCompounds)
      .filter(([_, isSelected]) => isSelected)
      .map(([id]) => results.find((r) => r.pubchemId === id))
      .filter(Boolean) as ChemicalCompound[];

    // Here you would implement the logic to insert the compounds into the document
    console.log("Importing selected compounds:", selected);

    // Close the dialog when done
    onClose();
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      aria-labelledby={titleId}
      maxWidth="sm"
      fullWidth
    >
      <AppBar variant="dialog" currentPage="PubChem" accessibilityTips={{}} />
      <DialogTitle id={titleId} component="h3">
        Import from PubChem
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} flexWrap="nowrap">
          <Box>
            <Typography variant="body2">
              Importing a compond from PubChem will insert it into the document.
            </Typography>
            <Typography variant="body2">
              See <Link href="#">[pubchem website]</Link> and our{" "}
              <Link href="#">PubChem integration docs</Link> for more.
            </Typography>
          </Box>
          <form onSubmit={handleSearch}>
            <Grid container spacing={1} direction="row">
              <Grid item flexGrow={1}>
                <TextField
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  variant="outlined"
                  fullWidth
                  placeholder={
                    searchType === "NAME"
                      ? "Enter a compound name or CAS number"
                      : "SMILES"
                  }
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <FormControl variant="standard" size="small">
                          <Select
                            inputProps={{
                              "aria-label": "Search type",
                              name: "search-type",
                            }}
                            value={searchType}
                            onChange={(e) =>
                              setSearchType(e.target.value as "NAME" | "SMILES")
                            }
                          >
                            <MenuItem value="NAME">Name/CAS</MenuItem>
                            <MenuItem value="SMILES">SMILES</MenuItem>
                          </Select>
                        </FormControl>
                      </InputAdornment>
                    ),
                  }}
                />
              </Grid>
              <Grid item>
                <Button type="submit">Search</Button>
              </Grid>
            </Grid>
          </form>
          <section aria-labelledby={resultsId}>
            <Typography id={resultsId} variant="h6" component="h4">
              Search Results
            </Typography>
            <Grid container spacing={2}>
              {results.map((compound) => (
                <Grid item xs={12} key={compound.pubchemId}>
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
        <Button onClick={() => onClose()}>Cancel</Button>
        <ValidatingSubmitButton
          validationResult={validationResult}
          loading={isSubmitting}
          onClick={handleSubmit}
        >
          Import Selected
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
}
