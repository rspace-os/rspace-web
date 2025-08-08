import React from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";
import useOauthToken from "../auth/useOauthToken";

export type GroupMember = {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  homeFolderId: number;
  workbenchId: number;
  hasPiRole: boolean;
  hasSysAdminRole: boolean;
  _links: Array<{
    link: string;
    rel: string;
  }>;
};

/**
 * This custom hook provides functionality to fetch user details from various userDetails endpoints
 */
export default function useUserDetails(): {
  /**
   * Fetches all members from all groups that the current user is a member of.
   */
  getGroupMembers: () => Promise<ReadonlyArray<GroupMember>>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  const getGroupMembers = React.useCallback(async (): Promise<
    ReadonlyArray<GroupMember>
  > => {
    try {
      const { data } = await axios.get<ReadonlyArray<GroupMember>>(
        `/api/v1/userDetails/groupMembers`,
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
          title: "Error fetching group members",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not fetch group members", {
        cause: e,
      });
    }
  }, [getToken, addAlert]);

  return { getGroupMembers };
}
