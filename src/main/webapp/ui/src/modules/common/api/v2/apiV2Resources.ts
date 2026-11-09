import type { QueryKey } from "@tanstack/react-query";
import type { ApiV2FilterResource, ApiV2Where } from "./apiV2Where";

type ApiV2AsyncFunction = (...args: never[]) => Promise<unknown>;
type ApiV2KeyFunction = (...args: never[]) => QueryKey;

export interface ApiV2ReadOperation<
  TRequest extends ApiV2AsyncFunction = ApiV2AsyncFunction,
  TQueryKey extends ApiV2KeyFunction = ApiV2KeyFunction,
> {
  request: TRequest;
  queryKey: TQueryKey;
}

export interface ApiV2OperationSet {
  list?: ApiV2ReadOperation;
  count?: ApiV2ReadOperation;
  get?: ApiV2ReadOperation;
  create?: ApiV2AsyncFunction;
  runtimeFields?: ApiV2ReadOperation;
}

/** The fetch options every generated request function accepts last. */
export type ApiV2RequestOptions = RequestInit;

/**
 * Query parameters every collection list accepts. `R` is the resource's filter; without one (`never`), `where` is a
 * plain RSQL string, for resources outside the generated registry.
 */
export type ApiV2CollectionParams<R extends ApiV2FilterResource = never> = {
  where?: [R] extends [never] ? string : ApiV2Where<R>;
  sort?: string;
  page?: number;
  limit?: number;
  totals?: boolean;
  max?: number;
  depth?: number;
  fields?: Partial<Record<string, string>>;
  exclude?: Partial<Record<string, string>>;
};

/** A collection list operation, callable with the shared collection parameters. */
export interface ApiV2ListOperation<R extends ApiV2FilterResource = never> {
  request: (params?: ApiV2CollectionParams<R>, options?: ApiV2RequestOptions) => Promise<unknown>;
  queryKey: (params?: ApiV2CollectionParams<R>) => QueryKey;
}

/** Query parameters a runtime-field catalog accepts. */
export type ApiV2RuntimeFieldParams = { q?: string; ids?: string; limit?: number; page?: number };

/** A runtime-field catalog operation; `N` is the literal union of the resource's namespaces. */
export interface ApiV2RuntimeFieldsOperation<N extends string> {
  request: (namespace: N, params?: ApiV2RuntimeFieldParams, options?: ApiV2RequestOptions) => Promise<unknown>;
  queryKey: (namespace: N, params?: ApiV2RuntimeFieldParams) => QueryKey;
}

export type ApiV2ResourceEntry<TName extends string, TOperations extends ApiV2OperationSet> = {
  readonly name: TName;
  /** The filter the list's `where` is typed against; absent when the list has no filter metadata. */
  readonly filterResource?: ApiV2FilterResource;
  /** The namespaces the runtime-field catalog accepts. */
  readonly runtimeFieldNamespaces?: readonly string[];
} & TOperations;
