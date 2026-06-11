import React, { useContext, useEffect, useState } from "react";
import { observer } from "mobx-react-lite";
import { type SearchView as SearchViewType } from "../../../stores/definitions/Search";
import SearchContext from "../../../stores/contexts/Search";
import SearchViewComponent from "../../Search/SearchView";
import Search from "../../Search/Search";
import { menuIDs } from "../../../util/menuIDs";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import SampleModel from "../../../stores/models/SampleModel";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";
import Collapse from "@mui/material/Collapse";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButton from "@mui/material/IconButton";

const TABS: SearchViewType[] = ["LIST", "TREE", "CARD"];

type SubsampleListingArgs = {
  sample: SampleModel;
};

/**
 * This is an inner search for the sample form which provides a mechanism for
 * the user to view the subsamples associated with a particular sample. Many of
 * the standard search and filter capabilities are provided all for the
 * efficient identification of subsamples given that each sample can have up to
 * 100 subsamples.
 */
function SubsampleListing({ sample }: SubsampleListingArgs): React.ReactNode {
  const { search: _ } = useContext(SearchContext);
  const [searchOpen, setSearchOpen] = useState(sample.subSamples.length > 1);

  useEffect(() => {
    setSearchOpen(sample.subSamples.length > 1);
  }, [sample.subSamples]);

  useEffect(() => {
    if (!sample.search.activeResult && sample.search.filteredResults.length > 0)
      void sample.search.setActiveResult();
  }, [sample.search.filteredResults, sample.search]);

  const handleSearch = (query: string) => {
    void sample.search.fetcher.performInitialSearch({
      query,
      parentGlobalId: sample.globalId,
    });
  };

  return (
    <Stack direction="row" spacing={1}>
      <Box sx={{ pl: 0, ml: -2 }}>
        <IconButton onClick={() => setSearchOpen(!searchOpen)} sx={{ p: 1 }}>
          <ExpandCollapseIcon open={searchOpen} />
        </IconButton>
      </Box>
      <Box sx={{ flexGrow: 1 }}>
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
              <Stack spacing={1}>
                <Search handleSearch={handleSearch} TABS={TABS} size="small" />
                <SearchViewComponent contextMenuId={menuIDs.RESULTS} />
              </Stack>
            </InnerSearchNavigationContext>
          </SearchContext.Provider>
        </Collapse>
      </Box>
    </Stack>
  );
}

export default observer(SubsampleListing);
