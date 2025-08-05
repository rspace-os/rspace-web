import React from "react";
import { Routes, Route } from "react-router";
import useStores from "../../stores/use-stores";
import { observer } from "mobx-react-lite";
import {
  RightPanelToggle,
  useIsSingleColumnLayout,
} from "../components/Layout/Layout2x1";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";
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

const useStyles = makeStyles<{ alwaysVisibleSidebar: boolean }>()(
  (theme, { alwaysVisibleSidebar }) => ({
    grid: {
      height: "100%",
      padding: alwaysVisibleSidebar ? theme.spacing(1) : theme.spacing(0),
    },
    searchbarWrapper: {
      width: "100%",
    },
    listWrapper: {
      overflow: "hidden",
      display: "flex",
      flexDirection: "column",
      flexGrow: 1,
    },
  }),
);

function LeftPanelView(): React.ReactNode {
  const { searchStore, uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const { classes } = useStyles({
    alwaysVisibleSidebar: uiStore.alwaysVisibleSidebar,
  });
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
    <Grid
      ref={searchNavRef as React.RefObject<HTMLDivElement>}
      container
      direction="column"
      wrap="nowrap"
      className={classes.grid}
      spacing={1}
      data-testid="MainSearch"
      role="navigation"
      aria-label="Search and Navigation"
    >
      <Grid item className={classes.searchbarWrapper}>
        <Search
          handleSearch={handleSearch}
          searchbarAdornment={<RightPanelToggle />}
        />
        {showLeftBreadcrumbs && recordForBreadcrumbs && (
          <Breadcrumbs record={recordForBreadcrumbs} showCurrent={false} />
        )}
      </Grid>
      <Grid item className={classes.listWrapper}>
        <Routes>
          <Route
            path="/"
            element={<SearchView contextMenuId={menuIDs.RESULTS} />}
          />
        </Routes>
      </Grid>
    </Grid>
  );
}

export default observer(LeftPanelView);
