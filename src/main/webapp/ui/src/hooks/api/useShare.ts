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

export type ShareInfoResponse = {
  sharedDocId: number;
  sharedDocName: string;
  directShares: ReadonlyArray<ShareInfo>;
  notebookShares: ReadonlyArray<ShareInfo>;
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
  getShareInfo: (globalId: string) => Promise<ShareInfoResponse>;

  /**
   * Fetches sharing information for multiple global IDs.
   */
  getShareInfoForMultiple: (globalIds: ReadonlyArray<string>) => Promise<
    Map<
      string,
      {
        directShares: ReadonlyArray<ShareInfo>;
        notebookShares: ReadonlyArray<ShareInfo>;
      }
    >
  >;

  /**
   * Creates new shares for an item.
   */
  createShare: (itemId: number, newShares: NewShare[]) => Promise<void>;

  /**
   * Updates an existing share.
   */
  updateShare: (shareInfo: ShareInfo) => Promise<ShareInfo>;

  /**
   * Deletes a single share by its ID.
   */
  deleteShare: (shareId: number) => Promise<void>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  async function getShareInfo(globalId: string): Promise<ShareInfoResponse> {
    try {
      const { data } = await axios.get<ReadonlyArray<ShareInfo>>(
        `/api/v1/share/document/${globalId.slice(2)}`,
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      return {
        sharedDocId: parseInt(globalId.slice(2), 10),
        sharedDocName: data.length > 0 ? data[0].shareItemName : "Unknown",
        directShares: data,
        notebookShares: [
          // Placeholder for future notebook shares
          {
            id: 0,
            sharedItemId: 0,
            shareItemName: "Notebook Share Placeholder",
            sharedTargetType: "GROUP",
            permission: "READ",
            shareeId: 1,
            shareeName: "userGroup",
            sharedToFolderId: null,
          },
        ],
      };
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
  ): Promise<
    Map<
      string,
      {
        directShares: ReadonlyArray<ShareInfo>;
        notebookShares: ReadonlyArray<ShareInfo>;
      }
    >
  > {
    try {
      // Make parallel requests for all global IDs
      const promises = globalIds.map(async (globalId) => {
        const { directShares, notebookShares } = await getShareInfo(globalId);
        return { globalId, directShares, notebookShares };
      });

      const results = await Promise.all(promises);

      // Convert to Map for easy lookup
      const shareMap = new Map<
        string,
        {
          directShares: ReadonlyArray<ShareInfo>;
          notebookShares: ReadonlyArray<ShareInfo>;
        }
      >();
      results.forEach(({ globalId, directShares, notebookShares }) => {
        shareMap.set(globalId, { directShares, notebookShares });
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

  async function updateShare(shareInfo: ShareInfo): Promise<ShareInfo> {
    try {
      const requestData: CreateShareRequest = {
        itemsToShare: [shareInfo.sharedItemId],
        users:
          shareInfo.sharedTargetType === "USER"
            ? [
                {
                  id: shareInfo.shareeId,
                  permission: shareInfo.permission,
                },
              ]
            : [],
        groups:
          shareInfo.sharedTargetType === "GROUP"
            ? [
                {
                  id: shareInfo.shareeId,
                  permission: shareInfo.permission,
                  sharedFolderId: shareInfo.sharedToFolderId!,
                },
              ]
            : [],
      };

      const { data } = await axios.put<ShareInfo>(
        `/api/v1/share/${shareInfo.id}`,
        requestData,
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
            "Content-Type": "application/json",
          },
        },
      );
      return data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error updating share",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not update share", {
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

  return {
    getShareInfo,
    getShareInfoForMultiple,
    createShare,
    updateShare,
    deleteShare,
  };
}
