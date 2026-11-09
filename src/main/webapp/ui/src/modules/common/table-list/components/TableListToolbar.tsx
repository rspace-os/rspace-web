import { isEqual } from "es-toolkit";
import { ArrowDownUpIcon, Columns3Icon, ListFilterIcon, RotateCcwIcon } from "lucide-react";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/config";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import { Button } from "@/modules/common/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/modules/common/ui/popover";
import { Separator } from "@/modules/common/ui/separator";
import { Switch } from "@/modules/common/ui/switch";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import type { TableListFeatures, TableListFilterButtons } from "../types";
import { toFirstPage } from "../utils/features";
import { CollectionSearchInput } from "./CollectionSearchInput";

export type TableListControlPanel = "filters" | "sorting" | "columns";

const remoteSearchDebounceMs = 300;

function CountBadge({
  count,
  ariaHidden,
  spaced,
  className = "ml-0.5",
}: {
  count: number;
  ariaHidden?: true;
  spaced?: true;
  className?: string;
}) {
  const { i18n } = useTranslation("common");
  return (
    <span
      aria-hidden={ariaHidden}
      className={cn(className, "rounded-sm bg-foreground px-1 text-[10px] text-background")}
    >
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
  resetView,
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
  resetView?: () => void;
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
  const filterPanelAvailable = filtering !== false && !hideFilterPanel;
  const quickFiltersInMenu = filterButtons?.presentation === "menu" && filterPanelAvailable;

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
      <div className={cn("flex min-w-0 flex-wrap items-center gap-2", filterButtons?.align === "end" && "justify-end")}>
        {filterButtons?.controls}
        {filterButtons && !quickFiltersInMenu ? (
          <fieldset
            className={cn("flex min-w-0 flex-wrap items-center gap-2", filterButtons.align === "end" && "justify-end")}
          >
            <legend className="sr-only">{filterButtons.legend}</legend>
            {filterButtons.buttons.map((button) => (
              <FilterButton key={button.id} button={button} />
            ))}
          </fieldset>
        ) : null}
        {filterButtons && quickFiltersInMenu ? (
          <FiltersMenu
            filterButtons={filterButtons}
            filterCount={filterCount}
            panelOpen={activePanel === "filters"}
            controlPanelId={controlPanelId}
            onOpenPanel={() => onPanelChange("filters")}
          />
        ) : null}
        {filterPanelAvailable && !quickFiltersInMenu ? (
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
                    if (resetView) {
                      resetView();
                    } else {
                      if (filtering !== false) filtering.onChange({ search: "", expression: null });
                      if (features.sorting !== false) features.sorting.onChange(defaultSorting);
                      if (features.columns !== false) features.columns.onChange(config.defaultColumns);
                      toFirstPage(features.pagination);
                      filterButtons?.onReset();
                    }
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

/** The Filters button of the `menu` presentation: quick filters as switches, and the way into the filter panel. */
function FiltersMenu({
  filterButtons,
  filterCount,
  panelOpen,
  controlPanelId,
  onOpenPanel,
}: {
  filterButtons: TableListFilterButtons;
  filterCount: number;
  panelOpen: boolean;
  controlPanelId: string;
  onOpenPanel: () => void;
}) {
  const { t } = useTranslation("common");
  const [open, setOpen] = useState(false);
  const appliedCount = filterButtons.buttons.filter(({ pressed }) => pressed).length + filterCount;
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <Button
            aria-label={
              appliedCount
                ? t("tableList.filters.applied", { count: appliedCount })
                : t("tableList.filters.noneApplied", { count: appliedCount })
            }
            // Lets rows that remove a filter (such as a filter chip) hand focus back to this control.
            data-table-list-filters
            variant={open || panelOpen || appliedCount > 0 ? "secondary" : "outline"}
          />
        }
      >
        <ListFilterIcon aria-hidden="true" data-icon="inline-start" />
        {t("tableList.toolbar.filters")}
        {appliedCount > 0 ? <CountBadge count={appliedCount} spaced /> : null}
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 gap-3 p-3">
        <fieldset className="flex flex-col gap-3">
          <legend className="mb-3 text-xs font-medium text-muted-foreground">{filterButtons.legend}</legend>
          {filterButtons.buttons.map((button) => (
            <FilterSwitch key={button.id} button={button} />
          ))}
        </fieldset>
        <Separator />
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs text-muted-foreground">
            {t("tableList.filters.panelSummary", { count: filterCount })}
          </span>
          <Button
            type="button"
            size="sm"
            variant="outline"
            aria-controls={controlPanelId}
            aria-expanded={panelOpen}
            onClick={() => {
              setOpen(false);
              onOpenPanel();
            }}
          >
            {t("tableList.filters.editFilters")}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

function FilterSwitch({ button }: { button: TableListFilterButtons["buttons"][number] }) {
  const id = useId();
  return (
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0 text-sm">
        {/* The popover renders outside the table's section, so size icons as its toolbar does. */}
        <span id={`${id}-label`} className="flex items-center gap-1.5 font-medium [&_svg]:size-3.5">
          {button.icon}
          {button.label}
          {button.count === undefined ? null : <CountBadge count={button.count} ariaHidden className="" />}
        </span>
        {button.description ? (
          <span id={`${id}-description`} className="block text-xs text-muted-foreground">
            {button.description}
          </span>
        ) : null}
      </div>
      <Switch
        checked={button.pressed}
        disabled={button.disabled}
        aria-labelledby={`${id}-label`}
        aria-describedby={button.description ? `${id}-description` : undefined}
        onCheckedChange={button.onClick}
      />
    </div>
  );
}

function FilterButton({ button }: { button: TableListFilterButtons["buttons"][number] }) {
  const descriptionId = useId();
  const props = {
    type: "button",
    "aria-pressed": button.pressed,
    disabled: button.disabled,
    variant: button.pressed ? "secondary" : "outline",
    onClick: button.onClick,
  } as const;
  const content = (
    <>
      {button.icon}
      {button.label}
      {button.count === undefined ? null : <CountBadge count={button.count} ariaHidden />}
    </>
  );
  if (!button.description) return <Button {...props}>{content}</Button>;
  return (
    <Tooltip>
      <TooltipTrigger render={<Button {...props} aria-describedby={descriptionId} />}>{content}</TooltipTrigger>
      <TooltipContent side="bottom" className="rounded-sm">
        {button.description}
      </TooltipContent>
      {/* Base UI tooltips are visual only, so screen readers get the description through aria-describedby. */}
      <span id={descriptionId} className="sr-only">
        {button.description}
      </span>
    </Tooltip>
  );
}
