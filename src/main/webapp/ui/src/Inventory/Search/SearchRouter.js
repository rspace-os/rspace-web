// @flow

import React, {
  useState,
  useEffect,
  type Node,
  type ComponentType,
  useContext,
} from "react";
import { Routes, Route } from "react-router";
import useStores from "../../stores/use-stores";
import { observer } from "mobx-react-lite";
import Layout from "../components/Layout/Layout2x1";
import { makeStyles } from "tss-react/mui";
import RightPanelView from "./RightPanelView";
import Grid from "@mui/material/Grid";
import Breadcrumbs from "../components/Breadcrumbs";
import SearchView from "./SearchView";
import SearchContext from "../../stores/contexts/Search";
import { parseCoreFetcherArgsFromUrl } from "../../stores/models/Fetcher/CoreFetcher";
import { type CoreFetcherArgs } from "../../stores/definitions/Search";
import { menuIDs } from "../../util/menuIDs";
import SubSampleModel from "../../stores/models/SubSampleModel";
import {
  globalIdPatterns,
  getSavedGlobalId,
} from "../../stores/definitions/BaseRecord";
import Search from "./Search";
import Header from "../components/Layout/Header";
import Sidebar from "../components/Layout/Sidebar";
import Main from "../Main";
import NavigateContext, {
  type UseLocation,
} from "../../stores/contexts/Navigate";
import { mkAlert } from "../../stores/contexts/Alert";
import { UserCancelledAction } from "../../util/error";
import ExpandCollapseIcon from "../../components/ExpandCollapseIcon";
import IconButtonWithTooltip from "../../components/IconButtonWithTooltip";
import MainSearchNavigationContext from "./MainSearchNavigationContext";

const useStyles = makeStyles()((theme, { alwaysVisibleSidebar }) => ({
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
  rightPanelHideToggle: {
    border: "2px solid",
  },
}));

type SearchRouterArgs = {|
  paramsOverride?: CoreFetcherArgs,
|};

