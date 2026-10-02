import { Undo2Icon, XIcon } from "lucide-react";
import {
  createContext,
  type ReactNode,
  type RefObject,
  useCallback,
  useContext,
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";

/** A one-line result of a table action, shown above the table until the user dismisses it. */
export type TableListAlert = {
  /** Pushing an alert with an existing id replaces it. */
  id: string;
  message: ReactNode;
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

type AlertEntry = TableListAlert & { status: "idle" | "undoing" | "failed"; failure?: string };

const TableListAlertsContext = createContext<TableListAlertsApi | null>(null);

/** Emits alerts from any control rendered inside a TableList: row actions, selection actions, or toolbar controls. */
export function useTableListAlerts(): TableListAlertsApi {
  const api = useContext(TableListAlertsContext);
  if (!api) throw new Error("useTableListAlerts must be used inside a TableList");
  return api;
}

const focusableSelector = "a[href], button:not(:disabled), input:not(:disabled), [tabindex]:not([tabindex='-1'])";

/** Quotes a value for an attribute selector. */
function attributeValue(value: string): string {
  return `"${value.replace(/["\\]/g, "\\$&")}"`;
}

/**
 * The first candidate the page shows; the table and card presentations both render, one hidden by CSS.
 * Without a layout engine (jsdom) nothing has client rects, so the first candidate stands in.
 */
function firstShown<TElement extends Element>(candidates: readonly TElement[]): TElement | undefined {
  const shown = candidates.find((element) => element.getClientRects().length > 0);
  return shown ?? (document.body.getClientRects().length === 0 ? candidates[0] : undefined);
}

/** The first visible control in a row's actions; the table and card presentations both render, one hidden by CSS. */
export function rowActionsFocusTarget(root: Element, rowId: string): HTMLElement | undefined {
  return firstShown(
    [...root.querySelectorAll(`[data-table-list-row-actions=${attributeValue(rowId)}]`)].flatMap((actions) => [
      ...actions.querySelectorAll<HTMLElement>(focusableSelector),
    ]),
  );
}

/**
 * Focuses what `find` returns. A table can render a row's controls once more after new rows commit,
 * replacing the focused element, and removal does not blur in every engine; so for a moment, follow
 * the target if focus has fallen back to the page.
 */
function focusAndFollow(root: Element, find: () => HTMLElement | undefined) {
  const target = find();
  target?.focus();
  if (!target) return;
  const observer = new MutationObserver(() => {
    if (target.isConnected) return;
    observer.disconnect();
    if (document.activeElement === null || document.activeElement === document.body) find()?.focus();
  });
  observer.observe(root, { childList: true, subtree: true });
  window.setTimeout(() => observer.disconnect(), 1000);
}

function focusIsLost(): boolean {
  return document.activeElement === null || document.activeElement === document.body;
}

/**
 * TableList-owned alert state. Returned `api` is stable, so an action may push from a callback that
 * outlives its row, such as a dialog confirming a cancellation that removes the row.
 */
export function useTableListAlertsState({
  root,
  rowIds,
}: {
  root: RefObject<HTMLElement | null>;
  rowIds: readonly string[];
}) {
  const [alerts, setAlerts] = useState<readonly AlertEntry[]>([]);
  const [focusRequest, setFocusRequest] = useState<
    | { kind: "alert"; id: string }
    | { kind: "alertIfFocusLost"; id: string }
    | { kind: "row"; rowId: string; until: number }
    | { kind: "fallback" }
    | null
  >(null);
  const alertsRef = useRef(alerts);
  useLayoutEffect(() => {
    alertsRef.current = alerts;
  }, [alerts]);

  const push = useCallback((...next: TableListAlert[]) => {
    if (next.length === 0) return;
    setAlerts((current) => {
      const ids = new Set(next.map(({ id }) => id));
      // Newest first, directly above the table.
      return [
        ...next.toReversed().map((alert) => ({ ...alert, status: "idle" as const })),
        ...current.filter(({ id }) => !ids.has(id)),
      ];
    });
    // Only take focus once the control that acted has gone, so a persistent toolbar or bulk-action button keeps it.
    setFocusRequest({ kind: "alertIfFocusLost", id: next[next.length - 1].id });
  }, []);

  const dismiss = useCallback((id: string) => {
    const current = alertsRef.current;
    const index = current.findIndex((alert) => alert.id === id);
    const remaining = current.filter((alert) => alert.id !== id);
    setAlerts(remaining);
    const neighbour = remaining[Math.min(Math.max(index, 0), remaining.length - 1)];
    setFocusRequest(neighbour ? { kind: "alert", id: neighbour.id } : { kind: "fallback" });
  }, []);

  const undo = useCallback(async (id: string) => {
    const alert = alertsRef.current.find((entry) => entry.id === id);
    if (!alert?.undo || alert.status === "undoing") return;
    setAlerts((current) => current.map((entry) => (entry.id === id ? { ...entry, status: "undoing" } : entry)));
    try {
      await alert.undo.run();
      setAlerts((current) => current.filter((entry) => entry.id !== id));
      setFocusRequest(
        alert.undo.focusRowId
          ? { kind: "row", rowId: alert.undo.focusRowId, until: Date.now() + 5000 }
          : { kind: "fallback" },
      );
    } catch (error) {
      setAlerts((current) =>
        current.map((entry) =>
          entry.id === id ? { ...entry, status: "failed", failure: alert.undo?.describeError?.(error) } : entry,
        ),
      );
      setFocusRequest({ kind: "alert", id });
    }
  }, []);

  // Runs after the stack or the rows re-render, so focus lands on what is now shown.
  useEffect(() => {
    const container = root.current;
    if (!focusRequest || !container) return;
    // With no alert left and no row to return to, focus the table's Filters control, else its search.
    const fallback = () =>
      firstShown([...container.querySelectorAll<HTMLElement>("[data-table-list-filters]")]) ??
      firstShown([...container.querySelectorAll<HTMLElement>("input")]);
    const focusAlert = (id: string) =>
      container.querySelector<HTMLElement>(`[data-table-list-alert=${attributeValue(id)}]`)?.focus();
    if (focusRequest.kind === "alert") {
      setFocusRequest(null);
      focusAlert(focusRequest.id);
      return;
    }
    if (focusRequest.kind === "alertIfFocusLost") {
      setFocusRequest(null);
      if (focusIsLost()) {
        focusAlert(focusRequest.id);
        return;
      }
      // The acting row may leave a moment later, for example once its list refetches.
      const { id } = focusRequest;
      const observer = new MutationObserver(() => {
        if (!focusIsLost()) return;
        observer.disconnect();
        focusAlert(id);
      });
      observer.observe(container, { childList: true, subtree: true });
      // Not an effect cleanup: clearing the request re-runs this effect, which would stop the watch at once.
      window.setTimeout(() => observer.disconnect(), 2000);
      return;
    }
    if (focusRequest.kind === "fallback") {
      setFocusRequest(null);
      fallback()?.focus();
      return;
    }
    // A row brought back by an undo appears once the table refetches; wait for it, but not forever.
    if (!rowIds.includes(focusRequest.rowId) && Date.now() < focusRequest.until) {
      const request = focusRequest;
      const timer = window.setTimeout(
        () => setFocusRequest((current) => (current === request ? { kind: "fallback" } : current)),
        request.until - Date.now(),
      );
      return () => window.clearTimeout(timer);
    }
    setFocusRequest(null);
    const { rowId } = focusRequest;
    focusAndFollow(container, () => rowActionsFocusTarget(container, rowId) ?? fallback());
  }, [focusRequest, root, rowIds]);

  const api = useMemo<TableListAlertsApi>(() => ({ push, dismiss }), [push, dismiss]);
  return { alerts, api, undo };
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
            <TableListAlertItem key={alert.id} alert={alert} onUndo={onUndo} onDismiss={onDismiss} />
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function TableListAlertItem({
  alert,
  onUndo,
  onDismiss,
}: {
  alert: AlertEntry;
  onUndo: (id: string) => void;
  onDismiss: (id: string) => void;
}) {
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
        "flex items-center gap-3 rounded-sm border px-4 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
        toneClassName[failed ? "destructive" : (alert.tone ?? "default")],
      )}
    >
      {alert.icon ? <span className="flex shrink-0 [&_svg]:size-4">{alert.icon}</span> : null}
      <p id={messageId} className="min-w-0 flex-1">
        {failed ? (alert.failure ?? t("tableList.alerts.undoFailed")) : alert.message}
      </p>
      {alert.undo && !failed ? (
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
