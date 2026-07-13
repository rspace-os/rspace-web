import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import useOauthToken from "../../hooks/auth/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import type { Filestore } from "./useGalleryListing";

export default function useFilestoresEndpoint(): {
  logout: (filestore: Filestore) => Promise<void>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation("gallery");

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
  };
}
