import IconButton from "@mui/material/IconButton";
import Box from "@mui/material/Box";
import CustomTooltip from "./CustomTooltip";
import InputBase from "@mui/material/InputBase";
import Paper from "@mui/material/Paper";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import { observer } from "mobx-react-lite";
import React, { useState } from "react";
import CloseIcon from "@mui/icons-material/Close";
import type { SxProps, Theme } from "@mui/material/styles";

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
            <IconButton
              aria-label="Search"
              data-test-id="s-search-submit"
              onClick={onSearch}
            >
              {" "}
              <SearchOutlinedIcon />
            </IconButton>
          </CustomTooltip>
        </Box>
        {query && <ClearSearch handleReset={handleReset} />}
      </>
    );
  }
);

type GenericsearchbarArgs = {
  handleSearch: (newQuery: string) => void;
  sx?: SxProps<Theme>;
  placeholder: string;
  searchToolTip: string;
};

const defaultSx = {
  display: "flex",
  alignItems: "center",
  background: "white",
  border: "1px solid #808080",
} as const;

function Genericsearchbar({
  handleSearch,
  sx = defaultSx,
  placeholder = "Search",
  searchToolTip = "Search",
}: GenericsearchbarArgs): React.ReactNode {
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
          "& .MuiInputBase-root": {
            flexGrow: 1,
            "& input:focus, & input:hover": {
              backgroundColor: "transparent !important",
            },
          },
          "& button.MuiIconButton-root": {
            width: 40,
            height: 40,
          },
        }}
      >
        <Paper sx={sx} elevation={0}>
          <Form
            handleSearch={handleSearch}
            placeholder={placeholder}
            searchToolTip={searchToolTip}
          />
        </Paper>
      </Box>
    </Box>
  );
}

export default observer(Genericsearchbar);
