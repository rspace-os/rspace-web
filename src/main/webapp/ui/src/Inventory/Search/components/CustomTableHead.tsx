import SelectAllIcon from "@mui/icons-material/SelectAll";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import AdjustableHeadCell from "@/Inventory/components/Tables/AdjustableHeadCell";
import { isSortable, sortProperties } from "@/stores/models/InventoryBaseRecord";
import * as ArrayUtils from "@/util/ArrayUtils";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import useViewportDimensions from "../../../hooks/browser/useViewportDimensions";
import SearchContext from "../../../stores/contexts/Search";
// biome-ignore lint/style/useImportType: initial biome migration
import { type AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
// biome-ignore lint/style/useImportType: initial biome migration
import { menuIDs } from "../../../util/menuIDs";
import ContextMenu from "../../components/ContextMenu/ContextMenu";
// biome-ignore lint/style/useImportType: initial biome migration
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";
import SortableProperty, { type SortProperty } from "../../components/Tables/SortableProperty";

type TableHeadArgs = {
  selectedCount: number;
  onSelectOptions: Array<SplitButtonOption>;
  toggleAll: () => void;
  contextMenuId: (typeof menuIDs)[keyof typeof menuIDs];
};

function CustomTableHead({ selectedCount, onSelectOptions, toggleAll, contextMenuId }: TableHeadArgs): React.ReactNode {
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

  const handleAdjustableColumnChange = (index: number) => (newColumn: AdjustableTableRowLabel) => {
    search.setAdjustableColumn(newColumn, index);
  };

  return (
    <TableHead>
      <TableRow>
        {selectedCount === 0 ? (
          <>
            {multiSelect && (
              <TableCell variant="head" sx={{ pl: 0.75, pr: 0, py: 0 }}>
                <IconButtonWithTooltip
                  title="Select all"
                  icon={<SelectAllIcon />}
                  onClick={toggleAll}
                  sx={{ p: 0.75 }}
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
          // biome-ignore lint/complexity/noUselessFragments: initial biome migration
          <>
            <TableCell variant="head" colSpan={cols} sx={{ p: "6px !important", pt: "0px !important" }}>
              <ContextMenu
                menuID={contextMenuId}
                selectedResults={search.selectedResults}
                onSelectOptions={onSelectOptions}
                forceDisabled={search.processingContextActions ? "Action In Progress" : ""}
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
