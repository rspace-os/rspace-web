import React from "react";
import axios from "@/common/axios";
import useOauthToken from "../auth/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";
import { Group } from "./useGroups";
import { GroupMember } from "./useUserDetails";
import * as ArrayUtils from "../../util/ArrayUtils";
import { Optional } from "../../util/optional";

export type ShareOption =
  | (Group & { optionType: "GROUP" })
  | (GroupMember & { optionType: "USER" });

export type NewShare = {
  id: string; // temporary ID for React keys
  sharedTargetId: number;
  sharedTargetName: string;
  sharedTargetDisplayName: string;
  sharedTargetType: "USER" | "GROUP";
  permission: "READ" | "EDIT";
  sharedFolderPath: string | null;
  sharedFolderId?: number; // For group shares
};

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

type CreateShareRequest = {
  itemsToShare: number[];
  users: Array<{
    id: number;
    permission: "READ" | "EDIT";
  }>;
  groups: Array<{
    id: number;
    permission: "READ" | "EDIT";
    sharedFolderId: number;
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
  getShareInfoForMultiple: (
    globalIds: string[],
  ) => Promise<Map<string, ShareInfo[]>>;

  /**
   * Creates new shares for an item.
   */
  createShare: (itemId: number, newShares: NewShare[]) => Promise<void>;

  /**
   * Deletes a single share by its ID.
   */
  deleteShare: (shareId: number) => Promise<void>;
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
        }),
      );
      throw new Error("Could not fetch sharing information", {
        cause: e,
      });
    }
  }

  async function getShareInfoForMultiple(
    globalIds: string[],
  ): Promise<Map<string, ShareInfo[]>> {
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
        }),
      );
      throw new Error(
        "Could not fetch sharing information for multiple items",
        {
          cause: e,
        },
      );
    }
  }

  async function createShare(
    itemId: number,
    newShares: NewShare[],
  ): Promise<void> {
    try {
      const requestData: CreateShareRequest = {
        itemsToShare: [itemId],
        users: newShares
          .filter((share) => share.sharedTargetType === "USER")
          .map((share) => ({
            id: share.sharedTargetId,
            permission: share.permission,
          })),
        groups: ArrayUtils.mapOptional<NewShare, [NewShare, number]>(
          (share) =>
            share.sharedTargetType === "GROUP" &&
            share.sharedFolderId !== undefined
              ? Optional.present([share, share.sharedFolderId])
              : Optional.empty(),
          newShares,
        ).map(([share, sharedFolderId]) => ({
          id: share.sharedTargetId,
          permission: share.permission,
          sharedFolderId,
        })),
      };

      await axios.post("/api/v1/share", requestData, {
        headers: {
          Authorization: `Bearer ${await getToken()}`,
          "Content-Type": "application/json",
        },
      });
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error creating share",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not create share", {
        cause: e,
      });
    }
  }

  async function deleteShare(shareId: number): Promise<void> {
    try {
      await axios.delete(`/api/v1/share/${shareId}`, {
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error deleting share",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not delete share", {
        cause: e,
      });
    }
  }

  return { getShareInfo, getShareInfoForMultiple, createShare, deleteShare };
}
