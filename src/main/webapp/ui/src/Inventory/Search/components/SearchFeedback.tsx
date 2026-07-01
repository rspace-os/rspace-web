import Alert, { alertClasses } from "@mui/material/Alert";
import LinearProgress from "@mui/material/LinearProgress";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import { match } from "@/util/Util";
import NavigateContext from "../../../stores/contexts/Navigate";
import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import SaveSearch from "./SaveSearch";

function SearchFeedback(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore } = useStores();
  const { search } = useContext(SearchContext);
  const { useLocation } = useContext(NavigateContext);

  function useSearchParams() {
    return new URLSearchParams(useLocation().search);
  }
  const searchParams = useSearchParams();
  const currentBasket = searchStore.savedBaskets.find((b) => b.globalId === searchParams.get("parentGlobalId"));

  const statusText = match<void, string>([
    [() => search.loading, t("search.feedback.loading")],
    [() => Boolean(search.fetcher.error), search.fetcher.error],
    [() => Boolean(search.fetcher.query), t("search.feedback.results", { count: search.count })],
    [() => search.fetcher.resultType === "CONTAINER", t("search.feedback.topLevelContainers", { count: search.count })],
    [() => search.fetcher.resultType === "SAMPLE", t("search.feedback.samples", { count: search.count })],
    [() => search.fetcher.resultType === "SUBSAMPLE", t("search.feedback.subsamples", { count: search.count })],
    [() => search.fetcher.resultType === "INSTRUMENT", t("search.feedback.instruments", { count: search.count })],
    [
      () => search.fetcher.resultType === "INSTRUMENT_TEMPLATE",
      t("search.feedback.instrumentTemplates", { count: search.count }),
    ],
    [() => search.fetcher.resultType === "SAMPLE_TEMPLATE", t("search.feedback.templates", { count: search.count })],
    [() => search.fetcher.parentGlobalIdType === "SAMPLE", t("search.feedback.subsamples", { count: search.count })],
    [
      () => search.fetcher.parentGlobalIdType === "CONTAINER",
      t("search.feedback.containerContents", { count: search.count }),
    ],
    [
      () => search.fetcher.parentGlobalIdType === "TEMPLATE",
      t("search.feedback.samplesOfTemplate", { count: search.count }),
    ],
    [
      () => search.fetcher.parentGlobalIdType === "INSTRUMENT_TEMPLATE",
      t("search.feedback.instrumentsOfTemplate", { count: search.count }),
    ],
    [() => search.fetcher.parentGlobalIdType === "BENCH", t("search.feedback.itemsOnBench", { count: search.count })],
    [
      () => search.fetcher.parentGlobalIdType === "BASKET",
      t("search.feedback.itemsInBasket", {
        basketName: currentBasket?.name || t("search.feedback.thisBasket"),
        count: search.count,
      }),
    ],
    [
      () => Boolean(search.fetcher.permalink),
      search.filteredResults.length > 0
        ? t("search.feedback.foundGlobalId", {
            globalId: search.filteredResults[0].globalId ?? t("search.feedback.errorGlobalId"),
          })
        : "",
    ],
    [() => true, t("search.feedback.undetermined")],
  ]);

  return (
    <Alert
      sx={(theme) => ({
        p: `${theme.spacing(0.5)} ${theme.spacing(2)}`,
        position: "relative",
        backgroundColor: theme.palette.primary.background,
        color: theme.palette.primary.contrastText,
        minHeight: theme.spacing(4),
        [`& .${alertClasses.message}`]: {
          p: 0,
          alignSelf: "center",
        },
        [`& .${alertClasses.action}`]: {
          p: 0,
          alignSelf: "center",
        },
      })}
      icon={false}
      severity={search.fetcher.error ? "warning" : "info"}
      action={<SaveSearch sx={{ p: 0 }} />}
      aria-label={t("search.feedback.label")}
      role="status"
    >
      {statusText()}
      {search.loading && (
        <LinearProgress
          sx={(theme) => ({
            position: "absolute",
            borderBottomLeftRadius: theme.spacing(0.5),
            borderBottomRightRadius: theme.spacing(0.5),
            bottom: 0,
            left: 0,
            width: "100%",
          })}
        />
      )}
    </Alert>
  );
}

export default observer(SearchFeedback);
