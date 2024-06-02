// @flow

import React, { useContext, type Node, type ComponentType } from "react";
import ResultsTable from "./ResultsTable";
import ResultsTree from "./ResultsTree";
import ResultsCards from "./ResultsCards";
import GridView from "../Container/Content/GridView/ContentGrid";
import ImageView from "../Container/Content/ImageView/PreviewImage";
import SearchContext from "../../stores/contexts/Search";
import { observer } from "mobx-react-lite";
import { menuIDs } from "../../util/menuIDs";
import NoResults from "./components/NoResults";
import EmptyListing from "./components/EmptyListing";

type SearchViewArgs = {|
  contextMenuId: $Values<typeof menuIDs>,
|};

function SearchView({ contextMenuId }: SearchViewArgs): Node {
  const { search } = useContext(SearchContext);

  if (search.searchView === "IMAGE") return <ImageView />;
  if (search.searchView === "GRID") return <GridView />;
  if (
    search.filteredResults.length === 0 &&
    !search.fetcher.loading &&
    search.fetcher.parentGlobalId
  ) {
    return <EmptyListing parentGlobalId={search.fetcher.parentGlobalId} />;
  }
  if (search.filteredResults.length === 0 && !search.fetcher.loading)
    return <NoResults query={search.fetcher.query} />;
  if (search.searchView === "LIST")
    return <ResultsTable contextMenuId={contextMenuId} />;
  if (search.searchView === "TREE") return <ResultsTree />;
  if (search.searchView === "CARD") return <ResultsCards />;
  return null;
}

export default (observer(SearchView): ComponentType<SearchViewArgs>);
