import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  type WorkspaceRecordInformation,
  WorkspaceGetRecordInformationResponseSchema,
} from "@/modules/workspace/schema";
import { toWorkspaceError } from "@/modules/workspace/utils";

const GALLERY_UPLOAD_URL = "/gallery/ajax/uploadFile";

export type UploadNewGalleryVersionParams = {
  mediaId: number;
  file: File;
};

/**
 * Replace a gallery media file with a new version, via multipart
 * `POST /gallery/ajax/uploadFile` with `xfile` (the new file) and
 * `selectedMediaId` (the existing media id). Returns the updated record information.
 * Callers should invalidate the record-info query for this id on success.
 */
export async function uploadNewGalleryVersion({
  mediaId,
  file,
}: UploadNewGalleryVersionParams): Promise<WorkspaceRecordInformation> {
  const formData = new FormData();
  formData.set("xfile", file);
  formData.set("selectedMediaId", String(mediaId));

  const response = await fetch(GALLERY_UPLOAD_URL, {
    method: "POST",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
    body: formData,
  });

  const data: unknown = await response.json();

  if (!response.ok) {
    throw toWorkspaceError(
      data,
      `Failed to upload new version: ${response.statusText || response.status}`,
    );
  }

  const parsed = parseOrThrow(WorkspaceGetRecordInformationResponseSchema, data);

  if (parsed.data === null) {
    throw toWorkspaceError(parsed, "No record information was returned.");
  }

  return parsed.data;
}
