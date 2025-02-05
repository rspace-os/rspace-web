// @flow

import SearchContext from "../../stores/contexts/Search";
import { DYNAMIC_PAGE_SIZE } from "../../stores/models/Fetcher/DynamicFetcher";
import CardList from "../components/DetailedListing/CardList";
import LoadingCard from "../components/DetailedListing/LoadingCard";
import LoadingList from "../components/FetchOnScroll/LoadingList";
import ScrollBox from "./ScrollBox";
import { observer } from "mobx-react-lite";
import React, { type Node, type ComponentType, useContext } from "react";
import { useIsSingleColumnLayout } from "../components/Layout/Layout2x1";

function ResultsCards(): Node {
  const { search } = useContext(SearchContext);
  const isSingleColumnLayout = useIsSingleColumnLayout();

  return (
    <ScrollBox
      overflowY="auto"
      sx={{ pb: 0.5 }}
    >
      <CardList records={search.filteredResults} />
      {search.filteredResults.length < search.count && (
        <LoadingList
          onVisible={() => search.dynamicFetcher.dynamicSearch()}
          count={search.dynamicFetcher.nextDynamicPageSize}
          pageNumber={search.dynamicFetcher.pageNumber}
          placeholder={<LoadingCard />}
          loading={search.dynamicFetcher.loading}
        />
      )}
      {search.dynamicFetcher.loading && (
        <LoadingList
          onVisible={() => {}}
          count={DYNAMIC_PAGE_SIZE}
          pageNumber={0}
          placeholder={<LoadingCard />}
          loading
        />
      )}
    </ScrollBox>
  );
}

export default (observer(ResultsCards): ComponentType<{||}>);
