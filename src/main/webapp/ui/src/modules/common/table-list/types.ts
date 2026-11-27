import type { Temporal } from "@js-temporal/polyfill";
import type { ReactNode, Ref } from "react";
import type { FieldName, ResolvedCollectionConfig, SearchSelector, SortRule } from "@/modules/common/collection/config";
import type { FilterOperator } from "@/modules/common/collection/filterOperators";
import type { RuntimeFieldCatalogForNamespace, RuntimeFieldDefinition } from "./api-v2/runtimeFieldCatalog";
import type { TableListAlertsApi } from "./components/TableListAlerts";
import type { TableListPersistenceIdentity } from "./persistence";

/** One filter operand; date-time operands are instants cut to milliseconds. */
export type FilterScalar = string | number | boolean | Temporal.Instant;
export type FilterValue = FilterScalar | readonly FilterScalar[];

export type FilterExpression<TDocument> =
  | {
      kind: "comparison";
      field: SearchSelector<TDocument>;
      operator: FilterOperator;
      value: FilterValue;
    }
  | {
      kind: "and" | "or";
      children: readonly FilterExpression<TDocument>[];
    };

export type FilterState<TDocument> = {
  search: string;
  expression: FilterExpression<TDocument> | null;
};

export type PageState = {
  pageIndex: number;
  pageSize: number;
};

export type CollectionQueryState<TDocument> = {
  filters: FilterState<TDocument>;
  sorting: readonly SortRule<TDocument>[];
  page: PageState;
  visibleFields: readonly FieldName<TDocument>[];
};

export type CollectionPage<TDocument> = {
  rows: readonly TDocument[];
  rowCount?: number;
  hasNextPage?: boolean;
};

type CollectionFetchContext = {
  signal: AbortSignal;
};

/** The common endpoint seam for API V2 and custom collection requests. */
export type CollectionFetcher<TDocument> = (
  state: CollectionQueryState<TDocument>,
  context: CollectionFetchContext,
) => Promise<CollectionPage<TDocument>>;

export type ClientTableListDataSource<TDocument> = {
  type: "client";
  rows: readonly TDocument[];
};

export type RemoteTableListDataSource<TDocument> = {
  type: "remote";
  queryKey: readonly unknown[] | ((state: CollectionQueryState<TDocument>) => readonly unknown[]);
  /** Prevent requests while external filter definitions or restored state are unresolved. */
  enabled?: boolean | ((state: CollectionQueryState<TDocument>) => boolean);
  staleTime?: number;
  gcTime?: number;
  retry?: boolean | number;
  refetchInterval?: number | false;
  keepPreviousData?: boolean;
  /** Previous rows may only be reused within the same endpoint and authorization scope. */
  dataScope?: string | number;
} & (
  | { fetch: CollectionFetcher<TDocument>; wire?: never }
  | {
      fetch?: never;
      /** Cache a wire response under its generated key and map it per observer. */
      wire: {
        request: (state: CollectionQueryState<TDocument>, context: CollectionFetchContext) => Promise<unknown>;
        select: (body: unknown, state: CollectionQueryState<TDocument>) => CollectionPage<TDocument>;
      };
    }
);

export type TableListDataSource<TDocument> =
  | ClientTableListDataSource<TDocument>
  | RemoteTableListDataSource<TDocument>;

export type TableListFeatures<TDocument> = {
  filtering:
    | false
    | {
        value: FilterState<TDocument>;
        onChange: (value: FilterState<TDocument>) => void;
      };
  sorting:
    | false
    | {
        value: readonly SortRule<TDocument>[];
        onChange: (value: readonly SortRule<TDocument>[]) => void;
      };
  pagination:
    | false
    | {
        value: PageState;
        rowCount?: number;
        hasNextPage?: boolean;
        onChange: (value: PageState) => void;
      };
  columns:
    | false
    | {
        value: readonly FieldName<TDocument>[];
        onChange: (value: readonly FieldName<TDocument>[]) => void;
      };
};

export type TableListStatus = "idle" | "loading" | "refreshing" | "error";

export type TableListVariant = "card" | "transparent";

/** Where a presentation is available relative to the TableList container's 48rem breakpoint. */
export type TableListPresentationVisibility = false | "all" | "narrow" | "wide";

export type TableListPresentations = {
  /** Defaults to `"all"`. */
  table?: TableListPresentationVisibility;
  /** Defaults to `false`, keeping cards explicitly opt-in. */
  cards?: TableListPresentationVisibility;
};

/** Layout of this field in item presentations such as cards. */
export type TableListItemFieldLayout = {
  /** Stack the label above the value and span the complete item body. */
  fullWidth?: boolean;
  /** `footer` holds compact row controls such as actions. */
  placement?: "field" | "footer";
};

