import Chip from "@mui/material/Chip";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useEffect } from "react";
import { useTranslation } from "react-i18next";
import SearchContext from "../../../stores/contexts/Search";
import type { ResultType } from "../../../stores/definitions/Search";
import useStores from "../../../stores/use-stores";

function ParameterChip({ label, onDelete }: { label: string; onDelete?: () => void }): React.ReactNode {
  return <Chip size="small" label={label} onDelete={onDelete} />;
}

function SearchParameterChips(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { search } = useContext(SearchContext);
  const { searchStore } = useStores();

  useEffect(() => {
    void searchStore.getBaskets();
  }, []);

  const { resultType } = search.fetcher;
  const currentBasket = search.currentBasket(searchStore.savedBaskets);
  const recordTypePluralLabels: Record<Exclude<ResultType, "ALL">, string> = {
    CONTAINER: t("recordTypes.container.plural"),
    INSTRUMENT: t("recordTypes.instrument.plural"),
    INSTRUMENT_TEMPLATE: t("recordTypes.instrumentTemplate.plural"),
    SAMPLE: t("recordTypes.sample.plural"),
    SAMPLE_TEMPLATE: t("recordTypes.sampleTemplate.plural"),
    SUBSAMPLE: t("recordTypes.subsample.plural"),
  };

  return (
    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
      {resultType && resultType !== "ALL" && (
        <ParameterChip
          label={t("search.parameterChips.type", { type: recordTypePluralLabels[resultType] })}
          onDelete={
            search.fetcher.allTypesAllowed && search.uiConfig.allowedTypeFilters.has("ALL")
              ? () => {
                  search.setTypeFilter("ALL");
                }
              : undefined
          }
        />
      )}
      {search.fetcher.owner && (
        <ParameterChip
          label={t("search.parameterChips.owner", { owner: search.fetcher.owner.label })}
          onDelete={() => {
            search.setOwner(null);
          }}
        />
      )}
      {search.fetcher.benchOwner && (
        <ParameterChip
          label={t("search.parameterChips.benchOwner", { owner: search.fetcher.benchOwner.label })}
          onDelete={() => {
            search.setBench(null);
          }}
        />
      )}
      {search.fetcher.deletedItems && (
        <ParameterChip
          label={t("search.parameterChips.status", { status: search.fetcher.deletedItemsLabel })}
          onDelete={
            search.fetcher.deletedItems !== "EXCLUDE" && search.showStatusFilter
              ? () => {
                  search.setDeletedItems("EXCLUDE");
                }
              : undefined
          }
        />
      )}
      {currentBasket && (
        <ParameterChip
          label={t("search.parameterChips.basket", {
            basket: currentBasket.name ?? t("search.parameterChips.unknown"),
          })}
          onDelete={() => {
            search.setParentGlobalId(null);
          }}
        />
      )}
      {search.fetcher.parentGlobalId &&
        (search.fetcher.parentIsSample || search.fetcher.parentIsContainer) &&
        !search.uiConfig.hideContentsOfChip && (
          <ParameterChip
            label={t("search.parameterChips.contentsOf", { globalId: search.fetcher.parentGlobalId })}
            onDelete={() => {
              search.setParentGlobalId(null);
            }}
          />
        )}
    </Stack>
  );
}

export default observer(SearchParameterChips);
