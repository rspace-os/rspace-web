import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import SearchContext from "../../../stores/contexts/Search";
import type { SearchView } from "../../../stores/definitions/Search";
import SortControls from "./SortControls";
import ToggleView from "./ToggleView";

type SearchDisplayControlsArgs = {
    TABS: Array<SearchView>;
};

function SearchDisplayControls({ TABS }: SearchDisplayControlsArgs): React.ReactNode {
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
