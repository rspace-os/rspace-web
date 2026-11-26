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
  success: false;
  error: { errorMessages: Array<string> } | null;
};

type ApiResponse = SuccessResponse | ErrorResponse;

function isErrorResponse(response: ApiResponse): response is ErrorResponse {
  return response.success !== true;
}

export async function fetchPlanSummaries({ like, page, pageSize }: SearchParameters): Promise<Plans> {
  const urlArgs = new URLSearchParams({
    ...(like ? { like } : {}),
    page: page.toString(),
    pageSize: pageSize.toString(),
  });
  const { data } = await axios.get<ApiResponse>(`/apps/argos/plans?${urlArgs.toString()}`);
  if (isErrorResponse(data)) {
    throw data.error?.errorMessages[0] ?? new Error("Listing DMPs failed.");
  }
  const plans = data.data;
  return {
    totalCount: plans.totalCount,
    data: plans.data,
  };
}
