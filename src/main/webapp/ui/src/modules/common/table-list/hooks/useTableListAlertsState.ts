import { clamp } from "es-toolkit";
import { type RefObject, useCallback, useMemo, useReducer, useRef } from "react";
import { flushSync } from "react-dom";
import type { AlertEntry, TableListAlert, TableListAlertsApi } from "../components/TableListAlerts";

const focusableSelector = "a[href], button:not(:disabled), input:not(:disabled), [tabindex]:not([tabindex='-1'])";

/**
 * The first candidate the page shows; the table and card presentations both render, one hidden by CSS.
 * Without a layout engine (jsdom) nothing has client rects, so the first candidate stands in.
 */
function firstShown<TElement extends Element>(candidates: readonly TElement[]): TElement | undefined {
  const shown = candidates.find((element) => element.getClientRects().length > 0);
  return shown ?? (document.body.getClientRects().length === 0 ? candidates[0] : undefined);
}

/** The first visible control in a row's actions; the table and card presentations both render, one hidden by CSS. */
function rowActionsFocusTarget(root: Element, rowId: string): HTMLElement | undefined {
  return firstShown(
    [...root.querySelectorAll<HTMLElement>("[data-table-list-row-actions]")]
      .filter((actions) => actions.dataset.tableListRowActions === rowId)
      .flatMap((actions) => [...actions.querySelectorAll<HTMLElement>(focusableSelector)]),
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
    if (focusIsLost()) find()?.focus();
  });
  observer.observe(root, { childList: true, subtree: true });
  window.setTimeout(() => observer.disconnect(), 1000);
}

function focusIsLost(): boolean {
  return document.activeElement === null || document.activeElement === document.body;
}

function alertElements(root: Element): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>("[data-table-list-alert]")];
}

function alertElement(root: Element, id: string): HTMLElement | undefined {
  return alertElements(root).find((alert) => alert.dataset.tableListAlert === id);
}

/** With no alert left and no row to return to, the table's Filters control, else its search. */
function fallbackControl(root: Element): HTMLElement | undefined {
  return (
    firstShown([...root.querySelectorAll<HTMLElement>("[data-table-list-filters]")]) ??
    firstShown([...root.querySelectorAll<HTMLElement>("input")])
  );
}

type AlertsAction =
  | { type: "push"; entries: readonly AlertEntry[] }
  | { type: "remove"; id: string }
  | { type: "update"; id: string; change: Pick<AlertEntry, "status" | "failure"> };

function alertsReducer(alerts: readonly AlertEntry[], action: AlertsAction): readonly AlertEntry[] {
  switch (action.type) {
    case "push": {
      const ids = new Set(action.entries.map(({ id }) => id));
      // Newest first, directly above the table.
      return [...action.entries.toReversed(), ...alerts.filter(({ id }) => !ids.has(id))];
    }
    case "remove":
      return alerts.filter(({ id }) => id !== action.id);
    case "update":
      return alerts.map((entry) => (entry.id === action.id ? { ...entry, ...action.change } : entry));
  }
}

/**
 * TableList-owned alert state. Returned `api` is stable, so an action may push from a callback that
 * outlives its row, such as a dialog confirming a cancellation that removes the row.
 * Call `cancelWatchers` when the table root unmounts.
 */
export function useTableListAlertsState({ root }: { root: RefObject<HTMLElement | null> }) {
  const [alerts, dispatch] = useReducer(alertsReducer, []);
  const nextRenderKey = useRef(0);
  // One focus watcher at a time: starting one cancels the previous.
  const stopWatcher = useRef<(() => void) | null>(null);
  const cancelWatchers = useCallback(() => {
    stopWatcher.current?.();
    stopWatcher.current = null;
  }, []);
  const watch = useCallback(
    (container: Element, onChange: () => boolean, timeout: number, onTimeout?: () => void) => {
      cancelWatchers();
      const observer = new MutationObserver(() => {
        if (onChange()) stop();
      });
      const timer = window.setTimeout(() => {
        stop();
        onTimeout?.();
      }, timeout);
      const stop = () => {
        observer.disconnect();
        window.clearTimeout(timer);
        if (stopWatcher.current === stop) stopWatcher.current = null;
      };
      observer.observe(container, { childList: true, subtree: true });
      stopWatcher.current = stop;
    },
    [cancelWatchers],
  );

  const push = useCallback(
    (...next: TableListAlert[]) => {
      if (next.length === 0) return;
      const entries = next.map((alert) => ({ ...alert, renderKey: ++nextRenderKey.current, status: "idle" as const }));
      dispatch({ type: "push", entries });
      const container = root.current;
      if (!container) return;
      const id = next[next.length - 1].id;
      // Only take focus once the control that acted has gone, so a persistent toolbar or bulk-action button keeps
      // it. The acting control may leave with this alert's render or a moment later, for example after a refetch.
      watch(
        container,
        () => {
          const alert = alertElement(container, id);
          if (!alert || !focusIsLost()) return false;
          alert.focus();
          return true;
        },
        2000,
      );
    },
    [root, watch],
  );

  const dismiss = useCallback(
    (id: string) => {
      const container = root.current;
      const shown = container ? alertElements(container) : [];
      const index = shown.findIndex((alert) => alert.dataset.tableListAlert === id);
      // Only a dismissal from inside the alert moves focus; a programmatic one leaves the acting control focused.
      if (!container || index === -1 || !shown[index].contains(document.activeElement)) {
        dispatch({ type: "remove", id });
        return;
      }
      const remaining = shown.filter((_, position) => position !== index);
      const neighbour = remaining[clamp(index, 0, remaining.length - 1)]?.dataset.tableListAlert;
      flushSync(() => dispatch({ type: "remove", id }));
      (neighbour === undefined ? fallbackControl(container) : alertElement(container, neighbour))?.focus();
    },
    [root],
  );

  const undo = async (id: string) => {
    const alert = alerts.find((entry) => entry.id === id);
    if (!alert?.undo || alert.status === "undoing") return;
    dispatch({ type: "update", id, change: { status: "undoing" } });
    try {
      await alert.undo.run();
    } catch (error) {
      flushSync(() =>
        dispatch({ type: "update", id, change: { status: "failed", failure: alert.undo?.describeError?.(error) } }),
      );
      const container = root.current;
      if (container) alertElement(container, id)?.focus();
      return;
    }
    flushSync(() => dispatch({ type: "remove", id }));
    const container = root.current;
    if (!container) return;
    const rowId = alert.undo.focusRowId;
    if (rowId === undefined) {
      fallbackControl(container)?.focus();
      return;
    }
    const focusRow = () =>
      focusAndFollow(container, () => rowActionsFocusTarget(container, rowId) ?? fallbackControl(container));
    if (rowActionsFocusTarget(container, rowId)) {
      focusRow();
      return;
    }
    // A row brought back by an undo appears once the table refetches; wait for it, but not forever.
    watch(
      container,
      () => {
        if (!rowActionsFocusTarget(container, rowId)) return false;
        focusRow();
        return true;
      },
      5000,
      () => fallbackControl(container)?.focus(),
    );
  };

  const api = useMemo<TableListAlertsApi>(() => ({ push, dismiss }), [push, dismiss]);
  return { alerts, api, undo, cancelWatchers };
}
