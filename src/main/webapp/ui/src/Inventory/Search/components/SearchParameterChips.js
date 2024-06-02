//@flow

import React, {
  type Node,
  type ComponentType,
  type ElementProps,
  useContext,
  useEffect,
} from "react";
import Chip from "@mui/material/Chip";
import SearchContext from "../../../stores/contexts/Search";
import { toTitleCase } from "../../../util/Util";
import { withStyles } from "Styles";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";

const CustomChip = withStyles<
  ElementProps<typeof Chip>,
  { label: string, root: string }
>(() => ({
  label: {
    letterSpacing: "0.02em",
    padding: "4px 12px",
  },
  root: {
    maxWidth: "100%",
  },
}))((props) => <Chip size="small" {...props} />);

const CustomGridItem = withStyles<{| children: Node |}, { root: string }>(
  () => ({
    root: {
      maxWidth: "100%",
    },
  })
)(({ children, classes }) => (
  <Grid item className={classes.root}>
    {children}
  </Grid>
));

function SearchParameterChips(): Node {
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
            <Grid item key={resultType}>
              <CustomChip
                label={`Type: ${toTitleCase(resultType)}s`}
                onDelete={
                  search.fetcher.allTypesAllowed &&
                  search.uiConfig.allowedTypeFilters.has("ALL")
                    ? () => {
                        search.setTypeFilter("ALL");
                      }
                    : null
                }
              />
            </Grid>
          )
      )}
      {search.fetcher.owner && (
        <CustomGridItem>
          <CustomChip
            label={`Owner: ${search.fetcher.owner.label}`}
            onDelete={() => {
              search.setOwner(null);
            }}
          />
        </CustomGridItem>
      )}
      {search.fetcher.benchOwner && (
        <CustomGridItem>
          <CustomChip
            label={`Bench Owner: ${search.fetcher.benchOwner.label}`}
            onDelete={() => {
              search.setBench(null);
            }}
          />
        </CustomGridItem>
      )}
      {search.fetcher.deletedItems && (
        <Grid item>
          <CustomChip
            label={`Status: ${search.fetcher.deletedItemsLabel}`}
            onDelete={
              search.fetcher.deletedItems !== "EXCLUDE" &&
              search.showStatusFilter
                ? () => {
                    search.setDeletedItems("EXCLUDE");
                  }
                : null
            }
          />
        </Grid>
      )}
      {search.currentBasket(searchStore.savedBaskets) && (
        <Grid item>
          <CustomChip
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
          <Grid item>
            <CustomChip
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

export default (observer(SearchParameterChips): ComponentType<{||}>);
