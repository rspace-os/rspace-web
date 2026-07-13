import SelectAllIcon from "@mui/icons-material/SelectAll";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import AdjustableHeadCell from "@/Inventory/components/Tables/AdjustableHeadCell";
import { translateAdjustableTableLabel } from "@/Inventory/components/Tables/adjustableTableLabels";
import { isSortable, sortProperties } from "@/stores/models/InventoryBaseRecord";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import useViewportDimensions from "../../../hooks/browser/useViewportDimensions";
import SearchContext from "../../../stores/contexts/Search";
import type { AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
import type { menuIDs } from "../../../util/menuIDs";
import ContextMenu from "../../components/ContextMenu/ContextMenu";
import type { SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";
import SortableProperty from "../../components/Tables/SortableProperty";

type TableHeadArgs = {
  selectedCount: number;
  onSelectOptions: Array<SplitButtonOption>;
  toggleAll: () => void;
  contextMenuId: (typeof menuIDs)[keyof typeof menuIDs];
};

function CustomTableHead({ selectedCount, onSelectOptions, toggleAll, contextMenuId }: TableHeadArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
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
  const mainProperty = sortProperties.find((p) => p.label === search.uiConfig.mainColumn);
  if (!mainProperty) {
    throw new Error("mainColumn is not a sortable property");
  }

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
                  title={t("search.resultsTable.selectAll")}
                  icon={<SelectAllIcon />}
                  onClick={toggleAll}
                  sx={{ p: 0.75 }}
                />
              </TableCell>
            )}
            {singleSelect && <TableCell variant="head">{t("search.resultsTable.select")}</TableCell>}
            <TableCell variant="head" padding="normal" sortDirection={order}>
              {isSortable(mainProperty.key) ? (
                <SortableProperty property={mainProperty} />
              ) : (
                <span>{translateAdjustableTableLabel(search.uiConfig.mainColumn, t)}</span>
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
          <TableCell variant="head" colSpan={cols} sx={{ p: "6px !important", pt: "0px !important" }}>
            <ContextMenu
              menuID={contextMenuId}
              selectedResults={search.selectedResults}
              onSelectOptions={onSelectOptions}
              forceDisabled={search.processingContextActions ? t("search.resultsTable.actionInProgress") : ""}
              paddingTop
              basketSearch={search.fetcher.basketSearch}
            />
          </TableCell>
        )}
      </TableRow>
    </TableHead>
  );
}

export default observer(CustomTableHead);
