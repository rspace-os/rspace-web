import { onlineManager } from "@tanstack/react-query";
import type { Namespace } from "i18next";
import { use, useCallback, useId, useImperativeHandle, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { useTranslation } from "react-i18next";
import { TooltipProvider } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import { useTableListAlertsState } from "../hooks/useTableListAlertsState";
import { TableListPageRegistryContext } from "../TableListPageRegistry";
import type { TableListProps } from "../types";
import { topLevelFilterCount } from "../utils/filterDrafts";
import { RestoredViewIssue } from "./RestoredViewIssue";
import { TableListAlertStack, TableListAlertsProvider } from "./TableListAlerts";
import { TableListControlPanel } from "./TableListControlPanel";
import { TableListDataTable } from "./TableListDataTable";
import { TableListHeader } from "./TableListHeader";
import { type TableListControlPanel as ControlPanel, TableListToolbar } from "./TableListToolbar";

const subscribeOnline = (onChange: () => void) => onlineManager.subscribe(onChange);

const getOnline = () => onlineManager.isOnline();

export function TableListLayout<TDocument extends Record<string, unknown>>({
  config,
  rows,
  getRowId,
  features,
  clientSide,
  status = "idle",
  error,
  onRowOpen,
  createAction,
  headingClassName,
  uiColumns,
  rowActions,
  selection,
  filterButtons,
  hideFilterPanel,
  debounceSearch,
  presentations,
  renderRows,
  renderRowsWhenEmpty,
  emptyDescription,
  variant = "card",
  hideHeader = false,
  reserveEmptyRows,
  runtimeFields,
  authScope,
  restoredViewIssue,
  alertsRef,
  persistenceIdentity,
}: TableListProps<TDocument>) {
  const { t } = useTranslation<Namespace>("common");
  const online = useSyncExternalStore(subscribeOnline, getOnline, getOnline);
  const [activePanel, setActivePanel] = useState<ControlPanel | null>(null);
  const [activeRowAction, setActiveRowAction] = useState<{ actionId: string; rowId: string } | null>(null);
  const rootRef = useRef<HTMLElement>(null);
  const controlPanelId = useId();
  // Read through a ref so a caller's inline getRowId does not rebuild the columns (which re-mounts their cells).
  const getRowIdRef = useRef(getRowId);
  getRowIdRef.current = getRowId;
  const alerts = useTableListAlertsState({ root: rootRef });
  const { cancelWatchers } = alerts;
  useImperativeHandle(alertsRef, () => alerts.api, [alerts.api]);
  const registry = use(TableListPageRegistryContext);
  const slug = config.slug;
  const parameterPrefix = persistenceIdentity?.parameterPrefix;
  const storageKey = persistenceIdentity?.storageKey;
  // Stable per identity: a new callback would re-run the cleanup, cancelling a focus watch that an alert just started.
  const attachRoot = useCallback(
    (node: HTMLElement) => {
      rootRef.current = node;
      const release =
        parameterPrefix === undefined || storageKey === undefined
          ? undefined
          : registry.register(controlPanelId, slug, [
              { kind: "parameterPrefix", value: parameterPrefix },
              { kind: "storageKey", value: storageKey },
            ]);
      return () => {
        release?.();
        cancelWatchers();
        rootRef.current = null;
      };
    },
    [cancelWatchers, controlPanelId, parameterPrefix, registry, slug, storageKey],
  );
  const collectionLabel = t(config.labels.pluralKey);
  const filterCount = features.filtering === false ? 0 : topLevelFilterCount(features.filtering.value.expression);
  const tableUiColumns = useMemo(() => {
    if (!rowActions) return uiColumns;
    return [
      ...(uiColumns ?? []),
      {
        ...rowActions,
        card: { ...rowActions.card, placement: "footer" as const },
        renderCell: (row: TDocument) => (
          // Lets an alert's undo return focus to the row it brings back.
          <div data-table-list-row-actions={getRowIdRef.current(row)} className="contents">
            {rowActions.renderCell({
              row,
              activate: (actionId) => setActiveRowAction({ actionId, rowId: getRowIdRef.current(row) }),
            })}
          </div>
        ),
      },
    ];
  }, [rowActions, uiColumns]);
  const activeRow = activeRowAction ? rows.find((row) => getRowId(row) === activeRowAction.rowId) : undefined;
  return (
    <TooltipProvider delay={250}>
      <TableListAlertsProvider api={alerts.api}>
        <section
          ref={attachRoot}
          className="text-foreground [&_[data-inventory-item]]:p-0 [&_[data-slot=badge]]:rounded-sm [&_[data-slot=button]]:rounded-sm [&_[data-slot=input]]:rounded-sm [&_svg]:size-3.5!"
        >
          {hideHeader ? null : (
            <div className={cn(variant === "card" && "mb-5")}>
              <TableListHeader
                config={config}
                collectionLabel={collectionLabel}
                createAction={createAction}
                headingClassName={headingClassName}
                divided={variant === "transparent"}
              />
            </div>
          )}
          <div className={cn(variant === "card" && "rounded-sm border bg-card px-3")}>
            <TableListAlertStack alerts={alerts.alerts} onUndo={alerts.undo} onDismiss={alerts.api.dismiss} />
            {clientSide === false && !online ? (
              <p role="status" className="py-3 text-sm text-muted-foreground">
                {t("tableList.offline")}
              </p>
            ) : null}
            {restoredViewIssue ? (
              <RestoredViewIssue issue={restoredViewIssue} />
            ) : (
              <>
                <TableListToolbar
                  config={config}
                  collectionLabel={collectionLabel}
                  features={features}
                  clientSide={clientSide}
                  activePanel={activePanel}
                  controlPanelId={controlPanelId}
                  filterCount={filterCount}
                  filterButtons={filterButtons}
                  hideFilterPanel={hideFilterPanel}
                  debounceSearch={debounceSearch}
                  onPanelChange={setActivePanel}
                  onReset={() => setActivePanel(null)}
                />
                <TableListControlPanel
                  runtimeFields={runtimeFields}
                  authScope={authScope}
                  activePanel={activePanel}
                  controlPanelId={controlPanelId}
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
                  presentations={presentations}
                  renderRows={renderRows}
                  renderRowsWhenEmpty={renderRowsWhenEmpty}
                  emptyDescription={emptyDescription}
                />
              </>
            )}
          </div>
        </section>
        {activeRowAction && activeRow && rowActions
          ? rowActions.renderInteraction({
              actionId: activeRowAction.actionId,
              row: activeRow,
              close: () => setActiveRowAction(null),
            })
          : null}
      </TableListAlertsProvider>
    </TooltipProvider>
  );
}
