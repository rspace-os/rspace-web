import type {
  ApiV2CollectionParams,
  ApiV2ListOperation,
  ApiV2RuntimeFieldsOperation,
} from "@/modules/common/api/v2/apiV2Resources";
import type { ApiV2FilterResource, ApiV2WhereObject } from "@/modules/common/api/v2/apiV2Where";
import type { FieldName } from "@/modules/common/collection/config";
import type { CollectionQueryState, FilterExpression } from "../types";
import { allOf } from "../utils/expressions";
import type { ApiV2CollectionAdapter } from "./adapter";
import { filterExpressionFromWhere, isFilterExpression } from "./filterExpressionFromWhere";

/** A collection resource whose list, and catalog if any, accept the shared API v2 parameter shapes. */
export type ApiV2CollectionResource<R extends ApiV2FilterResource = never, N extends string = never> = {
  readonly name: string;
  readonly filterResource?: R;
  readonly list: ApiV2ListOperation<R>;
  readonly runtimeFieldNamespaces?: readonly N[];
  readonly runtimeFields?: ApiV2RuntimeFieldsOperation<N>;
};

/** The filter a resource's `where` is typed against; `never` for a resource outside the generated registry. */
export type ApiV2FilterResourceOf<TEntry> = TEntry extends {
  readonly filterResource: infer R extends ApiV2FilterResource;
}
  ? R
  : never;

/** The namespaces a resource's runtime-field catalog accepts. */
export type ApiV2RuntimeFieldNamespaceOf<TEntry> = TEntry extends {
  readonly runtimeFieldNamespaces: readonly (infer N extends string)[];
}
  ? N
  : never;

/** The structural shape a resource entry must have to back an API v2 table. */
export type ApiV2CollectionResourceOf<TEntry> = ApiV2CollectionResource<
  ApiV2FilterResourceOf<TEntry>,
  ApiV2RuntimeFieldNamespaceOf<TEntry>
>;

export type ApiV2CollectionProjection<TDocument> = "visible" | { fixed: readonly FieldName<TDocument>[] };

type BaseFilterValue<TDocument, R extends ApiV2FilterResource> = FilterExpression<TDocument> | ApiV2WhereObject<R>;

/**
 * A filter ANDed with every request. A registry resource states it as a typed where object; a resource outside the
 * registry, whose selectors have no generated types, as a filter expression.
 */
export type ApiV2BaseFilter<TDocument, R extends ApiV2FilterResource> = [R] extends [never]
  ?
      | FilterExpression<TDocument>
      | ((expression: FilterExpression<TDocument> | null) => FilterExpression<TDocument> | undefined)
  : ApiV2WhereObject<R> | ((expression: FilterExpression<TDocument> | null) => ApiV2WhereObject<R> | undefined);

export type ApiV2CollectionFetchOptions<TDocument, R extends ApiV2FilterResource = never> = {
  depth?: number;
  /** Identity used for selection resets, never query keys. */
  authScope?: string | number;
  projection?: ApiV2CollectionProjection<TDocument>;
  baseFilter?: ApiV2BaseFilter<TDocument, R>;
  validateRows?: (rows: readonly TDocument[]) => void;
};

export function projectedState<TDocument>(
  adapter: Pick<ApiV2CollectionAdapter<TDocument>, "isRuntimeSelector">,
  state: CollectionQueryState<TDocument>,
  projection: ApiV2CollectionProjection<TDocument>,
): CollectionQueryState<TDocument> {
  if (projection === "visible") return state;
  const runtime = state.visibleFields.filter((field) => adapter.isRuntimeSelector(String(field)));
  return { ...state, visibleFields: [...projection.fixed, ...runtime] };
}

function baseFilterExpression<TDocument, R extends ApiV2FilterResource>(
  filter: BaseFilterValue<TDocument, R> | undefined,
): FilterExpression<TDocument> | undefined {
  if (filter === undefined) return undefined;
  return isFilterExpression(filter) ? filter : filterExpressionFromWhere<TDocument, R>(filter);
}

export function apiV2CollectionRequestParams<TDocument, R extends ApiV2FilterResource = never>(
  adapter: ApiV2CollectionAdapter<TDocument, R>,
  state: CollectionQueryState<TDocument>,
  {
    depth,
    projection = "visible",
    baseFilter,
  }: {
    depth?: number;
    projection?: ApiV2CollectionProjection<TDocument>;
    baseFilter?:
      | BaseFilterValue<TDocument, R>
      | ((expression: FilterExpression<TDocument> | null) => BaseFilterValue<TDocument, R> | undefined);
  } = {},
): ApiV2CollectionParams<R> {
  const filter = baseFilterExpression(
    typeof baseFilter === "function" ? baseFilter(state.filters.expression) : baseFilter,
  );
  const expression = allOf([filter, state.filters.expression]);
  const filteredState = { ...state, filters: { ...state.filters, expression } };
  const requestState = projectedState(adapter, filteredState, projection);
  const parameters = adapter.toRequestParams(requestState);
  const requiredDepth = adapter.requiredDepth(requestState);
  return depth !== undefined || requiredDepth > 0
    ? { ...parameters, depth: Math.max(depth ?? 0, requiredDepth) }
    : parameters;
}
