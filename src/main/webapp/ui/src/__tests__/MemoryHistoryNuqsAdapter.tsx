import { useLocation, useRouter } from "@tanstack/react-router";
import {
  renderQueryString,
  unstable_createAdapterProvider,
  type unstable_UpdateUrlFunction,
} from "nuqs/adapters/custom";
import { useCallback } from "react";
import { searchParamsFromRouterSearch } from "@/modules/common/table-list/utils/viewStorage";

/** Keep nuqs updates on the story router; the browser adapter reads location.search. */
export const MemoryHistoryNuqsAdapter = unstable_createAdapterProvider(() => {
  const location = useLocation();
  const router = useRouter();
  const updateUrl = useCallback<unstable_UpdateUrlFunction>(
    (search, options) => {
      const current = router.history.location;
      void router.navigate({
        to: current.pathname + renderQueryString(search),
        replace: options.history === "replace",
        resetScroll: options.scroll,
        hash: (previous) => previous ?? "",
        state: (previous) => previous,
      });
    },
    [router],
  );
  const getSearchParamsSnapshot = useCallback(() => new URLSearchParams(router.history.location.search), [router]);
  return {
    // The parsed search, converted as the nuqs adapter for TanStack Router converts it.
    searchParams: searchParamsFromRouterSearch(location.search),
    pathname: location.pathname,
    updateUrl,
    getSearchParamsSnapshot,
    rateLimitFactor: 0,
  };
});
