import { useSuspenseQuery } from "@tanstack/react-query";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  type WorkspaceRecordInformation,
  WorkspaceGetRecordInformationResponseSchema,
} from "@/modules/workspace/schema";
import {
  WORKSPACE_API_BASE_URL,
  toWorkspaceError,
} from "@/modules/workspace/utils";

export type GetWorkspaceRecordInformationParams = {
  recordId: number;
  revision?: number;
  version?: number;
};

export const workspaceQueryKeys = {
  all: ["rspace.workspace"] as const,
  recordInformation: (params: GetWorkspaceRecordInformationParams) =>
    [...workspaceQueryKeys.all, "getRecordInformation", params] as const,
};

export async function getWorkspaceRecordInformationAjax({
  recordId,
  revision,
  version,
}: GetWorkspaceRecordInformationParams): Promise<WorkspaceRecordInformation> {
  const searchParams = new URLSearchParams();
  searchParams.set("recordId", String(recordId));
  if (revision !== undefined) {
    searchParams.set("revision", String(revision));
  }
  if (version !== undefined) {
    searchParams.set("version", String(version));
  }

  const response = await fetch(
    `${WORKSPACE_API_BASE_URL}/getRecordInformation?${searchParams.toString()}`,
    {
      method: "GET",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    },
  );

  const data: unknown = await response.json();

  if (!response.ok) {
    throw toWorkspaceError(
      data,
      `Failed to fetch record information: ${response.statusText}`,
    );
  }

  const parsedResponse = parseOrThrow(
    WorkspaceGetRecordInformationResponseSchema,
    data,
  );

  if (parsedResponse.data === null) {
    throw toWorkspaceError(parsedResponse, "No record information was returned.");
  }

  return parsedResponse.data;
}

export function useGetWorkspaceRecordInformationAjaxQuery(
  params: GetWorkspaceRecordInformationParams,
) {
  return useSuspenseQuery({
    queryKey: workspaceQueryKeys.recordInformation(params),
    queryFn: () => getWorkspaceRecordInformationAjax(params),
    retry: false,
  });
}

