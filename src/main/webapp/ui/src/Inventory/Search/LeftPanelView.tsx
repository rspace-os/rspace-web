import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { Route, Routes } from "react-router";
import { useLandmark } from "../../components/LandmarksContext";
import NavigateContext from "../../stores/contexts/Navigate";
import { getSavedGlobalId, globalIdDefinitions } from "../../stores/definitions/BaseRecord";
import type { CoreFetcherArgs } from "../../stores/definitions/Search";
import { hasLocation } from "../../stores/models/HasLocation";
import SubSampleModel from "../../stores/models/SubSampleModel";
import useStores from "../../stores/use-stores";
import { menuIDs } from "../../util/menuIDs";
import * as Parsers from "../../util/parsers";
import Breadcrumbs from "../components/Breadcrumbs";
import { RightPanelToggle, useIsSingleColumnLayout } from "../components/Layout/Layout2x1";
import Search from "./Search";
import SearchView from "./SearchView";

function LeftPanelView(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore, uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const searchNavRef = useLandmark("Search");

  const results = searchStore.search.filteredResults.map(getSavedGlobalId);

  const [isActiveIncluded, setIsActiveIncluded] = React.useState<boolean | undefined>();
  React.useEffect(() => {
    const active = searchStore.search.activeResult?.globalId;
    const activeIncluded = (results as Array<string | null | undefined>).includes(active);
    setIsActiveIncluded(activeIncluded);
  }, [searchStore.search.filteredResults, searchStore.search.activeResult]);

  const [isParentContainerIncluded, setIsParentContainerIncluded] = React.useState<boolean | undefined>();
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

  const [inContainerSearch, setInContainerSearch] = React.useState<boolean | undefined>(false);
  React.useEffect(() => {
    const inContainer =
      typeof searchStore.search.fetcher.parentGlobalId === "string"
        ? globalIdDefinitions.container.pattern.test(searchStore.search.fetcher.parentGlobalId)
        : false;
    setInContainerSearch(inContainer);
  }, [searchStore.search.searchView, searchStore.search.fetcher.parentGlobalId]);

  const [isParentSampleIncluded, setIsParentSampleIncluded] = React.useState<boolean | undefined>();
  React.useEffect(() => {
    if (!(searchStore.activeResult instanceof SubSampleModel)) return;
    const parentSampleGlobalId = searchStore.activeResult.sample.globalId;
    const subSampleInSampleTree =
      searchStore.activeResult instanceof SubSampleModel &&
      (results as Array<string | null>).includes(parentSampleGlobalId);
    setIsParentSampleIncluded(subSampleInSampleTree);
  }, [searchStore.search.filteredResults, searchStore.search.activeResult]);

  const inContainerWithResults = inContainerSearch && searchStore.search.filteredResults.length > 0;

  const showLeftBreadcrumbs = isSingleColumnLayout
    ? isActiveIncluded ||
      inContainerWithResults ||
      (searchStore.search.searchView === "TREE" && (isParentContainerIncluded || isParentSampleIncluded))
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

    navigate(`/inventory/search?${searchStore.search.fetcher.generateQuery(params).toString()}`);
  };

  return (
    <Stack
      ref={searchNavRef as React.RefObject<HTMLDivElement | null>}
      sx={{
        flexWrap: "nowrap",
        height: "100%",
        p: uiStore.alwaysVisibleSidebar ? 1 : 0,
      }}
      spacing={1}
      data-testid="MainSearch"
      role="navigation"
      aria-label={t("search.navigation.label")}
    >
      <Box sx={{ width: "100%" }}>
        <Search handleSearch={handleSearch} searchbarAdornment={<RightPanelToggle />} />
        {showLeftBreadcrumbs && recordForBreadcrumbs && (
          <Breadcrumbs record={recordForBreadcrumbs} showCurrent={false} />
        )}
      </Box>
      <Box sx={{ overflow: "hidden", display: "flex", flexDirection: "column", flexGrow: 1 }}>
        <Routes>
          <Route path="/" element={<SearchView contextMenuId={menuIDs.RESULTS} />} />
        </Routes>
      </Box>
    </Stack>
  );
}

export default observer(LeftPanelView);
