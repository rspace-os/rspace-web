import { Undo2Icon, XIcon } from "lucide-react";
import { createContext, type ReactNode, useContext, useId } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";

/** A one-line result of a table action, shown above the table until the user dismisses it. */
export type TableListAlert = {
  /** Pushing an alert with an existing id replaces it. */
  id: string;
  message: ReactNode;
  /** Optional controls related to the result, such as View details or Focus on calendar. */
  actions?: ReactNode;
  tone?: "default" | "success" | "warning" | "destructive";
  icon?: ReactNode;
  undo?: {
    label?: string;
    run: () => Promise<unknown> | unknown;
    /** The row to focus once the undo lands, such as a row the undo brings back. */
    focusRowId?: string;
    /** Replaces the alert's message when the undo fails. */
    describeError?: (error: unknown) => string;
  };
};

export type TableListAlertsApi = {
  push: (...alerts: TableListAlert[]) => void;
  dismiss: (id: string) => void;
};

export type AlertEntry = TableListAlert & {
  renderKey: number;
  status: "idle" | "undoing" | "failed";
  failure?: string;
};

const TableListAlertsContext = createContext<TableListAlertsApi | null>(null);

/** Emits alerts from any control rendered inside a TableList: row actions, selection actions, or toolbar controls. */
export function useTableListAlerts(): TableListAlertsApi {
  const api = useContext(TableListAlertsContext);
  if (!api) throw new Error("useTableListAlerts must be used inside a TableList");
  return api;
}

export function TableListAlertsProvider({ api, children }: { api: TableListAlertsApi; children: ReactNode }) {
  return <TableListAlertsContext.Provider value={api}>{children}</TableListAlertsContext.Provider>;
}

const toneClassName: Record<NonNullable<TableListAlert["tone"]>, string> = {
  default: "border-border bg-card",
  success: "border-green-700/40 bg-green-50 text-green-950 dark:bg-green-950/40 dark:text-green-100",
  warning: "border-amber-600/50 bg-amber-50 text-amber-950 dark:bg-amber-950/40 dark:text-amber-100",
  destructive: "border-destructive/50 bg-card text-destructive",
};

/** The alert stack. The live region stays mounted so an alert pushed into it is announced. */
export function TableListAlertStack({
  alerts,
  onUndo,
  onDismiss,
}: {
  alerts: readonly AlertEntry[];
  onUndo: (id: string) => void;
  onDismiss: (id: string) => void;
}) {
  const { t } = useTranslation("common");
  return (
    // A plain polite live region: announces added alerts without claiming the page's single "status" role.
    <div aria-live="polite" aria-relevant="additions text">
      {alerts.length > 0 ? (
        <ul aria-label={t("tableList.alerts.label")} className="mb-3 space-y-2 pt-3">
          {alerts.map((alert) => (
            <TableListAlertItem key={alert.renderKey} alert={alert} onUndo={onUndo} onDismiss={onDismiss} />
          ))}
        </ul>
      ) : null}
    </div>
  );
}

export type TableListAlertItemProps = {
  alert: TableListAlert & { status?: "idle" | "undoing" | "failed"; failure?: string };
  onUndo?: (id: string) => void;
  onDismiss: (id: string) => void;
};

export function TableListAlertItem({ alert, onUndo, onDismiss }: TableListAlertItemProps) {
  const { t } = useTranslation("common");
  const messageId = useId();
  const undoing = alert.status === "undoing";
  const failed = alert.status === "failed";
  const dismissLabel = t("tableList.alerts.dismiss");
  return (
    <li
      data-table-list-alert={alert.id}
      tabIndex={-1}
      aria-labelledby={messageId}
      className={cn(
        "flex flex-wrap items-center gap-x-3 gap-y-2 rounded-sm border px-4 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
        toneClassName[failed ? "destructive" : (alert.tone ?? "default")],
      )}
    >
      {alert.icon ? <span className="flex shrink-0 [&_svg]:size-4">{alert.icon}</span> : null}
      <p id={messageId} className="min-w-0 flex-1">
        {failed ? (alert.failure ?? t("tableList.alerts.undoFailed")) : alert.message}
      </p>
      {alert.actions ? <div className="flex flex-wrap items-center gap-2">{alert.actions}</div> : null}
      {alert.undo && !failed && onUndo ? (
        <Button
          type="button"
          size="sm"
          variant="outline"
          disabled={undoing}
          aria-busy={undoing}
          onClick={() => onUndo(alert.id)}
        >
          <Undo2Icon aria-hidden="true" data-icon="inline-start" />
          {undoing ? t("tableList.alerts.undoing") : (alert.undo.label ?? t("tableList.alerts.undo"))}
        </Button>
      ) : null}
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              aria-label={dismissLabel}
              disabled={undoing}
              onClick={() => onDismiss(alert.id)}
            />
          }
        >
          <XIcon aria-hidden="true" />
        </TooltipTrigger>
        <TooltipContent role="tooltip">{dismissLabel}</TooltipContent>
      </Tooltip>
    </li>
  );
}
