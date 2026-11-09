import { type RefObject, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { AlertEntry, TableListAlert, TableListAlertsApi } from "../components/TableListAlerts";

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
function rowActionsFocusTarget(root: Element, rowId: string): HTMLElement | undefined {
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
    if (focusIsLost()) find()?.focus();
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
  const nextRenderKey = useRef(0);
  useLayoutEffect(() => {
    alertsRef.current = alerts;
  }, [alerts]);

  const push = useCallback((...next: TableListAlert[]) => {
    if (next.length === 0) return;
    const entries = next.map((alert) => ({ ...alert, renderKey: ++nextRenderKey.current, status: "idle" as const }));
    setAlerts((current) => {
      const ids = new Set(entries.map(({ id }) => id));
      // Newest first, directly above the table.
      return [...entries.toReversed(), ...current.filter(({ id }) => !ids.has(id))];
    });
    // Only take focus once the control that acted has gone, so a persistent toolbar or bulk-action button keeps it.
    setFocusRequest({ kind: "alertIfFocusLost", id: next[next.length - 1].id });
  }, []);

  const dismiss = useCallback((id: string) => {
    const current = alertsRef.current;
    const index = current.findIndex((alert) => alert.id === id);
    const remaining = current.filter((alert) => alert.id !== id);
    setAlerts((alerts) => alerts.filter((alert) => alert.id !== id));
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
