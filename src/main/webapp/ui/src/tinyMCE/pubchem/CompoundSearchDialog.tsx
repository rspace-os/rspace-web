import styled from "@emotion/styled";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActionArea from "@mui/material/CardActionArea";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Checkbox from "@mui/material/Checkbox";
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
import { alpha, type Theme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { ACCENT_COLOR } from "@/assets/branding/pubchem";
import useChemicalImport, { type ChemicalCompound } from "@/hooks/api/useChemicalImport";
import docLinks from "../../assets/DocLinks";
import AppBar from "../../components/AppBar";
import { Dialog } from "../../components/DialogBoundary";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";

function Dl({ children }: { children: React.ReactNode }): React.ReactNode {
    return (
        <Typography component="dl" sx={{ display: "grid", gridTemplateColumns: "auto 1fr", mt: 1, mb: 0 }}>
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
        <Typography component="dd" sx={{ lineHeight: 1.8, fontSize: "0.8rem" }} role="definition">
            {children}
        </Typography>
    );
}

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

const CompoundCard = styled(({ selected, compound, onSelected, className }: CompoundCardProps) => {
    const nameId = React.useId();
    return (
        <Card className={className} aria-labelledby={nameId} role="region">
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
                        <Dl>
                            <Dt>PubChem ID</Dt>
                            <Dd>{compound.pubchemId}</Dd>
                            {compound.cas !== "" && (
                                <>
                                    <Dt>CAS Number</Dt>
                                    <Dd>{compound.cas}</Dd>
                                </>
                            )}
                            <Dt>Formula</Dt>
                            <Dd>{compound.formula}</Dd>
                        </Dl>
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
})(({ selected, theme }: StyledProps) => {
    if (!theme) return {};

    return {
        border: `2px solid ${selected ? theme.palette.callToAction.main : theme.palette.primary.main}`,
        backgroundColor: selected ? `${alpha(theme.palette.callToAction.background, 0.15)}` : "initial",
        boxShadow: selected ? "none" : `hsl(${ACCENT_COLOR.main.hue} 66% 20% / 20%) 0px 2px 8px 0px`,
        "&:hover": {
            border: window.matchMedia("(prefers-contrast: more)").matches
                ? "2px solid black !important"
                : `2px solid ${theme.palette.callToAction.main} !important`,
            backgroundColor: `${alpha(theme.palette.callToAction.background, 0.05)} !important`,
        },
        "& .MuiCheckbox-root": {
            color: selected ? theme.palette.callToAction.main : theme.palette.primary.main,
        },
    };
});

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
    const { search } = useChemicalImport();
    const [searchTerm, setSearchTerm] = React.useState("");
    const [searchType, setSearchType] = React.useState<"NAME" | "SMILES">("NAME");
    const [results, setResults] = React.useState<ReadonlyArray<ChemicalCompound>>([]);
    const [selectedCompounds, setSelectedCompounds] = React.useState<Record<string, boolean>>({});
    const [hasSearched, setHasSearched] = React.useState(false);
    const [displayedSearchTerm, setDisplayedSearchTerm] = React.useState("");

    const validationResult = React.useMemo(() => {
        return Object.values(selectedCompounds).some((isSelected) => isSelected)
            ? IsValid()
            : IsInvalid("Please select at least one compound.");
    }, [selectedCompounds]);

    function handleSearch(e: React.FormEvent) {
        e.preventDefault();
        void search({ searchTerm, searchType }).then((newResults) => {
            setResults(newResults);
            setHasSearched(true);
            setDisplayedSearchTerm(searchTerm);
        });
    }

    React.useEffect(() => {
        if (open) {
            setSelectedCompounds(Object.fromEntries(results.map((c) => [c.pubchemId, results.length === 1])));
        }
    }, [open, results]);

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
        // Get the selected compounds
        const selected = Object.entries(selectedCompounds)
            .filter(([_, isSelected]) => isSelected)
            .map(([id]) => results.find((r) => r.pubchemId === id))
            .filter(Boolean) as ChemicalCompound[];

        onCompoundsSelected(selected);
        onClose();
    }

    return (
        <Dialog open={open} onClose={onClose} aria-labelledby={titleId} maxWidth="sm" fullWidth>
            <AppBar variant="dialog" currentPage="PubChem" accessibilityTips={{}} />
            <DialogTitle id={titleId} component="h3">
                {title}
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2} flexWrap="nowrap">
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
                            <Grid item flexGrow={1}>
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
                                            ? "Enter a compound name or CAS number"
                                            : "Enter a SMILES string"
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
                                    }}
                                />
                            </Grid>
                            <Grid item>
                                <Button type="submit">Search</Button>
                            </Grid>
                        </Grid>
                    </form>
                    <section aria-labelledby={resultsId} aria-live="polite">
                        <Typography id={resultsId} variant="h6" component="h4">
                            Search Results
                        </Typography>
                        <Grid container spacing={2}>
                            {!hasSearched && (
                                <Grid item xs={12}>
                                    <Typography variant="body2" color="text.secondary">
                                        Enter a search term and click Search to find chemical compounds.
                                    </Typography>
                                </Grid>
                            )}
                            {hasSearched && results.length === 0 && (
                                <Grid item xs={12}>
                                    <Typography variant="body2" color="text.secondary">
                                        No compounds found for "{displayedSearchTerm}". Try a different search term.
                                    </Typography>
                                </Grid>
                            )}
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
                <ValidatingSubmitButton validationResult={validationResult} loading={false} onClick={handleSubmit}>
                    {submitButtonText}
                </ValidatingSubmitButton>
            </DialogActions>
        </Dialog>
    );
}
