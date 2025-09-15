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
  recipientType: "USER" | "GROUP";
  recipientId: ShareInfo["recipientId"];
  recipientName: ShareInfo["recipientName"];
  permission: "READ" | "EDIT";
  locationName: string | null; // For group shares
  locationId: number | null; // For group shares
};

export type ShareInfo = {
  shareId: number;
  sharedDocId: number;
  sharedDocName: string;
  sharerId: number;
  sharerName: string;
  permission: "READ" | "EDIT";
  recipientType: "USER" | "GROUP";
  recipientId: number;
  recipientName: string;
  locationId: number | null;
  locationName: string | null;
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
  updateShare: (shareInfo: ShareInfo) => Promise<void>;

  /**
   * Deletes a single share by its ID.
   */
  deleteShare: (shareId: number) => Promise<void>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  async function getShareInfo(globalId: string): Promise<ShareInfoResponse> {
    try {
      const { data } = await axios.get<ShareInfoResponse>(
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
          .filter((share) => share.recipientType === "USER")
          .map((share) => ({
            id: share.recipientId,
            permission: share.permission,
          })),
        groups: ArrayUtils.mapOptional<NewShare, [NewShare, number]>(
          (share) =>
            share.recipientType === "GROUP" && share.locationId !== null
              ? Optional.present([share, share.locationId])
              : Optional.empty(),
          newShares,
        ).map(([share, sharedFolderId]) => ({
          id: share.recipientId,
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

  async function updateShare(shareInfo: ShareInfo): Promise<void> {
    try {
      await axios.put<void>(
        `/api/v1/share`,
        { shareId: shareInfo.shareId, permission: shareInfo.permission },
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
            "Content-Type": "application/json",
          },
        },
      );
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
