import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import SearchContext from "../../stores/contexts/Search";
import type { menuIDs } from "../../util/menuIDs";
import GridView from "../Container/Content/GridView/ContentGrid";
import ImageView from "../Container/Content/ImageView/PreviewImage";
import EmptyListing from "./components/EmptyListing";
import NoResults from "./components/NoResults";
import ResultsCards from "./ResultsCards";
import ResultsTable from "./ResultsTable";
import ResultsTree from "./ResultsTree";

type SearchViewArgs = {
    contextMenuId: (typeof menuIDs)[keyof typeof menuIDs];
};

function SearchView({ contextMenuId }: SearchViewArgs): React.ReactNode {
    const { search } = useContext(SearchContext);

    if (search.searchView === "IMAGE") return <ImageView />;
    if (search.searchView === "GRID") return <GridView />;
    if (search.filteredResults.length === 0 && !search.fetcher.loading && search.fetcher.parentGlobalId) {
        return <EmptyListing parentGlobalId={search.fetcher.parentGlobalId} />;
    }
    if (search.filteredResults.length === 0 && !search.fetcher.loading)
        return <NoResults query={search.fetcher.query} />;
    if (search.searchView === "LIST") return <ResultsTable contextMenuId={contextMenuId} />;
    if (search.searchView === "TREE") return <ResultsTree />;
    if (search.searchView === "CARD") return <ResultsCards />;
    return null;
}

export default observer(SearchView);
