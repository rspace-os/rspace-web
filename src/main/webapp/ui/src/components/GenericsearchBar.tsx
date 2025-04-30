import styled from "@emotion/styled";
import IconButton from "@mui/material/IconButton";
import CustomTooltip from "./CustomTooltip";
import InputBase from "@mui/material/InputBase";
import Paper from "@mui/material/Paper";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import { observer } from "mobx-react-lite";
import React, { useState } from "react";
import CloseIcon from "@mui/icons-material/Close";

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

type StyleArgs = {
  display: string;
  background: string;
  border: string;
  alignItems: string;
};

const GenericSearchbar = styled.div`
  form {
    display: flex;
    align-items: center;
    border-radius: 25px;
    background: ${(props: { background: string }) =>
      props.background ? props.background : "white"};
    padding-left: 5px;
  }
  span[role="tooltip"] {
    border-radius: 25px;
    background: white;
  }
  .MuiInputBase-root {
    flex-grow: 1;
    input:focus,
    input:hover {
      background-color: transparent !important;
    }
  }
  button.MuiIconButton-root {
    width: 40px;
    height: 40px;
  }
`;

type FormArgs = {
  handleSearch: (newQuery: string) => void;
  placeholder: string;
  searchToolTip: string;
};

const Form = observer(
  ({ handleSearch, placeholder, searchToolTip }: FormArgs) => {
    const [query, setQuery] = useState("");

    const handleChange = ({
      target: { value },
    }: {
      target: { value: string };
    }) => {
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
        <form
          onSubmit={(e) => {
            e.preventDefault();
            onSearch();
          }}
          style={{ width: "100%" }}
        >
          <InputBase
            data-test-id="s-search-input-normal"
            placeholder={placeholder}
            inputProps={{ "aria-label": searchToolTip }}
            value={query ?? ""}
            onChange={handleChange}
          />
          <CustomTooltip title={searchToolTip}>
            <IconButton
              aria-label="Search"
              data-test-id="s-search-submit"
              onClick={onSearch}
            >
              {" "}
              <SearchOutlinedIcon />
            </IconButton>
          </CustomTooltip>
        </form>
        {query && <ClearSearch handleReset={handleReset} />}
      </>
    );
  }
);

type GenericsearchbarArgs = {
  handleSearch: (newQuery: string) => void;
  style: StyleArgs;
  placeholder: string;
  searchToolTip: string;
};

function Genericsearchbar({
  handleSearch,
  style = {
    display: "flex",
    alignItems: "center",
    background: "white",
    border: "1px solid #808080",
  },
  placeholder = "Search",
  searchToolTip = "Search",
}: GenericsearchbarArgs): React.ReactNode {
  return (
    <div style={{ flexGrow: 1 }}>
      <GenericSearchbar background={style.background}>
        <Paper style={style} elevation={0}>
          <Form
            handleSearch={handleSearch}
            placeholder={placeholder}
            searchToolTip={searchToolTip}
          />
        </Paper>
      </GenericSearchbar>
    </div>
  );
}

export default observer(Genericsearchbar);
