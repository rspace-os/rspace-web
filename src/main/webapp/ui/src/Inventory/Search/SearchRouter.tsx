import React, { useEffect, useContext } from "react";
import useStores from "../../stores/use-stores";
import { observer } from "mobx-react-lite";
import Layout from "../components/Layout/Layout2x1";
import RightPanelView from "./RightPanelView";
import SearchContext from "../../stores/contexts/Search";
import { parseCoreFetcherArgsFromUrl } from "../../stores/models/Fetcher/CoreFetcher";
import { type CoreFetcherArgs } from "../../stores/definitions/Search";
import Header from "../components/Layout/Header";
import Sidebar from "../components/Layout/Sidebar";
import Main from "../Main";
import NavigateContext, {
  type UseLocation,
} from "../../stores/contexts/Navigate";
import { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage, UserCancelledAction } from "../../util/error";
import MainSearchNavigationContext from "./MainSearchNavigationContext";
import { UiPreferences } from "../../hooks/api/useUiPreference";
import LeftPanelView from "./LeftPanelView";
import Box from "@mui/material/Box";
import { globalId } from "@/stores/definitions/BaseRecord";

type SearchRouterArgs = {
  paramsOverride?: CoreFetcherArgs;
};

const SearchRouter = observer(({ paramsOverride }: SearchRouterArgs) => {
  const { searchStore, uiStore } = useStores();
  const { search } = searchStore;

  const { useNavigate, useLocation } = useContext(NavigateContext);
  const navigate = useNavigate();
  const location: UseLocation = useLocation();

  useEffect(() => {
    if (paramsOverride?.permalink) {
      document.title = `${globalId({
        type: paramsOverride.permalink.type,
        id: paramsOverride.permalink.id,
      })} | RSpace Inventory`;
    } else {
      document.title = "RSpace Inventory";
    }
  }, [paramsOverride]);

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
            message: getErrorMessage(error, "Unknown reason."),
            variant: "error",
            isInfinite: true,
          }),
        );
      }

      if (paramsOverride?.permalink) {
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

  useEffect(() => {
    search.overrideSearchOnFilter = (args: CoreFetcherArgs) => {
      // when the main search's parameters change, the URL should be updated
      navigate(
        `/inventory/search?${search.fetcher.generateQuery(args).toString()}`,
      );
    };
  }, [search]);

  const sidebarId = React.useId();

  return (
    <>
      <Header sidebarId={sidebarId} />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Sidebar id={sidebarId} />
        <Main>
          <Layout colLeft={<LeftPanelView />} colRight={<RightPanelView />} />
        </Main>
      </Box>
    </>
  );
});

/*
 * React contexts cannot be used inside the component in which they are defined
 * (see https://react.dev/reference/react/useContext#caveats). By defining the
 * Search Context and Navigate Context (via MainSearchNavigationContext) in
 * this wrapper component, SearchRouter may use them.
 */
function SearchRouterWrapper({
  paramsOverride,
}: SearchRouterArgs): React.ReactNode {
  const { searchStore } = useStores();
  return (
    <UiPreferences>
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
    </UiPreferences>
  );
}

export default observer(SearchRouterWrapper);
