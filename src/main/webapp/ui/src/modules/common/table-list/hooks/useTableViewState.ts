import { useRef, useState } from "react";
import type { FieldName, ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/config";
import { serializeRsqlExpression } from "../rsql";
import type { CollectionQueryState, FilterExpression } from "../types";
import { nonDefaultColumns, nonDefaultSorting, serializeFilters, serializeSorting } from "../utils/queryString";
import type { ParsedTableView, RawTableView } from "../utils/viewStorage";
import type { TableViewStore } from "./useTableViewStore";

export type TableListFeatureSelection = {
  filtering?: boolean;
  sorting?: boolean;
  pagination?: boolean;
  columns?: boolean;
};

/** A raw view resolved against the table. An absent or unusable member is `undefined` and takes the base value. */
export type ResolvedTableView<TDocument> = {
  search?: string;
  expression?: FilterExpression<TDocument> | null;
  sorting?: readonly SortRule<TDocument>[];
  visibleFields?: readonly FieldName<TDocument>[];
};

/** A parsed view's members to adopt: an invalid (`null`) member, like an absent one, falls back to the base view. */
export function resolvedFromParsed<TDocument>(parsed: ParsedTableView<TDocument>): ResolvedTableView<TDocument> {
  return {
    search: parsed.search,
    expression: parsed.where ?? undefined,
    sorting: parsed.sort ?? undefined,
    visibleFields: parsed.columns ?? undefined,
  };
}

type ViewState<TDocument> = {
  /** The raw view signature this state belongs to. */
  seen: string;
  adopted: boolean;
  state: CollectionQueryState<TDocument>;
  /** A rejected edit, shown with the table's error state until a valid edit or navigation replaces it. */
  pending: CollectionQueryState<TDocument> | null;
};

function withPageReset<TDocument>(
  previous: CollectionQueryState<TDocument>,
  next: CollectionQueryState<TDocument>,
): CollectionQueryState<TDocument> {
  const changed =
    serializeFilters(previous.filters) !== serializeFilters(next.filters) ||
    serializeSorting(previous.sorting) !== serializeSorting(next.sorting);
  return changed && next.page.pageIndex !== 0 ? { ...next, page: { ...next.page, pageIndex: 0 } } : next;
}

/**
 * The table's query state, adopted from the store's raw view during render and committed back to it by every
 * setter. `resolved` is `null` while the raw view cannot be adopted (blocked): the state is then `base` and
 * edits are ignored until the view resolves or `store.remove()` clears it.
 */
export function useTableViewState<TDocument>({
  config,
  base,
  store,
  resolved,
  features,
  normalizeFilterExpression,
  reject,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  base: CollectionQueryState<TDocument>;
  store: TableViewStore;
  resolved: ResolvedTableView<TDocument> | null;
  features?: TableListFeatureSelection;
  normalizeFilterExpression?: (expression: FilterExpression<TDocument> | null) => FilterExpression<TDocument> | null;
  /** The reason a state cannot be requested, or `null`. A rejected edit stays pending and is not written. */
  reject?: (state: CollectionQueryState<TDocument>) => unknown;
}) {
  const enabled = (feature: keyof TableListFeatureSelection) => features?.[feature] !== false;
  const normalize = (state: CollectionQueryState<TDocument>): CollectionQueryState<TDocument> =>
    normalizeFilterExpression
      ? { ...state, filters: { ...state.filters, expression: normalizeFilterExpression(state.filters.expression) } }
      : state;
  const adopt = (current: CollectionQueryState<TDocument>, view: ResolvedTableView<TDocument>) =>
    withPageReset(
      current,
      normalize({
        ...current,
        filters: enabled("filtering")
          ? {
              search: view.search ?? base.filters.search,
              expression: view.expression === undefined ? base.filters.expression : view.expression,
            }
          : base.filters,
        sorting: enabled("sorting") ? (view.sorting ?? base.sorting) : base.sorting,
        visibleFields: enabled("columns") ? (view.visibleFields ?? base.visibleFields) : base.visibleFields,
      }),
    );
  const serialize = (state: CollectionQueryState<TDocument>): RawTableView => ({
    search: enabled("filtering") ? state.filters.search || null : null,
    where:
      enabled("filtering") && state.filters.expression !== null
        ? serializeRsqlExpression(state.filters.expression)
        : null,
    columns: enabled("columns") ? nonDefaultColumns(state.visibleFields, config.defaultColumns) : null,
    sort: enabled("sorting") ? nonDefaultSorting(state.sorting, config.defaultSort ?? []) : null,
  });

  const [view, setView] = useState<ViewState<TDocument>>(() => ({
    seen: store.rawSignature,
    adopted: resolved !== null,
    state: resolved === null ? base : adopt(base, resolved),
    pending: null,
  }));
  // Adopt a new raw view during render. Navigation always wins: it drops a pending edit, even when blocked.
  let current = view;
  if (current.seen !== store.rawSignature)
    current = { ...current, seen: store.rawSignature, adopted: false, pending: null };
  if (!current.adopted && resolved !== null)
    current = { ...current, adopted: true, state: adopt(current.state, resolved) };
  if (current !== view) setView(current);

  const blocked = !current.adopted;
  const state = blocked ? base : (current.pending ?? current.state);
  // Read only in handlers: two setters in one handler compose on the first one's result.
  const latest = useRef({ state, blocked, raw: store.raw });
  latest.current = { state, blocked, raw: store.raw };

  const commit = (update: (state: CollectionQueryState<TDocument>) => CollectionQueryState<TDocument>) => {
    if (latest.current.blocked) return;
    const previous = latest.current.state;
    const next = withPageReset(previous, normalize(update(previous)));
    latest.current = { ...latest.current, state: next };
    if ((reject?.(next) ?? null) !== null) {
      setView((existing) => ({ ...existing, pending: next }));
      return;
    }
    const raw = serialize(next);
    const signature = store.signatureOf(raw);
    setView((existing) => ({ ...existing, seen: signature, adopted: true, state: next, pending: null }));
    if (store.signatureOf(latest.current.raw) !== signature) {
      latest.current = { ...latest.current, raw };
      store.write(raw);
    }
  };

  return { state, blocked, pending: blocked ? null : current.pending, commit };
}
