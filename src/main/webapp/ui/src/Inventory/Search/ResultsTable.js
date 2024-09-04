// @flow

import React, {
  useContext,
  type Node,
  type ComponentType,
  type ElementProps,
} from "react";
import { observer } from "mobx-react-lite";
import SearchContext from "../../stores/contexts/Search";
import { paginationOptions } from "../../util/table";
import { menuIDs } from "../../util/menuIDs";
import ResultRow from "./components/ResultRow";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TablePagination from "@mui/material/TablePagination";
import CustomTableHead from "./components/CustomTableHead";
import { type SplitButtonOption } from "../components/ContextMenu/ContextMenuSplitButton";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import Skeleton from "@mui/material/Skeleton";
import { withStyles } from "Styles";
import ScrollBox from "./ScrollBox";
import useStores from "../../stores/use-stores";
import { useIsSingleColumnLayout } from "../components/Layout/Layout2x1";

const ResultRowSkeleton = () => {
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  let cols = 3;
  if (!uiStore.isSmall && !uiStore.isVerySmall && isSingleColumnLayout) cols++;
  if (uiStore.isLarge && isSingleColumnLayout) cols++;

  return (
    <TableRow>
      <TableCell colSpan={cols}>
        <Skeleton variant="rectangular" width="100%" height={36} />
      </TableCell>
    </TableRow>
  );
};

const CustomTablePagination = withStyles<
  ElementProps<typeof TablePagination>,
  { root: string }
>(() => ({
  root: {
    overflow: "unset",
  },
}))((props) => (
  <nav>
    <TablePagination labelRowsPerPage="" component="div" {...props} />
  </nav>
));

type ResultsTableArgs = {|
  contextMenuId: $Values<typeof menuIDs>,
|};

function ResultsTable({ contextMenuId }: ResultsTableArgs): Node {
  const { search } = useContext(SearchContext);
  const isSingleColumnLayout = useIsSingleColumnLayout();

  const handleChangePageSize = ({
    target: { value },
  }: {
    target: { value: number, ... },
    ...
  }) => {
    search.setPageSize(value);
  };

  const handleChangePage = (newPage: number) => {
    void search.setPage(newPage);
  };

  const toggleAll = () => {
    const results = search.filteredResults.filter(
      (r) => !search.alwaysFilterOut(r)
    );
    const selected = search.filteredResults.some((r) => r.selected === false);
    results.map((r) => r.toggleSelected(selected));
  };

  const onSelectOptions: Array<SplitButtonOption> = [
    {
      text: "All",
      selection: () => {
        search.filteredResults.map((r) => r.toggleSelected(true));
      },
    },
    {
      text: "None",
      selection: () => {
        search.filteredResults.map((r) => r.toggleSelected(false));
      },
    },
    {
      text: "Invert",
      selection: () => {
        search.filteredResults.map((r) => r.toggleSelected());
      },
    },
    {
      text: "Mine",
      selection: () => {
        search.filteredResults.map((r) =>
          r.toggleSelected(r.currentUserIsOwner ?? false)
        );
      },
    },
    {
      text: "Not Mine",
      selection: () => {
        search.filteredResults.map(
          (r) => r.toggleSelected(r.currentUserIsOwner === false) // if currentUserIsOwner cannot be determined then don't select
        );
      },
    },
    {
      text: "Current",
      selection: () => {
        search.filteredResults.map((r) => r.toggleSelected(!r.deleted));
      },
    },
    {
      text: "In Trash",
      selection: () => {
        search.filteredResults.map((r) => r.toggleSelected(r.deleted));
      },
    },
  ];

  const count = search.count;
  const rowsPerPageOptions = paginationOptions(count);

  return (
    <>
      <ScrollBox overflowY={isSingleColumnLayout ? "unset" : "auto"}>
        <Table size="small" aria-label="Search results" stickyHeader>
          <CustomTableHead
            selectedCount={search.selectedResults.length}
            onSelectOptions={onSelectOptions}
            toggleAll={toggleAll}
            contextMenuId={contextMenuId}
          />
          <TableBody>
            {search.fetcher.loading
              ? new Array<mixed>(search.fetcher.pageSize)
                  .fill(null)
                  .map((_, i) => <ResultRowSkeleton key={i} />)
              : search.filteredResults.map((result) => (
                  <ResultRow
                    key={result.globalId}
                    result={result}
                    adjustableColumns={search.uiConfig.adjustableColumns}
                  />
                ))}
          </TableBody>
        </Table>
      </ScrollBox>
      <CustomTablePagination
        count={count}
        rowsPerPageOptions={rowsPerPageOptions}
        labelRowsPerPage=""
        component="div"
        rowsPerPage={Math.min(search.fetcher.pageSize, count)}
        SelectProps={{
          renderValue: (value: number) =>
            value < count ? value : `${value} (All)`,
        }}
        page={parseInt(search.fetcher.pageNumber) || 0}
        onPageChange={(_event: mixed, page: number) => handleChangePage(page)}
        onRowsPerPageChange={handleChangePageSize}
      />
    </>
  );
}

export default (observer(ResultsTable): ComponentType<ResultsTableArgs>);
