// @flow

import React, { type Node } from "react";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import OutlinedInput from "@mui/material/OutlinedInput";
import InputAdornment from "@mui/material/InputAdornment";
import IconButton from "@mui/material/IconButton";
import Grid from "@mui/material/Grid";
import SearchIcon from "@mui/icons-material/Search";

type JoveSearchBarArgs = {|
  searchQuery: string,
  handleSearchQueryChange: ({ target: { value: string } }) => void,
  submitSearch: () => void,
|};

export default function JoveSearchBar({
  searchQuery,
  handleSearchQueryChange,
  submitSearch,
}: JoveSearchBarArgs): Node {
  return (
    <>
      <Grid container style={{ gap: 8 }}>
        <FormControl fullWidth variant="outlined">
          <InputLabel htmlFor="jove-search-input">Search</InputLabel>
          <OutlinedInput
            id="jove-search-input"
            value={searchQuery}
            inputProps={{ maxLength: 320 }}
            onChange={handleSearchQueryChange}
            label="Search"
            onKeyDown={(event) => {
              if (event.key === "Enter") submitSearch();
            }}
            endAdornment={
              <InputAdornment position="end">
                <IconButton
                  aria-label="search"
                  onClick={submitSearch}
                  edge="end"
                >
                  <SearchIcon></SearchIcon>
                </IconButton>
              </InputAdornment>
            }
          />
        </FormControl>
      </Grid>
    </>
  );
}
