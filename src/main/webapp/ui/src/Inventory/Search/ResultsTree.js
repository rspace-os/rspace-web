// @flow

import SearchContext from "../../stores/contexts/Search";
import { DEFAULT_SEARCH } from "../../stores/models/Fetcher/CoreFetcher";
import useStores from "../../stores/use-stores";
import LoadingList from "../components/FetchOnScroll/LoadingList";
import LoadingNode from "../components/RecordTree/LoadingNode";
import RecordTree from "../components/RecordTree/RecordTree";
import ScrollBox from "./ScrollBox";
import { observer } from "mobx-react-lite";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import React, { type Node, type ComponentType, useContext } from "react";

function ResultsTree(): Node {
  const { search } = useContext(SearchContext);
  const { uiStore } = useStores();

  return (
    <ScrollBox
      overflowY={uiStore.isSingleColumnLayout ? "unset" : "auto"}
      overflowX="hidden"
    >
      <RecordTree />
      {search.filteredResults.length < search.count && (
        <SimpleTreeView>
          <LoadingList
            onVisible={() => search.dynamicFetcher.dynamicSearch()}
            count={search.dynamicFetcher.nextDynamicPageSize}
            pageNumber={search.dynamicFetcher.pageNumber}
            placeholder={<LoadingNode />}
            loading={search.dynamicFetcher.loading}
          />
        </SimpleTreeView>
      )}
      {search.dynamicFetcher.loading && (
        <SimpleTreeView>
          <LoadingList
            onVisible={() => {}}
            count={DEFAULT_SEARCH.pageSize}
            pageNumber={0}
            placeholder={<LoadingNode />}
            loading
          />
        </SimpleTreeView>
      )}
    </ScrollBox>
  );
}

export default (observer(ResultsTree): ComponentType<{||}>);
