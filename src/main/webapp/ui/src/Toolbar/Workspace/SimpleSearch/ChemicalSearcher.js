import React, { useState } from "react";
import KetcherDialog from "../../../components/Ketcher/KetcherDialog";
import axios from "@/common/axios";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import { DataGrid } from "@mui/x-data-grid";
import { DataGridColumn } from "../../../util/table";
import Link from "@mui/material/Link";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { IsValid, IsInvalid } from "../../../components/ValidatingSubmitButton";

const ChemicalSearcher = ({ isOpen, onClose }) => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [showSearchResults, setShowSearchResults] = useState(false);
  const [showKetcherDialog, setShowKetcherDialog] = useState(false);
  const [searchResults, setSearchResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [totalItems, setTotalItems] = useState(0);
  const [searchSmiles, setSearchSmiles] = useState("");
  const itemsPerPage = 10;
  const [searchType, setSearchType] = useState("SUBSTRUCTURE");
  const [paginationModel, setPaginationModel] = useState({
    page: 0,
    pageSize: itemsPerPage,
  });

  React.useEffect(() => {
    setShowKetcherDialog(isOpen);
  }, [isOpen]);

  const fetchData = (page, smiles) => {
    setLoading(true);
    const requestBody = {
      searchInput: smiles,
      pageNumber: page,
      pageSize: itemsPerPage,
      searchType,
    };
    axios
      .post("/chemical/search", requestBody)
      .then((response) => {
        setSearchResults(response.data.chemSearchResultsPage.hits);
        setTotalItems(response.data.chemSearchResultsPage.totalHitCount);
        setShowSearchResults(true);
        trackEvent("user:search:chemistry", {
          searchType,
          totalResults: response.data.chemSearchResultsPage.totalHitCount,
        });
      })
      .catch((error) => {
        setErrorMessage("Error fetching search results: " + error.message);
      })
      .finally(() => {
        setLoading(false);
      });
    onClose();
  };

  const closeAndReset = () => {
    window.ketcher.setMolecule("");
    setSearchSmiles("");
    setShowKetcherDialog(false);
    setShowSearchResults(false);
    setLoading(false);
    setErrorMessage(null);
    onClose();
  };

  const goBackToSearch = () => {
    setShowKetcherDialog(true);
    setShowSearchResults(false);
    setLoading(false);
    setErrorMessage(null);
  };

  const [isValid, setIsValid] = useState(IsValid());
  const validate = (ketcher) => {
    if (!ketcher) {
      setIsValid(IsValid());
      return;
    }
    ketcher.getKet().then((ketData) => {
      const molecules = Object.keys(JSON.parse(ketData)).filter((key) =>
        key.startsWith("mol")
      );
      if (molecules.length > 1) {
        setIsValid(
          IsInvalid(
            "Chemical search currently supports a single molecule. Please remove extra molecules from the canvas."
          )
        );
        return;
      }
      setIsValid(IsValid());
    });
  };

  const handleInsert = (ketcher) => {
    setShowKetcherDialog(false);
    ketcher.getSmiles().then((smiles) => {
      setSearchSmiles(smiles);
      fetchData(0, smiles);
      void window.ketcher.setMolecule("");
    });
  };

  const columns = [
    DataGridColumn.newColumnWithValueGetter(
      "preview",
      ({ breadcrumb, chemId }) =>
        breadcrumb.startsWith("/Gallery")
          ? "/images/icons/chemistry-file.png"
          : "/chemical/getImageChem/" + chemId + "/1",
      {
        headerName: "Preview",
        renderCell: (params) => (
          <img
            src={params.value}
            alt="Chemical Preview"
            style={{ width: "150px", height: "150px", marginTop: "8px" }}
          />
        ),
        sortable: false,
        flex: 1,
      }
    ),
    DataGridColumn.newColumnWithValueGetter(
      "name",
      ({ breadcrumb, globalId, recordName }) => {
        if (breadcrumb.startsWith("/Gallery")) {
          return {
            href: "/gallery?mediaType=Chemistry",
            text: breadcrumb.slice(1),
          };
        }
        return {
          href: `/globalId/${globalId}`,
          text: recordName,
        };
      },
      {
        headerName: "Name",
        renderCell: (params) => (
          <Link href={params.value.href} target="_blank" rel="noreferrer">
            {params.value.text}
          </Link>
        ),
        sortable: false,
        flex: 1,
      }
    ),
    DataGridColumn.newColumnWithFieldName("owner", {
      headerName: "Owner",
      sortable: false,
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName("lastModified", {
      headerName: "Last Modified",
      sortable: true,
      flex: 1,
    }),
  ];

  return (
    <div>
      <KetcherDialog
        isOpen={showKetcherDialog}
        title={"Chemistry Search"}
        handleInsert={handleInsert}
        actionBtnText="Search"
        handleClose={closeAndReset}
        existingChem={searchSmiles}
        validationResult={isValid}
        instructionText="Draw a single molecule below to search"
        onChange={() => {
          validate(window.ketcher);
        }}
        additionalControls={
          <FormControl>
            <FormLabel id="search-type">Search Type</FormLabel>
            <RadioGroup
              row
              aria-labelledby="search-type"
              name="search-type"
              value={searchType}
              onChange={(e) => {
                setSearchType(e.target.value);
              }}
            >
              <FormControlLabel
                value="SUBSTRUCTURE"
                control={<Radio />}
                label="Substructure"
              />
              <FormControlLabel
                value="EXACT"
                control={<Radio />}
                label="Exact"
              />
            </RadioGroup>
          </FormControl>
        }
      />
      <Dialog
        open={showSearchResults || loading || typeof errorMessage === "string"}
        fullWidth
        maxWidth="xl"
        PaperProps={{ style: { maxHeight: "90vh", minHeight: "90vh" } }}
      >
        <DialogTitle>Chemical Search</DialogTitle>
        <DialogContent>
          <div className="search-results-dialog">
            {loading || typeof errorMessage === "string" ? (
              <Box sx={{ textAlign: "center", width: "100%" }}>
                <Typography>{errorMessage ?? "Loading..."}</Typography>
              </Box>
            ) : (
              <DataGrid
                columns={columns}
                rows={searchResults}
                initialState={{
                  columns: {
                    columnVisibilityModel: {
                      preview: true,
                    },
                  },
                  pagination: {
                    rowCount: -1,
                  },
                }}
                disableColumnFilter
                rowHeight={150 + 2 * 8}
                autoHeight
                localeText={{
                  noRowsLabel: "No Search Results",
                }}
                getRowId={(row) => row.chemId}
                paginationMode="server"
                pageSizeOptions={[itemsPerPage]}
                paginationModel={paginationModel}
                paginationMeta={{ hasNextPage: totalItems !== null }}
                onPaginationModelChange={(model) => {
                  setPaginationModel(model);
                  fetchData(model.page, searchSmiles);
                }}
                rowCount={totalItems}
              />
            )}
          </div>
        </DialogContent>
        <DialogActions>
          <Button onClick={goBackToSearch}>Back</Button>
          <Button onClick={closeAndReset}>Close</Button>
        </DialogActions>
      </Dialog>
    </div>
  );
};

export default ChemicalSearcher;
