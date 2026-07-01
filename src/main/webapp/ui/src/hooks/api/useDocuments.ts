import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { getErrorMessage } from "@/util/error";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import useOauthToken from "../auth/useOauthToken";

type MoveDocumentRequest = {
  documentId: number;
  sourceFolderId: number;
  destinationFolderId: number;
  currentGrandparentId?: number;
};

/**
 * This custom hook provides functionality to manage documents
 * using the `/api/v1/documents` endpoint.
 */
export default function useDocuments(): {
  /**
   * Moves a document from one folder to another.
   */
  move: (params: MoveDocumentRequest) => Promise<void>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation();

  async function move(moveRequest: MoveDocumentRequest): Promise<void> {
    try {
      await axios.post(
        "/api/v1/documents/move",
        {
          docId: moveRequest.documentId,
          sourceFolderId: moveRequest.sourceFolderId,
          targetFolderId: moveRequest.destinationFolderId,
          ...(moveRequest.currentGrandparentId
            ? {
                currentGrandparentId: moveRequest.currentGrandparentId,
              }
            : {}),
        },
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
          title: t("apiErrors.documents.moveFailed"),
          message: getErrorMessage(e, t("apiErrors.unknown")),
        }),
      );
      throw new Error("Could not move document", {
        cause: e,
      });
    }
  }

  return {
    move,
  };
}
