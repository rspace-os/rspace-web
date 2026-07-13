import CloseIcon from "@mui/icons-material/Close";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import Box from "@mui/material/Box";
import IconButton, { iconButtonClasses } from "@mui/material/IconButton";
import InputBase, { inputBaseClasses } from "@mui/material/InputBase";
import Paper from "@mui/material/Paper";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "./CustomTooltip";

type FormArgs = {
  handleSearch: (newQuery: string) => void;
  placeholder: string;
  searchToolTip: string;
};

const Form = observer(({ handleSearch, placeholder, searchToolTip }: FormArgs) => {
  const { t } = useTranslation("common");
  const [query, setQuery] = useState("");

  const handleChange = ({ target: { value } }: { target: { value: string } }) => {
    setQuery(value);
  };

  const onSearch = () => {
    handleSearch(query);
  };

  const handleReset = () => {
    setQuery("");
    handleSearch("");
  };

  return (
    <>
      <Box
        component="form"
        onSubmit={(e) => {
          e.preventDefault();
          onSearch();
        }}
        sx={{ width: "100%" }}
      >
        <InputBase
          data-test-id="s-search-input-normal"
          placeholder={placeholder}
          slotProps={{
            input: { "aria-label": searchToolTip },
          }}
          value={query ?? ""}
          onChange={handleChange}
        />
        <CustomTooltip title={searchToolTip}>
          <IconButton aria-label={t("actions.search")} data-test-id="s-search-submit" onClick={onSearch}>
            {" "}
            <SearchOutlinedIcon />
          </IconButton>
        </CustomTooltip>
      </Box>
      {query && (
        <CustomTooltip title={t("search.clearTooltip")}>
          <IconButton
            size="small"
            data-test-id="reset-search"
            aria-label={t("actions.close")}
            color="inherit"
            onClick={handleReset}
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        </CustomTooltip>
      )}
    </>
  );
});

type GenericsearchbarArgs = {
  handleSearch: (newQuery: string) => void;
  placeholder?: string;
  searchToolTip?: string;
};

/**
 * @deprecated
 */
function Genericsearchbar({ handleSearch, placeholder, searchToolTip }: GenericsearchbarArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const searchLabel = t("actions.search");
  return (
    <Box sx={{ flexGrow: 1 }}>
      <Box
        sx={{
          "& form": {
            display: "flex",
            alignItems: "center",
            borderRadius: "25px",
            background: "white",
            paddingLeft: 5,
          },
          '& span[role="tooltip"]': {
            borderRadius: "25px",
            background: "white",
          },
          [`& .${inputBaseClasses.root}`]: {
            flexGrow: 1,
            "& input:focus, & input:hover": {
              backgroundColor: "transparent !important",
            },
          },
          [`& button.${iconButtonClasses.root}`]: {
            width: 40,
            height: 40,
          },
        }}
      >
        <Paper
          sx={{
            display: "flex",
            alignItems: "center",
            background: "white",
            border: "1px solid #808080",
          }}
          elevation={0}
        >
          <Form
            handleSearch={handleSearch}
            placeholder={placeholder ?? searchLabel}
            searchToolTip={searchToolTip ?? searchLabel}
          />
        </Paper>
      </Box>
    </Box>
  );
}

export default observer(Genericsearchbar);
