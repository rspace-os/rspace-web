// @flow

import styled from "@emotion/styled";
import IconButton from "@mui/material/IconButton";
import CustomTooltip from "../../../components/CustomTooltip";
import InputBase from "@mui/material/InputBase";
import Paper from "@mui/material/Paper";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import { observer } from "mobx-react-lite";
import React, { type Node, type ComponentType, useContext } from "react";
import SearchContext from "../../../stores/contexts/Search";
import CloseIcon from "@mui/icons-material/Close";
import useIsTextWiderThanField from "../../../util/useIsTextWiderThanField";
import SearchDialog from "../../../components/SearchDialog";
import { runInAction } from "mobx";

const SearchBar = styled.div`
  form {
    padding: 2px 2px;
    display: flex;
    align-items: center;
  }
  .MuiPaper-root {
    background-color: rgba(0, 0, 0, 0.08) !important;
  }
  .MuiInputBase-root {
    flex-grow: 1;
    input:focus,
    input:hover {
      background-color: transparent !important;
    }
  }
  .MuiInputBase-input {
    padding: 3px;
  }
  .MuiDivider-root {
    width: 1px;
    height: 35px;
    margin: 4px;
  }
  .MuiTextField-root {
    .MuiInputLabel-formControl {
      transform: translate(0, 16px) scale(1);
    }
    .MuiInputLabel-formControl.Mui-focused,
    .MuiInputLabel-formControl.MuiFormLabel-filled {
      transform: translate(5px, 0) scale(0.75);
      transform-origin: top left;
    }
    label + .MuiInput-formControl {
      margin-top: 5px;
    }
    .MuiInput-underline:before,
    .MuiInput-underline:after {
      border-bottom: 0px;
    }
  }
  button.MuiIconButton-root {
    width: 40px;
    height: 40px;
  }
  .grow {
    flex-grow: 1;
  }
`;

type FormArgs = {|
  handleSearch: (string) => void,
|};

const Form = observer(({ handleSearch }: FormArgs) => {
  const { search } = useContext(SearchContext);

  const handleChange = ({
    target: { value },
  }: {
    target: { value: string, ... },
    ...
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
        type="search"
        style={{ width: "100%" }}
      >
        <IconButton
          aria-label="Search"
          data-test-id="s-search-submit"
          onClick={onSearch}
        >
          <SearchOutlinedIcon />
        </IconButton>
        <InputBase
          data-test-id="s-search-input-normal"
          placeholder="Search"
          inputProps={{ "aria-label": "Search" }}
          value={search.fetcher.query ?? ""}
          onChange={handleChange}
          inputRef={inputRef}
        />
        <SearchDialog
          visible={textTooWide.orElse(false)}
          onSubmit={onSearch}
          query={search.fetcher.query ?? ""}
          setQuery={handleChange}
        />
      </form>
      {search.fetcher.query && <ClearSearch handleReset={handleReset} />}
    </>
  );
});

type ClearSearchArgs = {|
  handleReset: () => void,
|};

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

type SearchbarArgs = {|
  handleSearch: (string) => void,
|};

function Searchbar({ handleSearch }: SearchbarArgs): Node {
  return (
    <div style={{ flexGrow: 1 }}>
      <SearchBar>
        <Paper style={{ display: "flex", alignItems: "center" }} elevation={0}>
          <Form handleSearch={handleSearch} />
        </Paper>
      </SearchBar>
    </div>
  );
}

export default (observer(Searchbar): ComponentType<SearchbarArgs>);
