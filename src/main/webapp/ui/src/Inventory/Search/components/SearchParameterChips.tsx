import React, { useContext, useEffect } from "react";
import Chip from "@mui/material/Chip";
import SearchContext from "../../../stores/contexts/Search";
import { toTitleCase } from "../../../util/Util";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";

function SearchParameterChips(): React.ReactNode {
  const { search } = useContext(SearchContext);
  const { searchStore } = useStores();

  useEffect(() => {
    void searchStore.getBaskets();
  }, []);

  return (
    <Grid container direction="row" spacing={1}>
      {["CONTAINER", "SAMPLE", "SUBSAMPLE", "TEMPLATE"].map(
        (resultType) =>
          search.fetcher.resultType === resultType && (
            <Grid key={resultType}>
              <Chip
                size="small"
                sx={{ maxWidth: "100%" }}
                slotProps={{ label: { sx: { letterSpacing: "0.02em", p: "4px 12px" } } }}
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
            </Grid>
          )
      )}
      {search.fetcher.owner && (
        <Grid sx={{ maxWidth: "100%" }}>
          <Chip
            size="small"
            sx={{ maxWidth: "100%" }}
            slotProps={{ label: { sx: { letterSpacing: "0.02em", p: "4px 12px" } } }}
            label={`Owner: ${search.fetcher.owner.label}`}
            onDelete={() => {
              search.setOwner(null);
            }}
          />
        </Grid>
      )}
      {search.fetcher.benchOwner && (
        <Grid sx={{ maxWidth: "100%" }}>
          <Chip
            size="small"
            sx={{ maxWidth: "100%" }}
            slotProps={{ label: { sx: { letterSpacing: "0.02em", p: "4px 12px" } } }}
            label={`Bench Owner: ${search.fetcher.benchOwner.label}`}
            onDelete={() => {
              search.setBench(null);
            }}
          />
        </Grid>
      )}
      {search.fetcher.deletedItems && (
        <Grid>
          <Chip
            size="small"
            sx={{ maxWidth: "100%" }}
            slotProps={{ label: { sx: { letterSpacing: "0.02em", p: "4px 12px" } } }}
            label={`Status: ${search.fetcher.deletedItemsLabel}`}
            onDelete={
              search.fetcher.deletedItems !== "EXCLUDE" &&
              search.showStatusFilter
                ? () => {
                    search.setDeletedItems("EXCLUDE");
                  }
                : undefined
            }
          />
        </Grid>
      )}
      {search.currentBasket(searchStore.savedBaskets) && (
        <Grid>
          <Chip
            size="small"
            sx={{ maxWidth: "100%" }}
            slotProps={{ label: { sx: { letterSpacing: "0.02em", p: "4px 12px" } } }}
            label={`Basket: ${
              search.currentBasket(searchStore.savedBaskets)?.name ?? "UNKNOWN"
            }`}
            onDelete={() => {
              search.setParentGlobalId(null);
            }}
          />
        </Grid>
      )}
      {search.fetcher.parentGlobalId &&
        (search.fetcher.parentIsSample || search.fetcher.parentIsContainer) &&
        !search.uiConfig.hideContentsOfChip && (
          <Grid>
            <Chip
              size="small"
              sx={{ maxWidth: "100%" }}
              slotProps={{ label: { sx: { letterSpacing: "0.02em", p: "4px 12px" } } }}
              label={`Contents of: ${search.fetcher.parentGlobalId}`}
              onDelete={() => {
                search.setParentGlobalId(null);
              }}
            />
          </Grid>
        )}
    </Grid>
  );
}

export default observer(SearchParameterChips);
