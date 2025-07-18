import React, { useEffect } from "react";
import { observer } from "mobx-react-lite";
import Button from "@mui/material/Button";
import Search from "../../../stores/models/Search";
import SearchContext from "../../../stores/contexts/Search";
import SearchViewComponent from "../../Search/SearchView";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import CardHeader from "@mui/material/CardHeader";
import SearchComponent from "../../Search/Search";
import { withStyles } from "Styles";
import { menuIDs } from "../../../util/menuIDs";
import RsSet from "../../../util/set";
import Box from "@mui/material/Box";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import {
  type CoreFetcherArgs,
  type SearchView as SearchViewType,
} from "../../../stores/definitions/Search";
import Alert from "@mui/material/Alert";
import InnerSearchNavigationContext from "../InnerSearchNavigationContext";

const TABS: Array<SearchViewType> = ["LIST", "TREE"];

const MaxSizeCard = withStyles<
  { elevation: number; testId?: string; children: React.ReactNode },
  { root: string }
>(() => ({
  root: {
    height: "100%",
    width: "100%",
    display: "flex",
    flexDirection: "column",
  },
}))(({ classes, children, elevation, testId }) => (
  <Card elevation={elevation} classes={classes} data-test-id={testId}>
    {children}
  </Card>
));

const FullHeightCardContent = withStyles<
  { paddingless: boolean } & React.ComponentProps<typeof CardContent>,
  { root: string }
>((theme, { paddingless }) => ({
  root: {
    flexGrow: 1,
    overflowY: "auto",
    display: "flex",
    flexDirection: "column",
    padding: `${theme.spacing(paddingless ? 0 : 2)} !important`,
    paddingTop: "0 !important",
    overflowX: "hidden",
  },
}))(({ paddingless, ...props }) => <CardContent {...props} />);

const CustomCardHeader = withStyles<
  { title: React.ReactNode },
  { root: string }
>(() => ({
  root: {
    flexWrap: "nowrap",
  },
}))(({ title, classes }) => (
  <CardHeader title={title} classes={classes} sx={{ py: 1 }} />
));

type InventoryPickerArgs = {
  onAddition: (records: Array<InventoryRecord>) => void;
  elevation?: number;
  search: Search;
  header?: React.ReactNode;
  selectionHelpText?: string | null;
  testId?: string;
  paddingless?: boolean;
  showActions?: boolean;
};

function InventoryPicker({
  onAddition,
  elevation = 0,
  search,
  header,
  selectionHelpText,
  testId,
  paddingless = false,
  showActions = false,
}: InventoryPickerArgs): React.ReactNode {
  const handleSearch = (props: CoreFetcherArgs) => {
    void search.fetcher.performInitialSearch(props);
  };

  /*
   * When the user selects a result, the activeResult is set. This logic then
   * invokes onAddition so that callers of this component can rely on one method
   * of knowing when an item has been selected regardless of whether multi or
   * singular selection is used.
   */
  useEffect(() => {
    const singularSelection = search.uiConfig.selectionMode === "SINGLE";
    if (singularSelection && search.activeResult) {
      onAddition([search.activeResult]);
    }
  }, [search.activeResult, onAddition, search.uiConfig.selectionMode]);

  const selectedRecords =
    search.searchView === "LIST" ? [...search.selectedResults] : [];
  if (search.searchView === "TREE" && search.tree.selectedNode)
    selectedRecords.push(search.tree.selectedNode);
  const selectedAndNotAllowed = new RsSet(selectedRecords).filter(
    search.alwaysFilterOut,
  );

  return (
    <MaxSizeCard elevation={elevation} testId={testId}>
      <SearchContext.Provider
        value={{
          search,
          /*
           * Note that here we're, rather unusually, setting
           * `differentSearchForSettingActiveResult` and `search` to the same
           * instance of Search. This means that when a row of the table of
           * results is tapped, no other search will be impacted; most notably
           * the main search will not change.
           */
          differentSearchForSettingActiveResult: search,
          isChild: false,
        }}
      >
        <InnerSearchNavigationContext>
          {typeof header !== "undefined" && <CustomCardHeader title={header} />}
          <FullHeightCardContent paddingless={paddingless}>
            <Box mb={1}>
              <SearchComponent
                TABS={TABS}
                size="small"
                handleSearch={(query) =>
                  handleSearch({ query, resultType: search.fetcher.resultType })
                }
              />
            </Box>
            {selectionHelpText && (
              <Box mb={1}>
                <Alert severity="info">{selectionHelpText}</Alert>
              </Box>
            )}
            <SearchViewComponent contextMenuId={menuIDs.PICKER} />
          </FullHeightCardContent>
        </InnerSearchNavigationContext>
      </SearchContext.Provider>
      {showActions && (
        <CardActions>
          <>
            <Button
              variant="contained"
              color="callToAction"
              disableElevation
              onClick={() => {
                onAddition(selectedRecords);
              }}
              disabled={
                selectedRecords.length === 0 || !selectedAndNotAllowed.isEmpty
              }
            >
              Choose
            </Button>
            <Button
              onClick={() => {
                onAddition([]);
              }}
            >
              Cancel
            </Button>
          </>
        </CardActions>
      )}
    </MaxSizeCard>
  );
}

export default observer(InventoryPicker);
