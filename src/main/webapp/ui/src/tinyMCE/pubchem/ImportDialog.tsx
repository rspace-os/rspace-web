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
import useChemicalImport, {
  type ChemicalCompound,
} from "@/hooks/api/useChemicalImport";

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

  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    void search({ searchTerm, searchType }).then(setResults);
  }

  return (
    <Dialog open={open} onClose={onClose} aria-labelledby={titleId}>
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
                  variant="standard"
                  fullWidth
                  inputProps={{
                    role: "search",
                  }}
                  placeholder="Enter a compound name, CAS number, or SMILES"
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <FormControl variant="standard" size="small">
                          <Select
                            value={searchType}
                            onChange={(e) =>
                              setSearchType(e.target.value as "NAME" | "SMILES")
                            }
                            aria-label="Search type"
                            name="searchType"
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
            <ul>
              {results.map((compound) => (
                <li key={compound.pubchemId}>{compound.name}</li>
              ))}
            </ul>
          </section>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onClose()}>Cancel</Button>
      </DialogActions>
    </Dialog>
  );
}
