import CloseIcon from "@mui/icons-material/Close";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import Paper from "@mui/material/Paper";
import TextField, { textFieldClasses } from "@mui/material/TextField";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import CustomTooltip from "../../../components/CustomTooltip";
import SearchDialog from "../../../components/SearchDialog";
import useIsTextWiderThanField from "../../../hooks/ui/useIsTextWiderThanField";
import SearchContext from "../../../stores/contexts/Search";

type FormArgs = {
  handleSearch: (query: string) => void;
};

const Form = observer(({ handleSearch }: FormArgs) => {
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

  const { inputRef, textTooWide } = useIsTextWiderThanField();

  return (
    // biome-ignore lint/complexity/noUselessFragments: initial biome migration
    <>
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
          placeholder="Search"
          value={search.fetcher.query ?? ""}
          onChange={handleChange}
          sx={{ flexGrow: 1 }}
          slotProps={{
            input: {
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
                      </InputAdornment>
                    ),
                  }
                : {}),
            },

            htmlInput: {
              "aria-label": "Search",
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
      </Box>
    </>
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
