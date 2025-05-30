import React, { useContext } from "react";
import { observer } from "mobx-react-lite";
import SortControls from "./SortControls";
import ToggleView from "./ToggleView";
import SearchContext from "../../../stores/contexts/Search";
import Box from "@mui/material/Box";
import { type SearchView } from "../../../stores/definitions/Search";
import Grid from "@mui/material/Grid";

type SearchDisplayControlsArgs = {
  TABS: Array<SearchView>;
};

function SearchDisplayControls({
  TABS,
}: SearchDisplayControlsArgs): React.ReactNode {
  const { search } = useContext(SearchContext);

  return (
    <Grid container spacing={1} direction="row" wrap="nowrap">
      <Grid item>
        <Box>
          <ToggleView
            onChange={(viewType) => search.setSearchView(viewType)}
            currentView={search.searchView}
            views={TABS}
          />
        </Box>
      </Grid>
      <Grid item>
        <Box>
          <SortControls />
        </Box>
      </Grid>
    </Grid>
  );
}

export default observer(SearchDisplayControls);
