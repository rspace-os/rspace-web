import Backdrop from "@mui/material/Backdrop";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import { DataGrid } from "@mui/x-data-grid";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import {
  StructureSearchTypeControl,
  useSingleMoleculeValidation,
} from "../../../components/Ketcher/StructureSearchControls";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import type { ChemSearchType } from "../../../stores/definitions/Search";
import { DataGridColumn } from "../../../util/table";

const KetcherDialog = React.lazy(() => import("../../../components/Ketcher/KetcherDialog"));

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const ChemicalSearcher = ({ isOpen, onClose }: { isOpen: any; onClose: any }) => {
  const { t } = useTranslation(["workspace", "common"]);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [showSearchResults, setShowSearchResults] = useState(false);
  const [showKetcherDialog, setShowKetcherDialog] = useState(false);
  const [searchResults, setSearchResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [totalItems, setTotalItems] = useState(0);
  const [searchSmiles, setSearchSmiles] = useState("");
  const itemsPerPage = 10;
  const [searchType, setSearchType] = useState<ChemSearchType>("SUBSTRUCTURE");
  const [paginationModel, setPaginationModel] = useState({
    page: 0,
    pageSize: itemsPerPage,
  });

  React.useEffect(() => {
    setShowKetcherDialog(isOpen);
  }, [isOpen]);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const fetchData = (page: any, smiles: any) => {
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
        setErrorMessage(t("toolbar.chemicalSearch.errorFetching", { message: (error as Error).message }));
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

  const { validationResult: isValid, validate } = useSingleMoleculeValidation();

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleInsert = (ketcher: any) => {
    setShowKetcherDialog(false);
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    ketcher.getSmiles().then((smiles: any) => {
      setSearchSmiles(smiles);
      fetchData(0, smiles);
      void window.ketcher.setMolecule("");
    });
  };

  const columns = [
    DataGridColumn.newColumnWithValueGetter(
      "preview",
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      ({ breadcrumb, chemId }: any) =>
        breadcrumb.startsWith("/Gallery") ? "/images/icons/chemistry-file.png" : `/chemical/getImageChem/${chemId}/1`,
      {
        headerName: t("toolbar.chemicalSearch.columnHeaders.preview"),
        renderCell: (params) => (
          <Box
            component="img"
            src={params.value}
            alt={t("toolbar.chemicalSearch.chemicalPreviewAlt")}
            sx={{ width: "150px", height: "150px", marginTop: "8px" }}
          />
        ),
        sortable: false,
        flex: 1,
      },
    ),
    DataGridColumn.newColumnWithValueGetter(
      "name",
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      ({ breadcrumb, globalId, recordName }: any) => {
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
        headerName: t("toolbar.chemicalSearch.columnHeaders.name"),
        renderCell: (params) => (
          <Link href={params.value.href} target="_blank" rel="noreferrer">
            {params.value.text}
          </Link>
        ),
        sortable: false,
        flex: 1,
      },
    ),
    DataGridColumn.newColumnWithFieldName("owner", {
      headerName: t("toolbar.chemicalSearch.columnHeaders.owner"),
      sortable: false,
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName("lastModified", {
      headerName: t("toolbar.chemicalSearch.columnHeaders.lastModified"),
      sortable: true,
      flex: 1,
    }),
  ];

  return (
    <div>
      {showKetcherDialog && (
        <React.Suspense
          fallback={
            <Backdrop
              open
              sx={{
                color: "#fff",
                zIndex: 3, // more than the table pagination
              }}
            >
              <CircularProgress color="inherit" />
            </Backdrop>
          }
        >
          <KetcherDialog
            isOpen
            title={t("toolbar.chemicalSearch.ketcherTitle")}
            handleInsert={handleInsert}
            actionBtnText={t("common:actions.search")}
            handleClose={closeAndReset}
            existingChem={searchSmiles}
            validationResult={isValid}
            instructionText={t("common:ketcher.structureSearch.drawInstruction")}
            onChange={() => {
              validate(window.ketcher);
            }}
            additionalControls={<StructureSearchTypeControl value={searchType} onChange={setSearchType} />}
          />
        </React.Suspense>
      )}
      <Dialog
        open={showSearchResults || loading || typeof errorMessage === "string"}
        fullWidth
        maxWidth="xl"
        slotProps={{
          paper: { style: { maxHeight: "90vh", minHeight: "90vh" } },
        }}
      >
        <DialogTitle>{t("toolbar.chemicalSearch.dialogTitle")}</DialogTitle>
        <DialogContent>
          <div className="search-results-dialog">
            {loading || typeof errorMessage === "string" ? (
              <Box sx={{ textAlign: "center", width: "100%" }}>
                <Typography>{errorMessage ?? t("toolbar.chemicalSearch.loading")}</Typography>
              </Box>
            ) : (
              <DataGrid
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                columns={columns as any}
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
                  noRowsLabel: t("toolbar.chemicalSearch.noResults"),
                }}
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                getRowId={(row: any) => row.chemId}
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
          <Button onClick={goBackToSearch}>{t("toolbar.chemicalSearch.back")}</Button>
          <Button onClick={closeAndReset}>{t("common:actions.close")}</Button>
        </DialogActions>
      </Dialog>
    </div>
  );
};

export default ChemicalSearcher;
