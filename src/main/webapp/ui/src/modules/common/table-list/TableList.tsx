import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { TooltipProvider } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import { topLevelFilterCount } from "./components/filters/TableListFilters";
import { TableListControlPanel } from "./components/TableListControlPanel";
import { TableListDataTable } from "./components/TableListDataTable";
import { TableListHeader } from "./components/TableListHeader";
import { type TableListControlPanel as ControlPanel, TableListToolbar } from "./components/TableListToolbar";
import type { TableListProps } from "./tableListState";
import { useTableListQueryString } from "./useTableListQueryString";

export type {
  TableListProps,
  TableListRowActions,
  TableListSelection,
  TableListSelectionContext,
} from "./tableListState";

function TableListContent<TDocument extends Record<string, unknown>>({
  config,
  rows,
  getRowId,
  features,
  clientSide,
  status = "idle",
  error,
  onRowOpen,
  onCreate,
  createAction,
  createLabel,
  uiColumns,
  rowActions,
  selection,
  variant = "card",
  reserveEmptyRows,
  onSelectRuntimeField,
  runtimeFieldDefinitions,
}: TableListProps<TDocument>) {
  const { t } = useTranslation("common");
  const [activePanel, setActivePanel] = useState<ControlPanel | null>(null);
  const [activeRowAction, setActiveRowAction] = useState<{ actionId: string; rowId: string } | null>(null);
  const collectionLabel = t(config.labels.pluralKey as never);
  const filterCount = features.filtering === false ? 0 : topLevelFilterCount(features.filtering.value.expression);
  const tableUiColumns = useMemo(() => {
    if (!rowActions) return uiColumns;
    return [
      ...(uiColumns ?? []),
      {
        ...rowActions,
        renderCell: (row: TDocument) =>
          rowActions.renderCell({
            row,
            activate: (actionId) => setActiveRowAction({ actionId, rowId: getRowId(row) }),
          }),
      },
    ];
  }, [getRowId, rowActions, uiColumns]);
  const activeRow = activeRowAction ? rows.find((row) => getRowId(row) === activeRowAction.rowId) : undefined;
  return (
    <TooltipProvider delay={250}>
      <section className="text-foreground [&_[data-slot=badge]]:rounded-sm [&_[data-slot=button]]:rounded-sm [&_[data-slot=input]]:rounded-sm [&_svg]:size-3.5!">
        <div className={cn(variant === "card" && "mb-5")}>
          <TableListHeader
            config={config}
            collectionLabel={collectionLabel}
            onCreate={onCreate}
            createAction={createAction}
            createLabel={createLabel}
            divided={variant === "transparent"}
          />
        </div>
        <div className={cn(variant === "card" && "rounded-sm border bg-card px-3")}>
          <TableListToolbar
            config={config}
            collectionLabel={collectionLabel}
            features={features}
            clientSide={clientSide}
            activePanel={activePanel}
            filterCount={filterCount}
            onPanelChange={setActivePanel}
            onReset={() => setActivePanel(null)}
          />
          <TableListControlPanel
            onSelectRuntimeField={onSelectRuntimeField}
            runtimeFieldDefinitions={runtimeFieldDefinitions}
            activePanel={activePanel}
            config={config}
            features={features}
            onClose={() => setActivePanel(null)}
          />
          <TableListDataTable
            config={config}
            rows={rows}
            getRowId={getRowId}
            features={features}
            clientSide={clientSide}
            status={status}
            error={error}
            onRowOpen={onRowOpen}
            collectionLabel={collectionLabel}
            reserveEmptyRows={reserveEmptyRows}
            uiColumns={tableUiColumns}
            selection={selection}
          />
        </div>
      </section>
      {activeRowAction && activeRow && rowActions
        ? rowActions.renderInteraction({
            actionId: activeRowAction.actionId,
            row: activeRow,
            close: () => setActiveRowAction(null),
          })
        : null}
    </TooltipProvider>
  );
}

function QueryStringTableList<TDocument extends Record<string, unknown>>(props: TableListProps<TDocument>) {
  const features = useTableListQueryString(
    props.config,
    props.features,
    typeof props.queryString === "object" ? props.queryString : true,
  );
  return <TableListContent {...props} features={features} />;
}

export function TableList<TDocument extends Record<string, unknown>>(props: TableListProps<TDocument>) {
  return props.queryString === false ? <TableListContent {...props} /> : <QueryStringTableList {...props} />;
}
