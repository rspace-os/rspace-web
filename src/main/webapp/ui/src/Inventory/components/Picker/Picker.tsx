import React, { useCallback, useEffect } from "react";
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
import { menuIDs } from "../../../util/menuIDs";
import Box from "@mui/material/Box";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import {
  type CoreFetcherArgs,
  type SearchView as SearchViewType,
} from "../../../stores/definitions/Search";
import Alert from "@mui/material/Alert";
import InnerSearchNavigationContext from "../InnerSearchNavigationContext";

const TABS: Array<SearchViewType> = ["LIST", "TREE"];

type InventoryPickerArgs = {
  onAddition: (records: Array<InventoryRecord>) => void;
  onCancel?: () => void;
  elevation?: number;
  search: Search;
  header?: React.ReactNode;
  selectionHelpText?: string | null;
  testId?: string;
  paddingless?: boolean;
  showActions?: boolean;
  resetActiveResultOnClose?: boolean;
};

function InventoryPicker({
  onAddition,
  onCancel,
  elevation = 0,
  search,
  header,
  selectionHelpText,
  testId,
  paddingless = false,
  showActions = false,
  resetActiveResultOnClose = false,
}: InventoryPickerArgs): React.ReactNode {
  const handleSearch = (props: CoreFetcherArgs) => {
    void search.fetcher.performInitialSearch(props);
  };

  const resetActiveResultIfNeeded = useCallback(() => {
    if (!resetActiveResultOnClose) return;
    search.activeResult = null;
  }, [resetActiveResultOnClose, search]);

  const handleAddition = useCallback((records: Array<InventoryRecord>) => {
    try {
      onAddition(records);
    } finally {
      resetActiveResultIfNeeded();
    }
  }, [onAddition, resetActiveResultIfNeeded]);

  const handleCancel = useCallback(() => {
    if (search.uiConfig.instantConfirm || !onCancel) {
      handleAddition([]);
      return;
    }

    try {
      onCancel();
    } finally {
      resetActiveResultIfNeeded();
    }
  }, [handleAddition, onCancel, resetActiveResultIfNeeded, search.uiConfig.instantConfirm]);

  /*
   * When the user selects a result, the activeResult is set. This logic then
   * invokes onAddition so that callers of this component can rely on one method
   * of knowing when an item has been selected regardless of whether multi or
   * singular selection is used.
   */
  useEffect(() => {
    const singularSelection = search.uiConfig.selectionMode === "SINGLE";
    const instantConfirm = search.uiConfig.instantConfirm;
    if (singularSelection && instantConfirm && search.activeResult) {
      handleAddition([search.activeResult]);
    }
  }, [handleAddition, search.activeResult, search.uiConfig.selectionMode, search.uiConfig.instantConfirm]);

  const getSelectedRecords = () => {
    if (search.uiConfig.selectionMode === "SINGLE") {
      return search.activeResult ? [search.activeResult] : [];
    }
    const selectedRecords =
      search.searchView === "LIST" ? [...search.selectedResults] : [];
    if (search.searchView === "TREE" && search.tree.selectedNode) {
      selectedRecords.push(search.tree.selectedNode);
    }

    return selectedRecords;
  }

  const selectedRecords = getSelectedRecords();
  const hasDisallowedSelection = selectedRecords.some((record) =>
    search.alwaysFilterOut(record),
  );

  return (
    <Card
      elevation={elevation}
      data-test-id={testId}
      sx={{
        height: "100%",
        width: "100%",
        display: "flex",
        flexDirection: "column",
        p: 1,
      }}
    >
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
          {typeof header !== "undefined" && (
            <CardHeader title={header} sx={{ flexWrap: "nowrap", py: 1 }} />
          )}
          <CardContent
            sx={{
              flexGrow: 1,
              overflowY: "auto",
              display: "flex",
              flexDirection: "column",
              padding: `${paddingless ? 0 : 2} !important`,
              paddingTop: "0 !important",
              overflowX: "hidden",
            }}
          >
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
          </CardContent>
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
                handleAddition(selectedRecords);
              }}
              disabled={selectedRecords.length === 0 || hasDisallowedSelection}
            >
              Choose
            </Button>
            <Button onClick={handleCancel}>
              Cancel
            </Button>
          </>
        </CardActions>
      )}
    </Card>
  );
}

export default observer(InventoryPicker);
