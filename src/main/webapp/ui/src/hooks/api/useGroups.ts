import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { getErrorMessage } from "@/util/error";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
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
  sharedSnippetFolderId: number;
  members: GroupMember[];
  uniqueName: string;
  _links: Array<{
    link: string;
    rel: string;
  }>;
};

export type GroupDetail = {
  id: number;
  globalId: string;
  name: string;
  type: string;
  sharedFolderId: number;
  sharedSnippetFolderId: number;
  members: Array<{
    id: number;
    username: string;
    role: "PI" | "USER";
  }>;
  uniqueName: string;
  _links: ReadonlyArray<{
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
  /**
   * Fetches a specific group by ID.
   */
  getGroup: (groupId: number) => Promise<GroupDetail>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation();

  const getGroups = React.useCallback(async (): Promise<ReadonlyArray<Group>> => {
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
          title: t("apiErrors.groups.fetchManyFailed"),
          message: getErrorMessage(e, t("apiErrors.unknown")),
        }),
      );
      throw new Error(t("apiErrors.groups.fetchManyFailed"), {
        cause: e,
      });
    }
  }, [getToken, addAlert, t]);

  const getGroup = React.useCallback(
    async (groupId: number): Promise<GroupDetail> => {
      try {
        const { data } = await axios.get<GroupDetail>(`/api/v1/groups/${groupId}`, {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        });
        return data;
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: t("apiErrors.groups.fetchOneFailed"),
            message: getErrorMessage(e, t("apiErrors.unknown")),
          }),
        );
        throw new Error(t("apiErrors.groups.fetchOneFailed"), {
          cause: e,
        });
      }
    },
    [getToken, addAlert, t],
  );

  return { getGroups, getGroup };
}
