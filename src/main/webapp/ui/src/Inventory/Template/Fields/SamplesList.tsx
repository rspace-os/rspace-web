import React from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import SearchContext from "../../../stores/contexts/Search";
import SearchView from "../../Search/SearchView";
import Search from "../../Search/Search";
import { menuIDs } from "../../../util/menuIDs";
import Grid from "@mui/material/Grid";
import TemplateModel from "../../../stores/models/TemplateModel";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";
import { type SearchView as SearchViewType } from "../../../stores/definitions/Search";

const TABS: SearchViewType[] = ["LIST", "TREE", "CARD"];

function SamplesList(): React.ReactNode {
  const {
    searchStore: { activeResult, search },
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel))
    throw new Error("ActiveResult must be a Template");

  const samplesSearch = () => activeResult.search;

  const handleSearch = (query: string) => {
    void samplesSearch().fetcher.performInitialSearch({
      query,
      parentGlobalId: activeResult.globalId,
    });
  };

  return (
    <SearchContext.Provider
      value={{
        scopedResult: activeResult,
        search: samplesSearch(),
        isChild: true,
        differentSearchForSettingActiveResult: search,
      }}
    >
      <InnerSearchNavigationContext>
        <Grid container direction="column" spacing={1}>
          <Grid item>
            <Search handleSearch={handleSearch} TABS={TABS} size="small" />
          </Grid>
          <Grid item>
            <SearchView contextMenuId={menuIDs.RESULTS} />
          </Grid>
        </Grid>
      </InnerSearchNavigationContext>
    </SearchContext.Provider>
  );
}

export default observer(SamplesList);
