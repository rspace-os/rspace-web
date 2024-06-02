//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import SearchContext from "../../../stores/contexts/Search";
import SearchView from "../../Search/SearchView";
import Search from "../../Search/Search";
import { menuIDs } from "../../../util/menuIDs";
import Grid from "@mui/material/Grid";
import SampleModel from "../../../stores/models/SampleModel";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";

const TABS = ["LIST", "TREE", "CARD"];

type SubsampleListingArgs = {|
  sample: SampleModel,
|};

/**
 * This is an inner search for the sample form which provides a mechanism for
 * the user to view the subsamples associated with a particular sample. Many of
 * the standard search and filter capabilities are provided all for the
 * efficient identification of subsamples given that each sample can have up to
 * 100 subsamples.
 */
function SubsampleListing({ sample }: SubsampleListingArgs): Node {
  const { search } = React.useContext(SearchContext);

  const handleSearch = (query: string) => {
    void sample.search.fetcher.performInitialSearch({
      query,
      parentGlobalId: sample.globalId,
    });
  };

  return (
    <SearchContext.Provider
      value={{
        search: sample.search,
        scopedResult: sample,
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

export default (observer(
  SubsampleListing
): ComponentType<SubsampleListingArgs>);
