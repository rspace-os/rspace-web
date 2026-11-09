import { isEqual } from "es-toolkit";
import { ArrowDownUpIcon, Columns3Icon, ListFilterIcon, RotateCcwIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/config";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import { Button } from "@/modules/common/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import type { TableListFeatures, TableListFilterButtons } from "../types";
import { toFirstPage } from "../utils/features";
import { CollectionSearchInput } from "./CollectionSearchInput";

export type TableListControlPanel = "filters" | "sorting" | "columns";

const remoteSearchDebounceMs = 300;

function CountBadge({ count, ariaHidden, spaced }: { count: number; ariaHidden?: true; spaced?: true }) {
  const { i18n } = useTranslation("common");
  return (
    <span aria-hidden={ariaHidden} className="ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background">
      {spaced ? " " : null}
      {formatNumber(count, i18n.resolvedLanguage ?? i18n.language)}
    </span>
  );
}

export function TableListToolbar<TDocument>({
  config,
  collectionLabel,
  features,
  clientSide,
  activePanel,
  controlPanelId,
  filterCount,
  filterButtons,
  hideFilterPanel = false,
  debounceSearch = false,
  onPanelChange,
  onReset,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  collectionLabel: string;
  features: TableListFeatures<TDocument>;
  clientSide?: boolean;
  activePanel: TableListControlPanel | null;
  controlPanelId: string;
  filterCount: number;
  filterButtons?: TableListFilterButtons;
  /** Hides the filter panel for a data source that only honours free-text search. */
  hideFilterPanel?: boolean;
  /** Debounce client-side search when its value also drives remote work. */
  debounceSearch?: boolean;
  onPanelChange: (panel: TableListControlPanel) => void;
  onReset: () => void;
}) {
  const { t } = useTranslation("common");
  const filtering = features.filtering;
  const [searchResetSignal, setSearchResetSignal] = useState(0);
  const resetLabel = t("tableList.actions.resetToDefaults");
  const visibleColumns = features.columns === false ? config.defaultColumns : features.columns.value;
  const columnsChanged = !isEqual(visibleColumns, config.defaultColumns);
  const defaultSorting = config.defaultSort ?? [];
  const sorting = features.sorting === false ? defaultSorting : features.sorting.value;
  const sortingCount = sorting.length;
  const sortingChanged = !isEqual(sorting, defaultSorting);
  const filtersChanged = filtering !== false && (filtering.value.search !== "" || filtering.value.expression !== null);
  const viewChanged =
    filtersChanged ||
    sortingChanged ||
    columnsChanged ||
    filterButtons?.hasChanges ||
    filterButtons?.buttons.some(({ pressed }) => pressed);
  const visibleColumnCount = visibleColumns.length;
  const listableColumnCount = config.fields.filter((field) => field.list !== false).length;

  if (filtering === false && features.sorting === false && features.columns === false && !filterButtons) {
    return null;
  }

  return (
    <div
      role="toolbar"
      aria-label={collectionLabel}
      className={cn(
        "flex flex-col flex-wrap gap-2 border-b py-2",
        !filterButtons?.controlsOnSeparateRow && "lg:flex-row lg:items-center",
      )}
    >
      {filtering !== false && (config.listSearchableFields?.length ?? 0) > 0 ? (
        <CollectionSearchInput
          key={searchResetSignal}
          value={filtering.value.search}
          collectionLabel={collectionLabel}
          debounceMs={clientSide && !debounceSearch ? 0 : remoteSearchDebounceMs}
          onCommit={(search) => {
            filtering.onChange({ ...filtering.value, search });
            toFirstPage(features.pagination);
          }}
        />
      ) : (
        <div className="flex-1" />
      )}
      <div className="flex min-w-0 flex-wrap items-center gap-2">
        {filterButtons?.controls}
        {filterButtons ? (
          <fieldset className="flex min-w-0 flex-wrap items-center gap-2">
            <legend className="sr-only">{filterButtons.legend}</legend>
            {filterButtons.buttons.map((button) => (
              <Button
                key={button.id}
                type="button"
                aria-pressed={button.pressed}
                disabled={button.disabled}
                variant={button.pressed ? "secondary" : "outline"}
                onClick={button.onClick}
              >
                {button.icon}
                {button.label}
                {button.count === undefined ? null : <CountBadge count={button.count} ariaHidden />}
              </Button>
            ))}
          </fieldset>
        ) : null}
        {filtering !== false && !hideFilterPanel ? (
          <Button
            aria-label={
              filterCount
                ? t("tableList.filters.applied", { count: filterCount })
                : t("tableList.filters.noneApplied", { count: filterCount })
            }
            aria-controls={controlPanelId}
            aria-expanded={activePanel === "filters"}
            // Lets rows that remove a filter (such as a filter chip) hand focus back to this control.
            data-table-list-filters
            variant={activePanel === "filters" || filterCount > 0 ? "secondary" : "outline"}
            onClick={() => onPanelChange("filters")}
          >
            <ListFilterIcon aria-hidden="true" data-icon="inline-start" />
            {t("tableList.toolbar.filters")}
            {filterCount > 0 ? <CountBadge count={filterCount} spaced /> : null}
          </Button>
        ) : null}
        {features.sorting !== false ? (
          <Button
            aria-label={
              sortingCount > 0
                ? t("tableList.sorting.applied", {
                    count: sortingCount,
                  })
                : undefined
            }
            aria-controls={controlPanelId}
            aria-expanded={activePanel === "sorting"}
            variant={activePanel === "sorting" || sortingCount > 0 ? "secondary" : "outline"}
            onClick={() => onPanelChange("sorting")}
          >
            <ArrowDownUpIcon aria-hidden="true" data-icon="inline-start" />
            {t("tableList.toolbar.sorting")}
            {sortingCount > 0 ? <CountBadge count={sortingCount} spaced /> : null}
          </Button>
        ) : null}
        {features.columns !== false ? (
          <Button
            // Only named explicitly once the count matters; otherwise the button text is the name.
            aria-label={
              columnsChanged
                ? t("tableList.columns.customised", { count: visibleColumnCount, total: listableColumnCount })
                : undefined
            }
            aria-controls={controlPanelId}
            aria-expanded={activePanel === "columns"}
            variant={activePanel === "columns" || columnsChanged ? "secondary" : "outline"}
            onClick={() => onPanelChange("columns")}
          >
            <Columns3Icon aria-hidden="true" data-icon="inline-start" />
            {t("tableList.toolbar.columns")}
            {columnsChanged ? <CountBadge count={visibleColumnCount} /> : null}
          </Button>
        ) : null}
        {viewChanged ? (
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  aria-label={resetLabel}
                  size="icon"
                  variant="ghost"
                  onClick={() => {
                    if (filtering !== false) filtering.onChange({ search: "", expression: null });
                    if (features.sorting !== false) features.sorting.onChange(defaultSorting);
                    if (features.columns !== false) features.columns.onChange(config.defaultColumns);
                    toFirstPage(features.pagination);
                    filterButtons?.onReset();
                    setSearchResetSignal((current) => current + 1);
                    onReset();
                  }}
                />
              }
            >
              <RotateCcwIcon aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent side="bottom" className="rounded-sm">
              {resetLabel}
            </TooltipContent>
          </Tooltip>
        ) : null}
      </div>
    </div>
  );
}
