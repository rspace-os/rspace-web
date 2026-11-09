import type { QueryKey } from "@tanstack/react-query";

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
  update?: ApiV2AsyncFunction;
  delete?: ApiV2AsyncFunction;
  createMany?: ApiV2AsyncFunction;
  updateMany?: ApiV2AsyncFunction;
  deleteMany?: ApiV2AsyncFunction;
  runtimeFields?: ApiV2ReadOperation;
}

export type ApiV2ResourceEntry<TName extends string, TOperations extends ApiV2OperationSet> = {
  readonly name: TName;
} & TOperations;

export { apiV2Resources } from "./generated/apiV2Resources";
