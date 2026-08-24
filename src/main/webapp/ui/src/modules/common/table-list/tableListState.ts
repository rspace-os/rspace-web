import type { ReactNode } from "react";
import type {
  FieldName,
  FilterOperator,
  ResolvedCollectionConfig,
  SearchSelector,
  SortRule,
} from "@/modules/common/collection/collectionConfig";
import type { RuntimeFieldDefinition } from "./adapters/apiV2/runtimeFieldCatalog";

export type FilterValue = string | number | boolean | Date | readonly (string | number | boolean | Date)[];

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
  rowCount: number;
};

export type CollectionFetchContext = {
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
  fetch: CollectionFetcher<TDocument>;
  staleTime?: number;
  gcTime?: number;
  retry?: boolean | number;
  refetchInterval?: number | false;
  keepPreviousData?: boolean;
};

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
        rowCount: number;
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

export type TableListQueryStringOptions = {
  /** Prefix for the table's owned query parameters. Defaults to the collection slug; use a distinct prefix for tables on the same page. */
  parameterPrefix?: string;
  /** Stable browser-profile-scoped persistence identity. Defaults to the collection slug; set it when independent views reuse a config. */
  tableId?: string;
};

export type TableListUiColumn<TDocument> = {
  id: string;
  label: ReactNode;
  /** Initial column width in CSS pixels. */
  width?: number;
  /** Smallest user-selected column width in CSS pixels. */
  minWidth?: number;
  renderCell: (row: TDocument) => ReactNode;
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

export type TableListProps<TDocument extends Record<string, unknown>> = {
  runtimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  onSelectRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  config: ResolvedCollectionConfig<TDocument>;
  rows: readonly TDocument[];
  getRowId: (row: TDocument) => string;
  features: TableListFeatures<TDocument>;
  /** Apply filtering, sorting, and pagination to `rows` in the browser. */
  clientSide?: boolean;
  status?: TableListStatus;
  error?: unknown;
  onRowOpen?: (row: TDocument) => void;
  onCreate?: () => void;
  createAction?: ReactNode;
  createLabel?: string;
  uiColumns?: readonly TableListUiColumn<TDocument>[];
  rowActions?: TableListRowActions<TDocument>;
  selection?: TableListSelection<TDocument>;
  variant?: TableListVariant;
  /** Reserve the height of ten data rows for the empty state. Set this to `false` for a compact display. */
  reserveEmptyRows?: boolean;
  /** Persist filters, sorting, and visible columns in the URL and browser storage. Set this to `false` to disable both. */
  queryString?: boolean | TableListQueryStringOptions;
};
