//@flow
import React, { type Node } from "react";
import NavigateContext from "../../stores/contexts/Navigate";
import SearchContext from "../../stores/contexts/Search";
import { parseCoreFetcherArgsFromUrl } from "../../stores/models/Fetcher/CoreFetcher";

/**
 * This Navigation Context wraps the search components inside of the Inventory
 * forms: container's content, sample's subsamples, and template's samples.
 * This is so that when `navigate` is called by any sub-component (such as the
 * tag search combobox) it is the inner search whose state gets updated and not
 * the page's main search in the left panel. Where `navigate` is called with
 * URL that isn't `/inventory/search`, those navigations are propagated up to
 * the parent navigation context and should result in a page-wide navigation.
 * As such, tapping a Global ID link in the table of search results still
 * results in the whole page being navigated and not the `activeResult` of the
 * inner search which is never shown in the UI.
 */

type NavigationContextArgs = {|
  children: Node,
|};

export default function NavigationContext({
  children,
}: NavigationContextArgs): Node {
  const { search } = React.useContext(SearchContext);

  const { useNavigate: parentUseNavigate, useLocation } =
    React.useContext(NavigateContext);
  const parentNavigate = parentUseNavigate();

  const useNavigate =
    () =>
    (
      url: string,
      opts: ?{| skipToParentContext?: boolean, modifyVisiblePanel?: boolean |}
    ) => {
      const { skipToParentContext = false } = opts ?? {
        skipToParentContext: false,
        modifyVisiblePanel: true,
      };
      /* Navigation to search pages are changes to the right panel's search */
      if (/^\/inventory\/search/.test(url) && !skipToParentContext) {
        const searchParams = new URLSearchParams(
          url.match(/\/inventory\/search\?(.*)/)?.[1]
        );
        const newCoreFetcherArgs = parseCoreFetcherArgsFromUrl(searchParams);
        // by combining with existing fetcher args, we keep the `parentGlobalId`
        const combinedCoreFetcherArgs = parseCoreFetcherArgsFromUrl(
          search.fetcher.generateQuery(newCoreFetcherArgs)
        );
        void search.setupAndPerformInitialSearch(combinedCoreFetcherArgs);
      } else {
        /* Open all other pages, such as permalink pages, in the left panel's search */
        parentNavigate(url);
      }
    };

  return (
    <NavigateContext.Provider
      value={{
        useNavigate,
        useLocation,
      }}
    >
      {children}
    </NavigateContext.Provider>
  );
}
