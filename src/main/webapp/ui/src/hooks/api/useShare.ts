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
  shareeId: ShareInfo["shareeId"];
  shareeName: ShareInfo["shareeName"];
  sharedTargetType: "USER" | "GROUP";
  permission: "READ" | "EDIT";
  sharedFolderName: string | null;
  sharedFolderId?: number; // For group shares
};

export type ShareInfo = {
  id: number;
  sharedItemId: number;
  shareItemName: string;
  sharedTargetType: "USER" | "GROUP";
  permission: "READ" | "EDIT";
  sharedToFolderId: number | null;
  shareeId: number;
  shareeName: string;
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

/**
 * This custom hook provides functionality to fetch sharing information
 * using the `/api/v1/share` endpoint.
 */
export default function useShare(): {
  /**
   * Fetches sharing information for a specific global ID.
   */
  getShareInfo: (globalId: string) => Promise<ReadonlyArray<ShareInfo>>;

  /**
   * Fetches sharing information for multiple global IDs.
   */
  getShareInfoForMultiple: (
    globalIds: ReadonlyArray<string>,
  ) => Promise<Map<string, ReadonlyArray<ShareInfo>>>;

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

  async function getShareInfo(
    globalId: string,
  ): Promise<ReadonlyArray<ShareInfo>> {
    try {
      const { data } = await axios.get<ReadonlyArray<ShareInfo>>(
        `/api/v1/share/document/${globalId.slice(2)}`,
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
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
    globalIds: ReadonlyArray<string>,
  ): Promise<Map<string, ReadonlyArray<ShareInfo>>> {
    try {
      // Make parallel requests for all global IDs
      const promises = globalIds.map(async (globalId) => {
        const shares = await getShareInfo(globalId);
        return { globalId, shares };
      });

      const results = await Promise.all(promises);

      // Convert to Map for easy lookup
      const shareMap = new Map<string, ReadonlyArray<ShareInfo>>();
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
            id: share.shareeId,
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
          id: share.shareeId,
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