const SearchRouter = observer(({ paramsOverride }: SearchRouterArgs) => {
  const { searchStore, uiStore } = useStores();
  const { search, fetcher } = searchStore;

  const { classes } = useStyles({
    alwaysVisibleSidebar: uiStore.alwaysVisibleSidebar,
  });

  const { useNavigate, useLocation } = useContext(NavigateContext);
  const navigate = useNavigate();
  const location: UseLocation = useLocation();

  useEffect(() => {
    void (async () => {
      const params =
        paramsOverride ??
        parseCoreFetcherArgsFromUrl(new URLSearchParams(location.search));

      try {
        await search.setupAndPerformInitialSearch(params);
      } catch (error) {
        console.error(error);
        uiStore.addAlert(
          mkAlert({
            title: "Search failed.",
            message:
              error.response?.data.message ??
              error.message ??
              "Unknown reason.",
            variant: "error",
            isInfinite: true,
          })
        );
      }

      if (
        (!searchStore.activeResult &&
          Boolean(search.filteredResults.length) &&
          !uiStore.isSingleColumnLayout) ||
        paramsOverride?.permalink
      ) {
        try {
          await search.setActiveResult();
          uiStore.setVisiblePanel("right");
        } catch (e) {
          if (e instanceof UserCancelledAction) return;
          throw e;
        }
      }
    })();
  }, [paramsOverride]);

  // processes a change to the search query string only
  const handleSearch = (qry: string) => {
    if ([null, ""].includes(fetcher.query) && !qry) return;
    let params: CoreFetcherArgs = { query: qry };

    // If entering query, show all results regardless of type or owner by default
    if ([null, ""].includes(fetcher.query) && qry) {
      params = { ...params, resultType: "ALL", ownedBy: null };
    }

    navigate(`/inventory/search?${fetcher.generateQuery(params).toString()}`);
  };

  useEffect(() => {
    search.overrideSearchOnFilter = (args: CoreFetcherArgs) => {
      // when the main search's parameters change, the URL should be updated
      navigate(
        `/inventory/search?${search.fetcher.generateQuery(args).toString()}`
      );
    };
  }, [search]);

  const [isActiveIncluded, setIsActiveIncluded] = useState<?boolean>();
  const [isParentContainerIncluded, setIsParentContainerIncluded] =
    useState<?boolean>();
  const [isParentSampleIncluded, setIsParentSampleIncluded] =
    useState<?boolean>();
  const [inContainerSearch, setInContainerSearch] = useState<?boolean>(false);

  const view = search.searchView;
  const results = search.filteredResults.map(getSavedGlobalId);

  useEffect(() => {
    const active = search.activeResult?.globalId;
    const activeIncluded = results.includes(active);
    setIsActiveIncluded(activeIncluded);
  }, [search.filteredResults, search.activeResult]);

  useEffect(() => {
    if (search.activeResult?.hasParentContainers()) {
      const parents = search.activeResult
        // $FlowExpectedError[incompatible-use]
        // $FlowExpectedError[prop-missing] if hasParentContainer is true, then allParentContainers exists
        .allParentContainers()
        .map((p) => p.globalId);
      const anyParentIncluded = parents.some((p) => results.includes(p));
      setIsParentContainerIncluded(anyParentIncluded);
    } else {
      setIsParentContainerIncluded(false);
    }
  }, [search.filteredResults, search.activeResult]);

  useEffect(() => {
    if (!(searchStore.activeResult instanceof SubSampleModel)) return;
    const parentSampleGlobalId = searchStore.activeResult.sample.globalId;
    const subSampleInSampleTree =
      searchStore.activeResult instanceof SubSampleModel &&
      results.includes(parentSampleGlobalId);
    setIsParentSampleIncluded(subSampleInSampleTree);
  }, [search.filteredResults, search.activeResult]);

  const parentGlobalId = search.fetcher.parentGlobalId;

  useEffect(() => {
    const inContainer =
      typeof parentGlobalId === "string"
        ? globalIdPatterns.container.test(parentGlobalId)
        : false;
    setInContainerSearch(inContainer);
  }, [view, parentGlobalId]);

  const inContainerWithResults =
    inContainerSearch && search.filteredResults.length > 0;

  const showLeftBreadcrumbs = uiStore.isSingleColumnLayout
    ? isActiveIncluded ||
      inContainerWithResults ||
      (view === "TREE" && (isParentContainerIncluded || isParentSampleIncluded))
    : view === "TREE" && inContainerWithResults;

  // get a fallback for showing breadcrumbs when activeResult is not in container we navigated to
  const recordForBreadcrumbs =
    inContainerSearch && !isActiveIncluded
      ? search.filteredResults.length > 0
        ? search.filteredResults[0]
        : null
      : searchStore.activeResult;

  return (
    <>
      {uiStore.isSingleColumnLayout && <Header />}
      <Sidebar />
      <Main>
        <Layout
          colLeft={
            <Grid
              container
              direction="column"
              wrap="nowrap"
              className={classes.grid}
              spacing={1}
              data-testid="MainSearch"
            >
              <Grid item className={classes.searchbarWrapper}>
                <Search
                  handleSearch={handleSearch}
                  searchbarAdornment={
                    !uiStore.isVerySmall &&
                    !uiStore.isSmall && (
                      <IconButtonWithTooltip
                        onClick={() => {
                          uiStore.setVisiblePanel("left");
                          uiStore.setUserHiddenRightPanel(
                            !uiStore.userHiddenRightPanel
                          );
                        }}
                        sx={{ transform: "rotate(-90deg)" }}
                        size="small"
                        color="primary"
                        icon={
                          <ExpandCollapseIcon
                            open={uiStore.userHiddenRightPanel}
                          />
                        }
                        title={
                          uiStore.userHiddenRightPanel
                            ? "Show right panel"
                            : "Hide right panel"
                        }
                        className={classes.rightPanelHideToggle}
                      />
                    )
                  }
                />
                {showLeftBreadcrumbs && recordForBreadcrumbs && (
                  <Breadcrumbs
                    record={recordForBreadcrumbs}
                    showCurrent={false}
                  />
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
          }
          colRight={<RightPanelView />}
        />
      </Main>
    </>
  );
});

/*
 * React contexts cannot be used inside the component in which they are defined
 * (see https://react.dev/reference/react/useContext#caveats). By defining the
 * Search Context and Navigate Context (via MainSearchNavigationContext) in
 * this wrapper component, SearchRouter may use them.
 */
function SearchRouterWrapper({ paramsOverride }: SearchRouterArgs): Node {
  const { searchStore } = useStores();
  return (
    <SearchContext.Provider
      value={{
        search: searchStore.search,
        differentSearchForSettingActiveResult: searchStore.search,
      }}
    >
      <MainSearchNavigationContext>
        <SearchRouter paramsOverride={paramsOverride} />
      </MainSearchNavigationContext>
    </SearchContext.Provider>
  );
}

export default (observer(SearchRouterWrapper): ComponentType<SearchRouterArgs>);
