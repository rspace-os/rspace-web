//@flow

import { type CoreFetcherArgs } from "../stores/definitions/Search";
import { type InventoryRecord } from "../stores/definitions/InventoryRecord";
import useStores from "../stores/use-stores";
import React from "react";
import NavigateContext from "../stores/contexts/Navigate";
import { UserCancelledAction } from "../util/error";

export default function useNavigateHelpers(): {|
  navigateToSearch: (CoreFetcherArgs) => void,
  navigateToRecord: (InventoryRecord) => Promise<void>,
|} {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const { searchStore, uiStore } = useStores();

  return {
    navigateToSearch: (destination) => {
      const params = searchStore.fetcher.generateNewQuery(destination);
      navigate(`/inventory/search?${params.toString()}`);
      uiStore.setVisiblePanel("left");
      /*
       * Once the navigation is complete, setActiveResult may be called in
       * SearchRouter. Doing so before navigation has completed would result in
       * making the first result of the previous search the active result.
       */
      if (uiStore.isVerySmall) uiStore.toggleSidebar(false);
    },
    navigateToRecord: async (destination) => {
      try {
        /*
         * setActiveResult can be called here, unlike in navigateToSearch,
         * because we already have a reference to the record being navigated to.
         * All the same, setActiveResult will still be called by SearchRouter but
         * calling here first speeds up load time.
         */
        await searchStore.search.setActiveResult(destination);
        if (destination.permalinkURL) {
          navigate(destination.permalinkURL);
          uiStore.setVisiblePanel("right");
        }
      } catch (e) {
        if (e instanceof UserCancelledAction) return;
        /*
         * Do nothing; if the user doesn't want to lose any modifications they've
         * made to the active result then we can't show the record they tried to
         * navigate to.
         */
        throw e;
      } finally {
        if (uiStore.isVerySmall) uiStore.toggleSidebar(false);
      }
    },
  };
}
