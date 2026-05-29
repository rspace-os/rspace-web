import React, { useContext, useEffect } from "react";
import Chip from "@mui/material/Chip";
import SearchContext from "../../../stores/contexts/Search";
import { toTitleCase } from "../../../util/Util";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";

function ParameterChip({
  label,
  onDelete,
}: {
  label: string;
  onDelete?: () => void;
}): React.ReactNode {
  return (
    <Chip
      size="small"
      sx={{ maxWidth: "100%" }}
      slotProps={{ label: { sx: { letterSpacing: "0.02em", p: "4px 12px" } } }}
      label={label}
      onDelete={onDelete}
    />
  );
}

function SearchParameterChips(): React.ReactNode {
  const { search } = useContext(SearchContext);
  const { searchStore } = useStores();

  useEffect(() => {
    void searchStore.getBaskets();
  }, []);

  const { resultType } = search.fetcher;
  const currentBasket = search.currentBasket(searchStore.savedBaskets);

  return (
    <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
      {resultType && resultType !== "ALL" && (
        <ParameterChip
          label={`Type: ${toTitleCase(resultType)}s`}
          onDelete={
            search.fetcher.allTypesAllowed &&
            search.uiConfig.allowedTypeFilters.has("ALL")
              ? () => {
                  search.setTypeFilter("ALL");
                }
              : undefined
          }
        />
      )}
      {search.fetcher.owner && (
        <ParameterChip
          label={`Owner: ${search.fetcher.owner.label}`}
          onDelete={() => {
            search.setOwner(null);
          }}
        />
      )}
      {search.fetcher.benchOwner && (
        <ParameterChip
          label={`Bench Owner: ${search.fetcher.benchOwner.label}`}
          onDelete={() => {
            search.setBench(null);
          }}
        />
      )}
      {search.fetcher.deletedItems && (
        <ParameterChip
          label={`Status: ${search.fetcher.deletedItemsLabel}`}
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
          label={`Basket: ${currentBasket.name ?? "UNKNOWN"}`}
          onDelete={() => {
            search.setParentGlobalId(null);
          }}
        />
      )}
      {search.fetcher.parentGlobalId &&
        (search.fetcher.parentIsSample || search.fetcher.parentIsContainer) &&
        !search.uiConfig.hideContentsOfChip && (
          <ParameterChip
            label={`Contents of: ${search.fetcher.parentGlobalId}`}
            onDelete={() => {
              search.setParentGlobalId(null);
            }}
          />
        )}
    </Stack>
  );
}

export default observer(SearchParameterChips);
