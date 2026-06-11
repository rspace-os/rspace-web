import React from "react";
import { Routes, Route } from "react-router-dom";
import useStores from "../../stores/use-stores";
import { observer } from "mobx-react-lite";
import {
  RightPanelToggle,
  useIsSingleColumnLayout,
} from "../components/Layout/Layout2x1";
import Stack from "@mui/material/Stack";
import Box from "@mui/material/Box";
import Breadcrumbs from "../components/Breadcrumbs";
import SearchView from "./SearchView";
import { type CoreFetcherArgs } from "../../stores/definitions/Search";
import { menuIDs } from "../../util/menuIDs";
import SubSampleModel from "../../stores/models/SubSampleModel";
import {
  globalIdPatterns,
  getSavedGlobalId,
} from "../../stores/definitions/BaseRecord";
import Search from "./Search";
import NavigateContext from "../../stores/contexts/Navigate";
import { hasLocation } from "../../stores/models/HasLocation";
import * as Parsers from "../../util/parsers";
import { useLandmark } from "../../components/LandmarksContext";

function LeftPanelView(): React.ReactNode {
  const { searchStore, uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const searchNavRef = useLandmark("Search");

  const results = searchStore.search.filteredResults.map(getSavedGlobalId);

  const [isActiveIncluded, setIsActiveIncluded] = React.useState<
    boolean | undefined
  >();
  React.useEffect(() => {
    const active = searchStore.search.activeResult?.globalId;
    const activeIncluded = (
      results as Array<string | null | undefined>
    ).includes(active);
    setIsActiveIncluded(activeIncluded);
  }, [searchStore.search.filteredResults, searchStore.search.activeResult]);

  const [isParentContainerIncluded, setIsParentContainerIncluded] =
    React.useState<boolean | undefined>();
  React.useEffect(() => {
    setIsParentContainerIncluded(
      Parsers.isNotNull(searchStore.search.activeResult)
        .toOptional()
        .flatMap(hasLocation)
        .map((recordWithLocation) =>
          recordWithLocation.allParentContainers
            .map(({ globalId }) => globalId)
            .some((g) => (results as Array<string | null>).includes(g)),
        )
        .orElse(false),
    );
  }, [searchStore.search.filteredResults, searchStore.search.activeResult]);

  const [inContainerSearch, setInContainerSearch] = React.useState<
    boolean | undefined
  >(false);
  React.useEffect(() => {
    const inContainer =
      typeof searchStore.search.fetcher.parentGlobalId === "string"
        ? globalIdPatterns.container.test(
            searchStore.search.fetcher.parentGlobalId,
          )
        : false;
    setInContainerSearch(inContainer);
  }, [
    searchStore.search.searchView,
    searchStore.search.fetcher.parentGlobalId,
  ]);

  const [isParentSampleIncluded, setIsParentSampleIncluded] = React.useState<
    boolean | undefined
  >();
  React.useEffect(() => {
    if (!(searchStore.activeResult instanceof SubSampleModel)) return;
    const parentSampleGlobalId = searchStore.activeResult.sample.globalId;
    const subSampleInSampleTree =
      searchStore.activeResult instanceof SubSampleModel &&
      (results as Array<string | null>).includes(parentSampleGlobalId);
    setIsParentSampleIncluded(subSampleInSampleTree);
  }, [searchStore.search.filteredResults, searchStore.search.activeResult]);

  const inContainerWithResults =
    inContainerSearch && searchStore.search.filteredResults.length > 0;

  const showLeftBreadcrumbs = isSingleColumnLayout
    ? isActiveIncluded ||
      inContainerWithResults ||
      (searchStore.search.searchView === "TREE" &&
        (isParentContainerIncluded || isParentSampleIncluded))
    : searchStore.search.searchView === "TREE" && inContainerWithResults;

  // get a fallback for showing breadcrumbs when activeResult is not in container we navigated to
  const recordForBreadcrumbs =
    inContainerSearch && !isActiveIncluded
      ? searchStore.search.filteredResults.length > 0
        ? searchStore.search.filteredResults[0]
        : null
      : searchStore.activeResult;

  // processes a change to the search query string only
  const handleSearch = (qry: string) => {
    if ([null, ""].includes(searchStore.search.fetcher.query) && !qry) return;
    let params: CoreFetcherArgs = { query: qry };

    // If entering query, show all results regardless of type or owner by default
    if ([null, ""].includes(searchStore.search.fetcher.query) && qry) {
      params = { ...params, resultType: "ALL", ownedBy: null };
    }

    navigate(
      `/inventory/search?${searchStore.search.fetcher
        .generateQuery(params)
        .toString()}`,
    );
  };

  return (
    <Stack
      ref={searchNavRef as React.RefObject<HTMLDivElement>}
      sx={{
        flexWrap: "nowrap",
        height: "100%",
        p: uiStore.alwaysVisibleSidebar ? 1 : 0,
      }}
      spacing={1}
      data-testid="MainSearch"
      role="navigation"
      aria-label="Search and Navigation"
    >
      <Box sx={{ width: "100%" }}>
        <Search
          handleSearch={handleSearch}
          searchbarAdornment={<RightPanelToggle />}
        />
        {showLeftBreadcrumbs && recordForBreadcrumbs && (
          <Breadcrumbs record={recordForBreadcrumbs} showCurrent={false} />
        )}
      </Box>
      <Box sx={{ overflow: "hidden", display: "flex", flexDirection: "column", flexGrow: 1 }}>
        <Routes>
          <Route
            path="/"
            element={<SearchView contextMenuId={menuIDs.RESULTS} />}
          />
        </Routes>
      </Box>
    </Stack>
  );
}

export default observer(LeftPanelView);
