import CloseIcon from "@mui/icons-material/Close";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import Paper from "@mui/material/Paper";
import Popover from "@mui/material/Popover";
import TextField, { textFieldClasses } from "@mui/material/TextField";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import SearchBarcodeIcon from "../../../assets/graphics/SearchBarcode";
import CustomTooltip from "../../../components/CustomTooltip";
import SearchDialog from "../../../components/SearchDialog";
import useIsTextWiderThanField from "../../../hooks/ui/useIsTextWiderThanField";
import SearchContext from "../../../stores/contexts/Search";
import { isInventoryPermalink, visitUrl } from "../../../util/Util";
import BarcodeScanner from "../../components/BarcodeScanner/BarcodeScanner";
import type { BarcodeInput } from "../../components/BarcodeScanner/BarcodeScannerSkeleton";

type FormArgs = {
  handleSearch: (query: string) => void;
};

const Form = observer(({ handleSearch }: FormArgs) => {
  const { t } = useTranslation("inventory");
  const { search } = useContext(SearchContext);

  const handleChange = ({ target: { value } }: { target: { value: string } }) => {
    runInAction(() => {
      search.fetcher.query = value;
    });
  };

  const onSearch = () => {
    handleSearch(search.fetcher.query ?? "");
  };

  const handleReset = () => {
    handleSearch("");
    runInAction(() => {
      search.fetcher.query = "";
    });
  };

  const [scannerAnchorEl, setScannerAnchorEl] = useState<HTMLElement | null>(null);

  const handleScan = (barcode: BarcodeInput) => {
    if (isInventoryPermalink(barcode.rawValue)) {
      visitUrl(barcode.rawValue);
    } else {
      runInAction(() => {
        search.fetcher.query = barcode.rawValue;
      });
      handleSearch(barcode.rawValue);
    }
  };

  const { inputRef, textTooWide } = useIsTextWiderThanField();

  return (
    <Box
      component="form"
      onSubmit={(e) => {
        e.preventDefault();
        onSearch();
      }}
      sx={{ width: "100%" }}
    >
      <TextField
        data-test-id="s-search-input-normal"
        placeholder={
          search.showBarcodeScan
            ? t("search.controls.searchbar.scanPlaceholder")
            : t("search.controls.searchbar.search")
        }
        value={search.fetcher.query ?? ""}
        onChange={handleChange}
        sx={{ flexGrow: 1 }}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <IconButton
                  aria-label={t("search.controls.searchbar.search")}
                  data-test-id="s-search-submit"
                  onClick={onSearch}
                  size="small"
                  edge="start"
                >
                  <SearchOutlinedIcon />
                </IconButton>
              </InputAdornment>
            ),
            ...(search.showBarcodeScan || search.fetcher.query
              ? {
                  endAdornment: (
                    <InputAdornment position="end">
                      {Boolean(search.fetcher.query) && (
                        <CustomTooltip title={t("search.controls.searchbar.clearSearch")}>
                          <IconButton
                            size="small"
                            data-test-id="reset-search"
                            aria-label={t("search.controls.searchbar.clearSearch")}
                            color="inherit"
                            onClick={handleReset}
                          >
                            <CloseIcon fontSize="small" />
                          </IconButton>
                        </CustomTooltip>
                      )}
                      {Boolean(search.fetcher.query) && search.showBarcodeScan && (
                        /*
                         * An explicit height rather than flexItem: InputAdornment's
                         * flex container is 0.01em tall, so a stretched divider
                         * renders invisibly.
                         */
                        <Divider orientation="vertical" sx={{ height: 20, ml: 1.5, mr: 0.5 }} />
                      )}
                      {search.showBarcodeScan && (
                        <CustomTooltip title={t("search.controls.searchbar.scanBarcode")}>
                          <IconButton
                            size="small"
                            data-test-id="s-search-scan"
                            aria-label={t("search.controls.searchbar.scanBarcode")}
                            color="inherit"
                            onClick={({ currentTarget }) => setScannerAnchorEl(currentTarget)}
                          >
                            <SearchBarcodeIcon fontSize="small" />
                          </IconButton>
                        </CustomTooltip>
                      )}
                    </InputAdornment>
                  ),
                }
              : {}),
          },

          htmlInput: {
            "aria-label": search.showBarcodeScan
              ? t("search.controls.searchbar.scanPlaceholder")
              : t("search.controls.searchbar.search"),
            type: "search",
            ref: inputRef,
          },
        }}
      />
      <SearchDialog
        visible={textTooWide.orElse(false)}
        onSubmit={onSearch}
        query={search.fetcher.query ?? ""}
        setQuery={handleChange}
      />
      <Popover
        open={Boolean(scannerAnchorEl)}
        anchorEl={scannerAnchorEl}
        onClose={() => setScannerAnchorEl(null)}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "center",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "center",
        }}
        slotProps={{
          paper: {
            variant: "outlined",
            elevation: 0,
            style: {
              minWidth: 300,
            },
          },
        }}
      >
        <BarcodeScanner
          onClose={() => setScannerAnchorEl(null)}
          onScan={handleScan}
          buttonPrefix={t("search.controls.searchbar.scanConfirm")}
        />
      </Popover>
    </Box>
  );
});

type SearchbarArgs = {
  handleSearch: (query: string) => void;
};

function Searchbar({ handleSearch }: SearchbarArgs): React.ReactNode {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Box
        sx={{
          "& form": {
            display: "flex",
            alignItems: "center",
            width: "100%",
          },
          [`& .${textFieldClasses.root}`]: {
            flexGrow: 1,
            [`& .${outlinedInputClasses.root}`]: {
              "& input:focus, & input:hover": {
                backgroundColor: "transparent !important",
              },
            },
            [`& .${outlinedInputClasses.input}`]: {
              padding: "8px 0 8px 0",
            },
          },
        }}
      >
        <Paper
          sx={{
            display: "flex",
            alignItems: "center",
          }}
          elevation={0}
        >
          <Form handleSearch={handleSearch} />
        </Paper>
      </Box>
    </Box>
  );
}

export default observer(Searchbar);
