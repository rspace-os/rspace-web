//@flow
import React, { type Node, useContext } from "react";
import NavigateContext from "../../stores/contexts/Navigate";
import SearchContext from "../../stores/contexts/Search";
import { parseCoreFetcherArgsFromUrl } from "../../stores/models/Fetcher/CoreFetcher";
import useStores from "../../stores/use-stores";
import { mkAlert } from "../../stores/contexts/Alert";
import { UserCancelledAction } from "../../util/error";

type MainSearchNavigationContextArgs = {|
  children: Node,
|};

/*
 * This NavigationContext captures calls to `navigate` made within Inventory's
 * main search UI (i.e. /inventory/search) and performs two actions
 *  1. It updates the current search parameters of the page-wide SearchContext,
 *     and triggers a new search whether they have changed or not.
 *  2. It propagates the `navigate` calls to the parent NavigationContext,
 *     ultimately resulting in react-router updating the browser's URL.
 */
export default function MainSearchNavigationContext({
  children,
}: MainSearchNavigationContextArgs): Node {
  const { search } = useContext(SearchContext);
  const { uiStore } = useStores();
  const { useNavigate, useLocation } = useContext(NavigateContext);
  const navigate = useNavigate();

  const doSearch = async (urlSearchParams: URLSearchParams) => {
    const params = parseCoreFetcherArgsFromUrl(urlSearchParams);

    try {
      await search.setupAndPerformInitialSearch(params);
    } catch (error) {
      console.error(error);
      uiStore.addAlert(
        mkAlert({
          title: "Search failed.",
          message:
            error.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
          isInfinite: true,
        })
      );
    }

    if (
      !search.activeResult &&
      Boolean(search.filteredResults.length) &&
      !uiStore.isSingleColumnLayout
    ) {
      try {
        await search.setActiveResult();
        uiStore.setVisiblePanel("right");
      } catch (e) {
        if (e instanceof UserCancelledAction) return;
        throw e;
      }
    }
  };

  const newUseNavigate =
    () => (url: string, opts: ?{| skipToParentContext?: boolean |}) => {
      const { skipToParentContext = false } = opts ?? {
        skipToParentContext: false,
      };
      if (/^\/inventory\/search/.test(url) && !skipToParentContext) {
        /*
         * If the navigation is to another part of the Inventory search page,
         * with either the same or different search parameters, then the
         * search is re-run.
         */
        void doSearch(
          new URLSearchParams(url.match(/\/inventory\/search\?(.*)/)?.[1])
        );
        /*
         * We also invoke the parent NavigationContext to propagate the
         * navigation up, until it reaches the root NavigationContext which
         * then calls react-router and the URL is updated. We do not want to
         * open that URL in a new window as the user has been navigated in this
         * browser window by updating the search paramters.
         */
        navigate(url, { skipToParentContext: false });
      } else {
        /*
         * For all other URLs, including all other Inventory URLs such as the
         * Permalink pages, we propagate up to the parent NavigationContext,
         * which will eventually invoke react-router and thus navigate the
         * page. If the URL is to another Inventory page then the Routers
         * configured in InventoryRouter will update the page and reconfigure
         * the search parameters.
         *
         * Because MainSearchNavigationContext is not the root context, we
         * continue to pass the skipToParentContext parameter making this
         * context completely transparent to all contexts below when set.
         */
        navigate(url, { skipToParentContext });
      }
    };

  return (
    <NavigateContext.Provider
      value={{
        useNavigate: newUseNavigate,
        useLocation,
      }}
    >
      {children}
    </NavigateContext.Provider>
  );
}
