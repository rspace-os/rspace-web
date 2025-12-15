import axios from "@/common/axios";

export type PlanSummary = {
  id: string;
  label: string;
  grant: string;
  createdAt: number;
  modifiedAt: number;
};

export type SearchParameters = {
  like: string | null;
  grantsLike: string | null;
  fundersLike: string | null;
  collaboratorsLike: string | null;
  page: number;
  pageSize: number;
};

export type Plans = {
  totalCount: number;
  data: Array<PlanSummary>;
};

type SuccessResponse = {
  success: true;
  data: { totalCount: number; data: Array<PlanSummary> };
};

type ErrorResponse = {
  error: unknown;
};

type ApiResponse = SuccessResponse | ErrorResponse;

function isErrorResponse(response: ApiResponse): response is ErrorResponse {
  return !("success" in response);
}

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
    ...((like              ? { like              } : {})),
    ...((grantsLike        ? { grantsLike        } : {})),
    ...((fundersLike       ? { fundersLike       } : {})),
    ...((collaboratorsLike ? { collaboratorsLike } : {})),
    page: page.toString(),
    pageSize: pageSize.toString(),
  });
  const { data } = await axios.get<ApiResponse>(
    `/apps/argos/plans?${urlArgs.toString()}`
  );
  if (isErrorResponse(data)) {
    throw data.error;
  }
  const plans = data.data;
  return {
    totalCount: plans.totalCount,
    data: plans.data,
  };
}
