import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import useOauthToken from "../../hooks/auth/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import type { Filestore } from "./useGalleryListing";

/** A composed metadata sidecar: the target filename and its serialized (YAML) content. */
export type Sidecar = { filename: string; content: string };

export default function useFilestoresEndpoint(): {
  logout: (filestore: Filestore) => Promise<void>;
  previewSidecar: (filestoreId: number, folderPath: string) => Promise<Sidecar>;
  saveSidecar: (filestoreId: number, folderPath: string) => Promise<Sidecar>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation("gallery");

  const authHeader = async () => ({ Authorization: `Bearer ${await getToken()}` });

  const previewSidecar = async (filestoreId: number, folderPath: string): Promise<Sidecar> => {
    const { data } = await axios.post<Sidecar>(
      `/api/v1/gallery/filestores/${filestoreId}/sidecar/preview`,
      { path: folderPath },
      { headers: await authHeader() },
    );
    return data;
  };

  const saveSidecar = async (filestoreId: number, folderPath: string): Promise<Sidecar> => {
    const { data } = await axios.post<Sidecar>(
      `/api/v1/gallery/filestores/${filestoreId}/sidecar`,
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
    previewSidecar,
    saveSidecar,
  };
}
