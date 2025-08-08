import React from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";
import useOauthToken from "../auth/useOauthToken";

export type GroupMember = {
  id: number;
  username: string;
  role: "PI" | "USER";
};

export type Group = {
  id: number;
  globalId: string;
  name: string;
  type: string;
  sharedFolderId: number;
  members: GroupMember[];
  uniqueName: string;
  _links: Array<{
    link: string;
    rel: string;
  }>;
};

/**
 * This custom hook provides functionality to fetch groups that the current user is a member of
 * using the `/groups` endpoint.
 */
export default function useGroups(): {
  /**
   * Fetches all groups that the current user is a member of.
   */
  getGroups: () => Promise<ReadonlyArray<Group>>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  const getGroups = React.useCallback(async (): Promise<
    ReadonlyArray<Group>
  > => {
    try {
      const { data } = await axios.get<ReadonlyArray<Group>>(`/api/v1/groups`, {
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
      return data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error fetching groups",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not fetch groups", {
        cause: e,
      });
    }
  }, [getToken, addAlert]);

  return { getGroups };
}
