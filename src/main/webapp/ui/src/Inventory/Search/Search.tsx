import { observer } from "mobx-react-lite";
import React from "react";
import Searchbar from "./components/Searchbar";
import SearchFeedback from "./components/SearchFeedback";
import { type SearchView } from "../../stores/definitions/Search";
import SearchDisplayControls from "./components/SearchDisplayControls";
import SearchParameterControls from "./components/SearchParameterControls";
import Grid from "@mui/material/Grid";
import Divider from "@mui/material/Divider";
import SearchParameterChips from "./components/SearchParameterChips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import docLinks from "../../assets/DocLinks";

type MainSearchArgs = {
  size?: "small" | "default";
  TABS?: Array<SearchView>;
  handleSearch: (query: string) => void;
  searchbarAdornment?: React.ReactNode;
};

function MainSearch({
  size = "default",
  handleSearch,
  TABS = ["LIST", "TREE", "CARD"],
  searchbarAdornment,
}: MainSearchArgs): React.ReactNode {
  return (
    <Grid container sx={{ flexDirection: "column" }} spacing={1}>
      <Grid>
        <Grid
          container
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", flexWrap: "nowrap" }}
        >
          <Grid sx={{ flexGrow: 1 }}>
            <Searchbar handleSearch={handleSearch} />
          </Grid>
          <Grid>
            <HelpLinkIcon
              link={docLinks.search}
              title="Info on searching Inventory."
            />
          </Grid>
          {size === "small" && (
            <Grid>
              <SearchDisplayControls TABS={TABS} />
            </Grid>
          )}
          {Boolean(searchbarAdornment) && <Grid>{searchbarAdornment}</Grid>}
        </Grid>
      </Grid>
      <Grid sx={{ pt: 0 }}>
        <SearchParameterControls />
      </Grid>
      <Grid sx={{ maxWidth: "100% !important" }}>
        <SearchParameterChips />
      </Grid>
      {size === "default" && (
        <>
          <Grid>
            <Divider orientation="horizontal" sx={{ my: 0.75 }} />
          </Grid>
          <Grid>
            <Grid container direction="row" spacing={1} sx={{ flexWrap: "nowrap" }}>
              <Grid sx={{ flexGrow: 1 }}>
                <SearchFeedback />
              </Grid>
              <Grid>
                <SearchDisplayControls TABS={TABS} />
              </Grid>
            </Grid>
          </Grid>
        </>
      )}
    </Grid>
  );
}

export default observer(MainSearch);
