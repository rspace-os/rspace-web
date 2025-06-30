import React from "react";
import Dialog from "@mui/material/Dialog";
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
import FormControlLabel from "@mui/material/FormControlLabel";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
} from "../../components/ValidatingSubmitButton";
import useChemicalImport, {
  type ChemicalCompound,
} from "@/hooks/api/useChemicalImport";
import DescriptionList from "@/components/DescriptionList";

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
                  <Card sx={{ display: "flex" }}>
                    <CardMedia
                      component="img"
                      sx={{ width: 151 }}
                      image={compound.pngImage}
                      alt={`Chemical structure of ${compound.name}`}
                    />
                    <Box
                      sx={{
                        display: "flex",
                        flexDirection: "column",
                        flexGrow: 1,
                      }}
                    >
                      <CardContent sx={{ flex: "1 0 auto" }}>
                        <Typography component="div" variant="h6">
                          {compound.name}
                        </Typography>
                        <DescriptionList
                          content={[
                            {
                              label: "PubChem ID",
                              value: compound.pubchemId,
                            },
                            {
                              label: "SMILES",
                              value: compound.smiles,
                            },
                            {
                              label: "Formula",
                              value: compound.formula,
                            },
                            ...(compound.cas !== null
                              ? [
                                  {
                                    label: "CAS Number",
                                    value: compound.cas,
                                  },
                                ]
                              : []),
                          ]}
                          sx={{ mt: 3 }}
                        />
                      </CardContent>
                      <CardActions>
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={Boolean(
                                selectedCompounds[compound.pubchemId]
                              )}
                              onChange={(e) =>
                                handleCompoundSelection(
                                  compound,
                                  e.target.checked
                                )
                              }
                            />
                          }
                          label="Select"
                        />
                        <Link
                          href={compound.pubchemUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          sx={{ ml: 1 }}
                        >
                          View on PubChem
                        </Link>
                      </CardActions>
                    </Box>
                  </Card>
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
