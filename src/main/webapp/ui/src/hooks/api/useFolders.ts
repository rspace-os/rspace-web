import React from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";
import useOauthToken from "../auth/useOauthToken";

export type FolderRecord = {
  id: number;
  globalId: string;
  name: string;
  type: string;
};

export type FolderTreeResponse = {
  totalHits: number;
  pageNumber: number;
  records: ReadonlyArray<FolderRecord>;
};

export type FolderResponse = {
  id: number;
  globalId: string;
  name: string;
  created: string;
  lastModified: string;
  parentFolderId: number | null;
  notebook: boolean;
  mediaType: string | null;
  pathToRootFolder: string | null;
  _links: Array<{
    link: string;
    rel: string;
  }>;
};

type InternalFolderTreeResponse = {
  totalHits: number;
  pageNumber: number;
  _links: Array<{
    link: string;
    rel: string;
  }>;
  parentId: number | null;
  records: Array<{
    id: number;
    globalId: string;
    name: string;
    created: string;
    lastModified: string;
    parentFolderId: number;
    type: string;
    _links: Array<{
      link: string;
      rel: string;
    }>;
    owner: {
      id: number;
      username: string;
      email: string;
      firstName: string;
      lastName: string;
      homeFolderId: number;
      workbenchId: number | null;
      hasPiRole: boolean;
      hasSysAdminRole: boolean;
      _links: Array<unknown>;
    };
  }>;
};

export default function useFolders(): {
  getFolderTree: (
    id?: number,
    typesToInclude?: "document" | "notebook" | "folder",
    pageNumber?: number,
    pageSize?: number,
  ) => Promise<FolderTreeResponse>;
  getFolder: (id: number) => Promise<FolderResponse>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  const getFolderTree = React.useCallback(
    async (
      id?: number,
      typesToInclude?: "document" | "notebook" | "folder",
      pageNumber?: number,
      pageSize: number = 20,
    ): Promise<FolderTreeResponse> => {
      try {
        const endpoint = id
          ? `/api/v1/folders/tree/${id}`
          : `/api/v1/folders/tree`;

        const params = new URLSearchParams();
        if (typesToInclude) {
          params.append("typesToInclude", typesToInclude);
        }
        if (pageNumber !== undefined) {
          params.append("pageNumber", pageNumber.toString());
        }
        params.append("pageSize", pageSize.toString());

        const { data } = await axios.get<InternalFolderTreeResponse>(endpoint, {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
          params,
        });

        return {
          totalHits: data.totalHits,
          pageNumber: data.pageNumber,
          records: data.records.map((record) => ({
            id: record.id,
            globalId: record.globalId,
            name: record.name,
            type: record.type,
          })),
        };
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error fetching folder tree",
            message: getErrorMessage(e, "An unknown error occurred."),
          }),
        );
        throw new Error("Could not fetch folder tree", {
          cause: e,
        });
      }
    },
    [getToken, addAlert],
  );

  const getFolder = React.useCallback(
    async (id: number): Promise<FolderResponse> => {
      try {
        const { data } = await axios.get<FolderResponse>(
          `/api/v1/folders/${id}`,
          {
            headers: {
              Authorization: `Bearer ${await getToken()}`,
            },
            params: {
              includePathToRootFolder: "true",
            },
          },
        );

        return data;
      } catch (e) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error fetching folder",
            message: getErrorMessage(e, "An unknown error occurred."),
          }),
        );
        throw new Error("Could not fetch folder", {
          cause: e,
        });
      }
    },
    [getToken, addAlert],
  );

  return { getFolderTree, getFolder };
}
