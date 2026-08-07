import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import useOauthToken from "../../hooks/auth/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import type { Filestore } from "./useGalleryListing";

/** A composed metadata sidecar: the target filename and its serialized (YAML) content. */
export type SidecarFile = { filename: string; content: string };

export default function useFilestoresEndpoint(): {
  logout: (filestore: Filestore) => Promise<void>;
  previewSidecarFile: (filestoreId: number, folderPath: string) => Promise<SidecarFile>;
  saveSidecarFile: (filestoreId: number, folderPath: string) => Promise<SidecarFile>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation("gallery");

  const authHeader = async () => ({ Authorization: `Bearer ${await getToken()}` });

  const previewSidecarFile = async (filestoreId: number, folderPath: string): Promise<SidecarFile> => {
    const { data } = await axios.post<SidecarFile>(
      `/api/v1/gallery/filestores/${filestoreId}/sidecarFile/preview`,
      { path: folderPath },
      { headers: await authHeader() },
    );
    return data;
  };

  const saveSidecarFile = async (filestoreId: number, folderPath: string): Promise<SidecarFile> => {
    const { data } = await axios.post<SidecarFile>(
      `/api/v1/gallery/filestores/${filestoreId}/sidecarFile`,
      { path: folderPath },
      { headers: await authHeader() },
    );
    return data;
  };

  const logout = async (filestore: Filestore) => {
    try {
      await axios.post<unknown>(
        `/api/v1/gallery/filesystems/${filestore.filesystemId}/logout`,
        {},
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: t("filestores.logoutSuccess", { name: filestore.name }),
        }),
      );
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          message: t("filestores.logoutFailed", { name: filestore.name }),
        }),
      );
    }
  };

  return {
    logout,
    previewSidecarFile,
    saveSidecarFile,
  };
}
