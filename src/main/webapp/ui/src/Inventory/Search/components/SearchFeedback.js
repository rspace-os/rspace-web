// @flow

import Alert from "@mui/material/Alert";
import LinearProgress from "@mui/material/LinearProgress";
import React, { useContext, type Node, type ComponentType } from "react";
import useStores from "../../../stores/use-stores";
import { makeStyles } from "tss-react/mui";
import { match } from "../../../util/Util";
import { observer } from "mobx-react-lite";
import SearchContext from "../../../stores/contexts/Search";
import SaveSearch from "./SaveSearch";
import NavigateContext, {
  type UseLocation,
} from "../../../stores/contexts/Navigate";

const useStyles = makeStyles()((theme) => ({
  progress: {
    position: "absolute",
    borderBottomLeftRadius: theme.spacing(0.5),
    borderBottomRightRadius: theme.spacing(0.5),
    bottom: 0,
    left: 0,
    width: "100%",
  },
  alert: {
    position: "relative",
    backgroundColor: theme.palette.primary.background,
    padding: theme.spacing(0.625, 1, 0.625, 1),
    minHeight: theme.spacing(4),
  },
  message: {
    padding: 0,
  },
  text: {
    hyphens: "auto",
  },
  action: {
    padding: theme.spacing(0, 0, 0, 1),
  },
}));

function SearchFeedback(): Node {
  const { uiStore, searchStore } = useStores();
  const { search } = useContext(SearchContext);
  const { classes } = useStyles({ singleColumn: uiStore.isSingleColumnLayout });
  const { useLocation } = useContext(NavigateContext);

  function useSearchParams() {
    return new URLSearchParams((useLocation(): UseLocation).search);
  }
  const searchParams = useSearchParams();
  const currentBasket = searchStore.savedBaskets.find(
    (b) => b.globalId === searchParams.get("parentGlobalId")
  );

  const resultsStatusText = (what: string) => `${search.count} ${what} found.`;

  const statusText = match<void, string>([
    [() => search.loading, "Loading..."],
    [() => Boolean(search.fetcher.error), search.fetcher.error],
    [() => Boolean(search.fetcher.query), resultsStatusText("search results")],
    [
      () => search.fetcher.resultType === "CONTAINER",
      resultsStatusText("top-level containers"),
    ],
    [
      () => search.fetcher.resultType === "SAMPLE",
      resultsStatusText("samples"),
    ],
    [
      () => search.fetcher.resultType === "SUBSAMPLE",
      resultsStatusText("subsamples"),
    ],
    [
      () => search.fetcher.resultType === "TEMPLATE",
      resultsStatusText("templates"),
    ],
    [
      () => search.fetcher.parentGlobalIdType === "SAMPLE",
      resultsStatusText("subsamples"),
    ],
    [
      () => search.fetcher.parentGlobalIdType === "CONTAINER",
      resultsStatusText("container contents"),
    ],
    [
      () => search.fetcher.parentGlobalIdType === "TEMPLATE",
      resultsStatusText("samples of the template"),
    ],
    [
      () => search.fetcher.parentGlobalIdType === "BENCH",
      `${search.count} items found on this bench.`,
    ],
    [
      () => search.fetcher.parentGlobalIdType === "BASKET",
      `${search.count} items found in ${currentBasket?.name || "this basket"}.`,
    ],
    [
      () => Boolean(search.fetcher.permalink),
      search.filteredResults.length > 0
        ? `Found ${search.filteredResults[0].globalId ?? "ERROR"}.`
        : "",
    ],
    [() => true, "Results cannot be fully determined."],
  ]);

  return (
    <Alert
      className={classes.alert}
      classes={{
        message: classes.message,
        action: classes.action,
      }}
      icon={false}
      severity={search.fetcher.error ? "warning" : "info"}
      action={<SaveSearch />}
      aria-label="Search status"
      role="status"
    >
      {statusText()}
      {search.loading && <LinearProgress className={classes.progress} />}
    </Alert>
  );
}

export default (observer(SearchFeedback): ComponentType<{||}>);
