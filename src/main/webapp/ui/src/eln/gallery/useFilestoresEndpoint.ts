import React from "react";
import axios from "@/common/axios";
import useOauthToken from "../../common/useOauthToken";
import { type Filestore } from "./useGalleryListing";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

export default function useFilestoresEndpoint(): {
  logout: (filestore: Filestore) => Promise<void>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  const logout = async (filestore: Filestore) => {
    try {
      await axios.post<unknown>(
        `/api/v1/gallery/filesystems/${filestore.filesystemId}/logout`,
        {},
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        }
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: `Logged out of ${filestore.name}`,
        })
      );
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          message: `Failed to log out of ${filestore.name}`,
        })
      );
    }
  };

  return {
    logout,
  };
}