export type TableListFilterButtons = {
  legend: string;
  /** Keep the toolbar controls on a row below Search at every breakpoint. */
  controlsOnSeparateRow?: boolean;
  /** Page-owned filter controls rendered before the quick-filter buttons. */
  controls?: ReactNode;
  /** Whether page-owned controls differ from their defaults and should enable the shared reset. */
  hasChanges?: boolean;
  buttons: readonly {
    id: string;
    label: ReactNode;
    icon?: ReactNode;
    pressed: boolean;
    disabled?: boolean;
    count?: number;
    onClick: () => void;
  }[];
  onReset: () => void;
};

export type TableListUiColumn<TDocument> = {
  id: string;
  label: ReactNode;
  /** Initial column width in CSS pixels. */
  width?: number;
  /** Smallest user-selected column width in CSS pixels. */
  minWidth?: number;
  item?: TableListItemFieldLayout;
  /** `rowId` is the row's `getRowId` value. */
  renderCell: (row: TDocument, rowId: string) => ReactNode;
};

export type TableListRowActions<TDocument> = Omit<TableListUiColumn<TDocument>, "renderCell"> & {
  /** Render lightweight controls for one row. Call `activate` only for an interaction hosted outside the row. */
  renderCell: (context: { row: TDocument; activate: (actionId: string) => void }) => ReactNode;
  /** Render the single active dialog, drawer, form, or other row interaction. */
  renderInteraction: (context: { actionId: string; row: TDocument; close: () => void }) => ReactNode;
};

export type TableListSelectionContext = {
  selectedRowIds: ReadonlySet<string>;
  clearSelection: () => void;
};

export type TableListSelection<TDocument> = {
  value: ReadonlySet<string>;
  onChange: (value: ReadonlySet<string>) => void;
  disabled?: boolean;
  /** The positive maximum number of rows that the user can select. */
  maximumCount: number;
  getRowLabel?: (row: TDocument) => string;
  renderActions: (context: TableListSelectionContext) => ReactNode;
};

export type TableListRestoredViewIssue = {
  kind: "invalid" | "network";
  encoded: string;
  retry?: () => void;
  remove: () => void;
};

/** Runtime fields the filter and column panels can add, and the callback that loads a chosen definition. */
export type TableListRuntimeFields = {
  definitions: readonly RuntimeFieldCatalogForNamespace[];
  onSelect: (namespace: string, definition: RuntimeFieldDefinition) => void;
};

export type TableListProps<TDocument extends Record<string, unknown>> = {
  restoredViewIssue?: TableListRestoredViewIssue;
  /** Imperative access for an owner that reports action results outside TableList context. */
  alertsRef?: Ref<TableListAlertsApi>;
  runtimeFields?: TableListRuntimeFields;
  /** The caller's query scope for searches the filter panel makes. */
  authScope?: string | number;
  config: ResolvedCollectionConfig<TDocument>;
  rows: readonly TDocument[];
  getRowId: (row: TDocument) => string;
  features: TableListFeatures<TDocument>;
  /** Apply filtering, sorting, and pagination to `rows` in the browser. */
  clientSide?: boolean;
  status?: TableListStatus;
  error?: unknown;
  onRowOpen?: (row: TDocument) => void;
  createAction?: ReactNode;
  /** Override the collection heading's visual style without changing its h1 semantics. */
  headingClassName?: string;
  uiColumns?: readonly TableListUiColumn<TDocument>[];
  rowActions?: TableListRowActions<TDocument>;
  selection?: TableListSelection<TDocument>;
  /** Page-owned filters rendered beside the built-in toolbar controls. */
  filterButtons?: TableListFilterButtons;
  /** Enable table and card presentations everywhere or on either side of the standard 48rem container breakpoint. */
  presentations?: TableListPresentations;
  /** Replace the built-in table/card result area with rows from the same filtered, sorted, and paginated row model. */
  renderRows?: (rows: readonly TDocument[]) => ReactNode;
  /** Call `renderRows` with an empty array instead of showing the standard empty state. */
  renderRowsWhenEmpty?: boolean;
  /** Replace the default empty-state guidance while preserving TableList controls. */
  emptyDescription?: ReactNode;
  variant?: TableListVariant;
  /** Hide the collection heading when another page section labels this embedded list. */
  hideHeader?: boolean;
  /** Hides the filter panel for a data source that only honours free-text search. */
  hideFilterPanel?: boolean;
  /** Debounce client-side search when its value also drives remote work. */
  debounceSearch?: boolean;
  /** Reserve the height of ten data rows for the empty state. Set this to `false` for a compact display. */
  reserveEmptyRows?: boolean;
  /** Set by the persisted hooks; a page may not render two tables that share either value. */
  persistenceIdentity?: TableListPersistenceIdentity;
};
