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
import Collapse from "@mui/material/Collapse";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButton from "@mui/material/IconButton";

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
  const [searchOpen, setSearchOpen] = React.useState(
    sample.subSamples.length > 1
  );

  React.useEffect(() => {
    setSearchOpen(sample.subSamples.length > 1);
  }, [sample.subSamples]);

  React.useEffect(() => {
    if (!sample.search.activeResult && sample.search.filteredResults.length > 0)
      void sample.search.setActiveResult();
  }, [sample.search.filteredResults]);

  const handleSearch = (query: string) => {
    void sample.search.fetcher.performInitialSearch({
      query,
      parentGlobalId: sample.globalId,
    });
  };

  return (
    <Grid container direction="row" flexWrap="nowrap" spacing={1}>
      <Grid item sx={{ pl: 0, ml: -2 }}>
        <IconButton onClick={() => setSearchOpen(!searchOpen)} sx={{ p: 1.25 }}>
          <ExpandCollapseIcon open={searchOpen} />
        </IconButton>
      </Grid>
      <Grid item>
        <Collapse
          in={searchOpen}
          collapsedSize={44}
          onClick={() => {
            setSearchOpen(true);
          }}
        >
          <SearchContext.Provider
            value={{
              search: sample.search,
              scopedResult: sample,
              isChild: false,
              differentSearchForSettingActiveResult: sample.search,
            }}
          >
            <InnerSearchNavigationContext>
              <Grid container direction="column" spacing={1}>
                <Grid item>
                  <Search
                    handleSearch={handleSearch}
                    TABS={TABS}
                    size="small"
                  />
                </Grid>
                <Grid item>
                  <SearchView contextMenuId={menuIDs.RESULTS} />
                </Grid>
              </Grid>
            </InnerSearchNavigationContext>
          </SearchContext.Provider>
        </Collapse>
      </Grid>
    </Grid>
  );
}

export default (observer(
  SubsampleListing
): ComponentType<SubsampleListingArgs>);
