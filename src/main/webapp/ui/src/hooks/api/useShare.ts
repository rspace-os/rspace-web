import React from "react";
import axios from "@/common/axios";
import useOauthToken from "@/common/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";

export type ShareInfo = {
  id: number;
  sharedItemId: number;
  shareItemName: string;
  sharedTargetType: "USER" | "GROUP";
  permission: "READ" | "EDIT";
  sharedTargetId: number;
  sharedTargetName: string;
  sharedTargetDisplayName: string;
  _links: Array<{
    link: string;
    rel: string;
  }>;
};

export type ShareResponse = {
  totalHits: number;
  pageNumber: number;
  shares: ShareInfo[];
  _links: Array<{
    link: string;
    rel: string;
  }>;
};

/**
 * This custom hook provides functionality to fetch sharing information
 * using the `/api/v1/share` endpoint.
 */
export default function useShare(): {
  /**
   * Fetches sharing information for a specific global ID.
   */
  getShareInfo: (globalId: string) => Promise<ShareResponse>;

  /**
   * Fetches sharing information for multiple global IDs.
   */
  getShareInfoForMultiple: (globalIds: string[]) => Promise<Map<string, ShareInfo[]>>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  async function getShareInfo(globalId: string): Promise<ShareResponse> {
    try {
      const { data } = await axios.get<ShareResponse>(`/api/v1/share`, {
        params: {
          query: globalId,
        },
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
      return data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error fetching sharing information",
          message: getErrorMessage(e, "An unknown error occurred."),
        })
      );
      throw new Error("Could not fetch sharing information", {
        cause: e,
      });
    }
  }

  async function getShareInfoForMultiple(globalIds: string[]): Promise<Map<string, ShareInfo[]>> {
    try {
      // Make parallel requests for all global IDs
      const promises = globalIds.map(async (globalId) => {
        const response = await getShareInfo(globalId);
        return { globalId, shares: response.shares };
      });

      const results = await Promise.all(promises);
      
      // Convert to Map for easy lookup
      const shareMap = new Map<string, ShareInfo[]>();
      results.forEach(({ globalId, shares }) => {
        shareMap.set(globalId, shares);
      });

      return shareMap;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error fetching sharing information",
          message: getErrorMessage(e, "An unknown error occurred."),
        })
      );
      throw new Error("Could not fetch sharing information for multiple items", {
        cause: e,
      });
    }
  }

  return { getShareInfo, getShareInfoForMultiple };
}
