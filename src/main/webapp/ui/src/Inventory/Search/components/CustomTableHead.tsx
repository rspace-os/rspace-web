import React, { useContext } from "react";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import ContextMenu from "../../components/ContextMenu/ContextMenu";
import SearchContext from "../../../stores/contexts/Search";
import SelectAllIcon from "@mui/icons-material/SelectAll";
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import { menuIDs } from "../../../util/menuIDs";
import AdjustableHeadCell from "../../components/Tables/AdjustableHeadCell";
import SortableProperty, {
  type SortProperty,
} from "../../components/Tables/SortableProperty";
import { type AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
import {
  sortProperties,
  isSortable,
} from "../../../stores/models/InventoryBaseRecord";
import * as ArrayUtils from "../../../util/ArrayUtils";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";
import useViewportDimensions from "../../../hooks/browser/useViewportDimensions";

const useStyles = makeStyles()((theme) => ({
  iconCell: {
    padding: theme.spacing(0, 0, 0, 0.75),
  },
  iconButton: {
    padding: theme.spacing(0.75),
  },
  contextMenuCell: {
    padding: "6px !important",
    paddingTop: "0px !important",
  },
}));

type TableHeadArgs = {
  selectedCount: number;
  onSelectOptions: Array<SplitButtonOption>;
  toggleAll: () => void;
  contextMenuId: (typeof menuIDs)[keyof typeof menuIDs];
};

function CustomTableHead({
  selectedCount,
  onSelectOptions,
  toggleAll,
  contextMenuId,
}: TableHeadArgs): React.ReactNode {
  const { isViewportSmall, isViewportLarge } = useViewportDimensions();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { search } = useContext(SearchContext);
  const multiSelect = search.uiConfig.selectionMode === "MULTIPLE";
  const singleSelect = search.uiConfig.selectionMode === "SINGLE";
  const { order } = search.fetcher;

  let cols = 3;
  if (!isViewportSmall && isSingleColumnLayout) cols++;
  if (isViewportLarge && isSingleColumnLayout) cols++;

  /* this could be made adjustable too (e.g. name or global ID) */
  const mainProperty: SortProperty = ArrayUtils.find(
    (p) => p.label === search.uiConfig.mainColumn,
    sortProperties,
  ).orElseGet(() => {
    throw new Error("mainColumn is not a sortable property");
  });

  const handleAdjustableColumnChange =
    (index: number) => (newColumn: AdjustableTableRowLabel) => {
      search.setAdjustableColumn(newColumn, index);
    };

  const { classes } = useStyles();
  return (
    <TableHead>
      <TableRow>
        {selectedCount === 0 ? (
          <>
            {multiSelect && (
              <TableCell variant="head" className={classes.iconCell}>
                <IconButtonWithTooltip
                  title="Select all"
                  icon={<SelectAllIcon />}
                  onClick={toggleAll}
                  className={classes.iconButton}
                />
              </TableCell>
            )}
            {singleSelect && <TableCell variant="head">Select</TableCell>}
            <TableCell variant="head" padding="normal" sortDirection={order}>
              {isSortable(mainProperty.key) ? (
                <SortableProperty property={mainProperty} />
              ) : (
                <span>{search.uiConfig.mainColumn}</span>
              )}
            </TableCell>
            <AdjustableHeadCell
              options={search.adjustableColumnOptions}
              onChange={handleAdjustableColumnChange(0)}
              current={search.uiConfig.adjustableColumns[0]}
              sortableProperties={sortProperties}
            />
            {cols > 3 && (
              <AdjustableHeadCell
                options={search.adjustableColumnOptions}
                onChange={handleAdjustableColumnChange(1)}
                current={search.uiConfig.adjustableColumns[1]}
                sortableProperties={sortProperties}
              />
            )}
            {cols > 4 && (
              <AdjustableHeadCell
                options={search.adjustableColumnOptions}
                onChange={handleAdjustableColumnChange(2)}
                current={search.uiConfig.adjustableColumns[2]}
                sortableProperties={sortProperties}
              />
            )}
          </>
        ) : (
          <>
            <TableCell
              variant="head"
              colSpan={cols}
              className={classes.contextMenuCell}
            >
              <ContextMenu
                menuID={contextMenuId}
                selectedResults={search.selectedResults}
                onSelectOptions={onSelectOptions}
                forceDisabled={
                  search.processingContextActions ? "Action In Progress" : ""
                }
                paddingTop
                basketSearch={search.fetcher.basketSearch}
              />
            </TableCell>
          </>
        )}
      </TableRow>
    </TableHead>
  );
}

export default observer(CustomTableHead);
