const FILTER_KEYS = [
  "sharedFilter",
  "favoritesFilter",
  "viewableItemsFilter",
  "templatesFilter",
  "ontologiesFilter",
] as const;

export type FilterKey = (typeof FILTER_KEYS)[number];

const SEARCH_OPTIONS = [
  "global",
  "fullText",
  "tag",
  "name",
  "created",
  "lastModified",
  "form",
  "template",
  "attachment",
  "owner",
  "records",
] as const;

// The server reports this sort field, descending, on a restored search even when none was chosen
const SERVER_DEFAULT_ORDER_BY = "modificationDateMillis";

export type SearchOption = (typeof SEARCH_OPTIONS)[number];

export type WorkspaceSearchState = {
  options: ReadonlyArray<string>;
  terms: ReadonlyArray<string>;
  advancedSearch: boolean;
  operator: string;
  orderBy: string | null;
  sortOrder: string;
} & Record<FilterKey, boolean>;

/**
 * The canonical query string for a Workspace search. The key order is fixed so
 * that two equal searches always give the same string.
 */
export function toSearchQuery(settings: WorkspaceSearchState): string {
  const params = new URLSearchParams();
  for (const option of settings.options) params.append("options[]", option);
  for (const term of settings.terms) params.append("terms[]", term);
  params.append("advancedSearch", String(settings.advancedSearch));
  params.append("operator", settings.operator);
  for (const key of FILTER_KEYS) {
    if (settings[key]) params.append(key, "true");
  }
  const isServerDefaultSort = settings.orderBy === SERVER_DEFAULT_ORDER_BY && settings.sortOrder === "DESC";
  if (settings.orderBy && !isServerDefaultSort) params.append("orderBy", settings.orderBy);
  params.append("sortOrder", settings.sortOrder);
  return params.toString();
}

export type SummaryLabels = {
  options: Record<SearchOption, string>;
  filters: Record<FilterKey, string>;
  operators: Record<"AND" | "OR", string>;
  dateRange: (from: string | null, to: string | null) => string;
};

function summariseTerm(option: string, term: string, labels: SummaryLabels): string {
  if (option === "created" || option === "lastModified") {
    const [from, to] = term.split("; ").map((date) => (!date || date === "null" ? null : date.slice(0, 10)));
    return labels.dateRange(from ?? null, to ?? null);
  }
  return term.replaceAll("<<>>", ", ").replaceAll("__rspactags_comma__", ", ");
}

/**
 * Plain text describing a search, for a tooltip. Render it as a text node.
 */
export function summariseSearch(state: WorkspaceSearchState, labels: SummaryLabels): string {
  const operator = labels.operators[state.operator === "OR" ? "OR" : "AND"];
  const criteria = state.options
    .map((option, i) => {
      const label = labels.options[option as SearchOption] ?? option;
      return `${label}: ${summariseTerm(option, state.terms[i] ?? "", labels)}`;
    })
    .join(` ${operator} `);
  const filters = FILTER_KEYS.filter((key) => state[key]).map((key) => labels.filters[key]);
  return filters.length > 0 ? `${criteria}; ${filters.join(", ")}` : criteria;
}

export type ParsedSearchQuery =
  | { kind: "none" }
  | { kind: "invalid" }
  | { kind: "search"; state: WorkspaceSearchState };

export function parseSearchQuery(search: string): ParsedSearchQuery {
  const params = new URLSearchParams(search);
  const options = params.getAll("options[]");
  const terms = params.getAll("terms[]");
  if (options.length === 0 && terms.length === 0) return { kind: "none" };
  if (
    options.length !== terms.length ||
    terms.some((term) => term.trim() === "") ||
    !options.every((option) => (SEARCH_OPTIONS as ReadonlyArray<string>).includes(option))
  ) {
    return { kind: "invalid" };
  }
  const operator = params.get("operator");
  const sortOrder = params.get("sortOrder");
  return {
    kind: "search",
    state: {
      options,
      terms,
      advancedSearch: params.get("advancedSearch") === "true",
      operator: operator === "OR" ? "OR" : "AND",
      ...(Object.fromEntries(FILTER_KEYS.map((key) => [key, params.get(key) === "true"])) as Record<
        FilterKey,
        boolean
      >),
      orderBy: params.get("orderBy"),
      sortOrder: sortOrder === "ASC" ? "ASC" : "DESC",
    },
  };
}
