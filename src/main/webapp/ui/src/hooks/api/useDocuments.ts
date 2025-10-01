import React from "react";
import axios from "@/common/axios";
import useOauthToken from "../auth/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";

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
          title: "Error moving document",
          message: getErrorMessage(e, "An unknown error occurred."),
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
