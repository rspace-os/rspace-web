import React, { useContext } from "react";
import styled from "@emotion/styled";
import IconButton from "@mui/material/IconButton";
import CustomTooltip from "../../../components/CustomTooltip";
import TextField from "@mui/material/TextField";
import InputAdornment from "@mui/material/InputAdornment";
import Paper from "@mui/material/Paper";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import { observer } from "mobx-react-lite";
import SearchContext from "../../../stores/contexts/Search";
import CloseIcon from "@mui/icons-material/Close";
import useIsTextWiderThanField from "../../../hooks/ui/useIsTextWiderThanField";
import SearchDialog from "../../../components/SearchDialog";
import { runInAction } from "mobx";

const SearchBar = styled.div`
  form {
    display: flex;
    align-items: center;
    width: 100%;
  }
  .MuiTextField-root {
    flex-grow: 1;
    .MuiOutlinedInput-root {
      input:focus,
      input:hover {
        background-color: transparent !important;
      }
    }
    .MuiOutlinedInput-input {
      padding: 8px 0 8px 0;
    }
  }
  .grow {
    flex-grow: 1;
  }
`;

type FormArgs = {
  handleSearch: (query: string) => void;
};

const Form = observer(({ handleSearch }: FormArgs) => {
  const { search } = useContext(SearchContext);

  const handleChange = ({
    target: { value },
  }: {
    target: { value: string };
  }) => {
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

  const { inputRef, textTooWide } = useIsTextWiderThanField();

  return (
    <>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSearch();
        }}
        style={{ width: "100%" }}
      >
        <TextField
          data-test-id="s-search-input-normal"
          placeholder="Search"
          value={search.fetcher.query ?? ""}
          onChange={handleChange}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <IconButton
                  aria-label="Search"
                  data-test-id="s-search-submit"
                  onClick={onSearch}
                  size="small"
                  edge="start"
                >
                  <SearchOutlinedIcon />
                </IconButton>
              </InputAdornment>
            ),
            ...(search.fetcher.query
              ? {
                  endAdornment: (
                    <InputAdornment position="end">
                      <ClearSearch handleReset={handleReset} />
                    </InputAdornment>
                  ),
                }
              : {}),
          }}
          inputProps={{
            "aria-label": "Search",
            type: "search",
            ref: inputRef,
          }}
          sx={{ flexGrow: 1 }}
        />
        <SearchDialog
          visible={textTooWide.orElse(false)}
          onSubmit={onSearch}
          query={search.fetcher.query ?? ""}
          setQuery={handleChange}
        />
      </form>
    </>
  );
});

type ClearSearchArgs = {
  handleReset: () => void;
};

const ClearSearch = ({ handleReset }: ClearSearchArgs) => (
  <CustomTooltip title="Clear search">
    <IconButton
      size="small"
      data-test-id="reset-search"
      aria-label="close"
      color="inherit"
      onClick={handleReset}
    >
      <CloseIcon fontSize="small" />
    </IconButton>
  </CustomTooltip>
);

type SearchbarArgs = {
  handleSearch: (query: string) => void;
};

function Searchbar({ handleSearch }: SearchbarArgs): React.ReactNode {
  return (
    <div style={{ flexGrow: 1 }}>
      <SearchBar>
        <Paper
          style={{
            display: "flex",
            alignItems: "center",
          }}
          elevation={0}
        >
          <Form handleSearch={handleSearch} />
        </Paper>
      </SearchBar>
    </div>
  );
}

export default observer(Searchbar);
