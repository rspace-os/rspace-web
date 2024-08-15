// @flow

import axios from "axios";

export type PlanSummary = {
  id: string,
  label: string,
  grant: string,
  createdAt: number,
  modifiedAt: number,
};

export type SearchParameters = {|
  like: ?string,
  grantsLike: ?string,
  fundersLike: ?string,
  collaboratorsLike: ?string,
  page: number,
  pageSize: number,
|};

export type Plans = {|
  totalCount: number,
  data: Array<PlanSummary>,
|};

export async function fetchPlanSummaries({
  like,
  grantsLike,
  fundersLike,
  collaboratorsLike,
  page,
  pageSize,
}: SearchParameters): Promise<Plans> {
  // prettier-ignore
  const urlArgs = new URLSearchParams({
    ...((like              ? { like              } : {}): {| like             ?: string |}),
    ...((grantsLike        ? { grantsLike        } : {}): {| grantsLike       ?: string |}),
    ...((fundersLike       ? { fundersLike       } : {}): {| fundersLike      ?: string |}),
    ...((collaboratorsLike ? { collaboratorsLike } : {}): {| collaboratorsLike?: string |}),
    page: page.toString(),
    pageSize: pageSize.toString(),
  });
  const { data } = await axios.get<
    | {|
        success: boolean,
        data: { totalCount: number, data: Array<PlanSummary> },
      |}
    | {| error: mixed |}
  >(`/apps/argos/plans?${urlArgs.toString()}`);
  if (data.success) {
    const plans = data.data;
    return {
      totalCount: plans.totalCount,
      data: plans.data,
    };
  }
  throw data.error;
}
