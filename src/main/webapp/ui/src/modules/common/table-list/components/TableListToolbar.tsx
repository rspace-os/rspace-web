import { ArrowDownUpIcon, Columns3Icon, ListFilterIcon, RotateCcwIcon, SearchIcon, XIcon } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import { Button } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import type { TableListFeatures, TableListFilterButtons } from "../tableListState";

export type TableListControlPanel = "filters" | "sorting" | "columns";

const remoteSearchDebounceMs = 300;

function SearchRecordsInput({
  value,
  collectionLabel,
  debounceMs,
  resetSignal,
  onCommit,
}: {
  value: string;
  collectionLabel: string;
  debounceMs: number;
  resetSignal: number;
  onCommit: (value: string) => void;
}) {
  const { t } = useTranslation("common");
  const [draft, setDraft] = useState(value);
  const timeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const currentValue = useRef(value);
  const commit = useRef(onCommit);
  currentValue.current = value;
  commit.current = onCommit;

  const cancel = useCallback(() => {
    if (timeout.current !== null) clearTimeout(timeout.current);
    timeout.current = null;
  }, []);

  useEffect(() => cancel, [cancel]);
  useEffect(() => {
    cancel();
    setDraft(value);
  }, [cancel, resetSignal, value]);

  const commitNow = (next: string) => {
    cancel();
    if (next !== currentValue.current) commit.current(next);
  };

  const change = (next: string) => {
    setDraft(next);
    cancel();
    if (debounceMs === 0) {
      if (next !== currentValue.current) commit.current(next);
      return;
    }
    timeout.current = setTimeout(() => {
      timeout.current = null;
      if (next !== currentValue.current) commit.current(next);
    }, debounceMs);
  };

  return (
    <div className="relative min-w-56 flex-1">
      <SearchIcon
        aria-hidden="true"
        className="absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted-foreground"
      />
      <Input
        aria-label={t("tableList.search.label", { collection: collectionLabel })}
        className="h-8 bg-background pr-9 pl-9"
        placeholder={t("tableList.search.placeholder")}
        value={draft}
        onChange={(event) => change(event.target.value)}
        onKeyDown={(event) => {
          if (event.key !== "Enter") return;
          event.preventDefault();
          commitNow(draft);
        }}
      />
      {draft ? (
        <button
          type="button"
          aria-label={t("tableList.search.clear")}
          className="absolute top-1/2 right-3 -translate-y-1/2 rounded-sm text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
          onClick={() => {
            setDraft("");
            commitNow("");
          }}
        >
          <XIcon aria-hidden="true" />
        </button>
      ) : null}
    </div>
  );
}

export function TableListToolbar<TDocument>({
  config,
  collectionLabel,
  features,
  clientSide,
  activePanel,
  filterCount,
  filterButtons,
  hideFilterPanel = false,
  onPanelChange,
  onReset,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  collectionLabel: string;
  features: TableListFeatures<TDocument>;
  clientSide?: boolean;
  activePanel: TableListControlPanel | null;
  filterCount: number;
  filterButtons?: TableListFilterButtons;
  /** Hides the filter panel for a data source that only honours free-text search. */
  hideFilterPanel?: boolean;
  onPanelChange: (panel: TableListControlPanel) => void;
  onReset: () => void;
}) {
  const { t } = useTranslation("common");
  const [searchResetSignal, setSearchResetSignal] = useState(0);
  const resetLabel = t("tableList.actions.resetToDefaults");
  const visibleColumns = features.columns === false ? config.defaultColumns : features.columns.value;
  const columnsChanged =
    visibleColumns.length !== config.defaultColumns.length ||
    visibleColumns.some((field, index) => field !== config.defaultColumns[index]);
  const defaultSorting = config.defaultSort ?? [];
  const sorting = features.sorting === false ? defaultSorting : features.sorting.value;
  const sortingCount = sorting.length;
  const sortingChanged =
    sorting.length !== defaultSorting.length ||
    sorting.some(
      (rule, index) =>
        rule.field !== defaultSorting[index]?.field || rule.direction !== defaultSorting[index]?.direction,
    );
  const filtersChanged =
    features.filtering !== false &&
    (features.filtering.value.search !== "" || features.filtering.value.expression !== null);
  const viewChanged =
    filtersChanged || sortingChanged || columnsChanged || filterButtons?.buttons.some(({ pressed }) => pressed);
  const visibleColumnCount = visibleColumns.length;
  const listableColumnCount = config.fields.filter((field) => field.list !== false).length;

  if (features.filtering === false && features.sorting === false && features.columns === false && !filterButtons) {
    return null;
  }

  return (
    <div className="flex flex-col flex-wrap gap-2 border-b py-2 lg:flex-row lg:items-center">
      {features.filtering !== false && (config.listSearchableFields?.length ?? 0) > 0 ? (
        <SearchRecordsInput
          value={features.filtering.value.search}
          collectionLabel={collectionLabel}
          debounceMs={clientSide ? 0 : remoteSearchDebounceMs}
          resetSignal={searchResetSignal}
          onCommit={(search) => {
            if (features.filtering !== false) {
              features.filtering.onChange({ ...features.filtering.value, search });
            }
            if (features.pagination !== false) {
              features.pagination.onChange({ ...features.pagination.value, pageIndex: 0 });
            }
          }}
        />
      ) : (
        <div className="flex-1" />
      )}
      <div className="flex min-w-0 flex-wrap items-center gap-2">
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
                {button.count === undefined ? null : (
                  <span aria-hidden="true" className="ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background">
                    {button.count}
                  </span>
                )}
              </Button>
            ))}
          </fieldset>
        ) : null}
        {features.filtering !== false && !hideFilterPanel ? (
          <Button
            aria-label={
              filterCount
                ? t("tableList.filters.applied", { count: filterCount })
                : t("tableList.filters.noneApplied", { count: filterCount })
            }
            aria-controls="table-list-control-panel"
            aria-expanded={activePanel === "filters"}
            variant={activePanel === "filters" || filterCount > 0 ? "secondary" : "outline"}
            onClick={() => onPanelChange("filters")}
          >
            <ListFilterIcon aria-hidden="true" data-icon="inline-start" />
            {t("tableList.toolbar.filters")}
            {filterCount > 0 ? (
              <span className="ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background">{filterCount}</span>
            ) : null}
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
            aria-controls="table-list-control-panel"
            aria-expanded={activePanel === "sorting"}
            variant={activePanel === "sorting" || sortingCount > 0 ? "secondary" : "outline"}
            onClick={() => onPanelChange("sorting")}
          >
            <ArrowDownUpIcon aria-hidden="true" data-icon="inline-start" />
            {t("tableList.toolbar.sorting")}
            {sortingCount > 0 ? (
              <span className="ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background">{sortingCount}</span>
            ) : null}
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
            aria-controls="table-list-control-panel"
            aria-expanded={activePanel === "columns"}
            variant={activePanel === "columns" || columnsChanged ? "secondary" : "outline"}
            onClick={() => onPanelChange("columns")}
          >
            <Columns3Icon aria-hidden="true" data-icon="inline-start" />
            {t("tableList.toolbar.columns")}
            {columnsChanged ? (
              <span className="ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background">
                {visibleColumnCount}
              </span>
            ) : null}
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
                    if (features.filtering !== false) features.filtering.onChange({ search: "", expression: null });
                    if (features.sorting !== false) features.sorting.onChange(defaultSorting);
                    if (features.columns !== false) features.columns.onChange(config.defaultColumns);
                    if (features.pagination !== false)
                      features.pagination.onChange({ ...features.pagination.value, pageIndex: 0 });
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
