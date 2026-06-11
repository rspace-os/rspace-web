import React, { useRef, useState } from "react";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import {
  type WorkspaceRecordInformation,
  type LinkedRecords,
} from "@/modules/workspace/schema";
import { getLinkedDocuments } from "@/modules/workspace/linkedRecords";
import { uploadNewGalleryVersion } from "@/modules/workspace/galleryUpload";
import { formatFileSize } from "@/util/files";
import RelatedInventoryItems from "./RelatedInventoryItems";

export interface GallerySectionsProps {
  info: WorkspaceRecordInformation;
  /** Called after a successful upload-new-version so the shell can refetch. */
  onRecordChanged: () => void;
}

/** One label/value row of the core metadata table. */
function MetaRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <TableRow>
      <TableCell
        component="th"
        scope="row"
        sx={{ color: "text.secondary", width: "10rem", verticalAlign: "top" }}
      >
        {label}
      </TableCell>
      <TableCell>{children}</TableCell>
    </TableRow>
  );
}

/** A `/globalId/<id>` link that opens in a new tab. */
function GlobalIdLink({
  globalId,
  children,
}: {
  globalId: string;
  children?: React.ReactNode;
}): React.ReactElement {
  return (
    <Link
      href={`/globalId/${globalId}`}
      target="_blank"
      rel="noopener noreferrer"
    >
      {children ?? globalId}
    </Link>
  );
}

/**
 * Gallery-file body for {@link EnElnRecordInfoDialog}. Mirrors the ELN
 * `#recordInfoDialog` for GL targets: the core metadata table, an image thumbnail
 * preview, and the Download / Show-linked-docs / Upload-new-version actions.
 */
export default function GallerySections({
  info,
  onRecordChanged,
}: GallerySectionsProps): React.ReactElement {
  const isRevisionView = info.revision != null;
  const isImage = info.type === "Image";
  // The ELN gates upload on `!isRevisionView && (owner || VIEW_MODE)`. `VIEW_MODE` is
  // the server's editable signal (returned for owners too), so gating on it covers the
  // common owner case without wiring bearer-token whoami plumbing into this dialog.
  const canUpload = !isRevisionView && info.status === "VIEW_MODE";

  const [linked, setLinked] = useState<LinkedRecords | null>(null);
  const [linkedLoading, setLinkedLoading] = useState(false);
  // Mounting RelatedInventoryItems triggers its fetch, so deferring the mount
  // until the user asks for linked docs keeps the dialog itself lazy.
  const [showRelatedInventory, setShowRelatedInventory] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Cache-buster so the thumbnail refreshes after an upload-new-version.
  const thumbnailSrc = `/gallery/getThumbnail/${info.id}/${
    info.modificationDate ?? info.version ?? 0
  }`;
  const downloadHref = isRevisionView
    ? `/Streamfile/${info.id}?revision=${info.revision}`
    : `/Streamfile/${info.id}`;

  function handleShowLinkedDocs(): void {
    setShowRelatedInventory(true);
    setLinkedLoading(true);
    void getLinkedDocuments(info.id)
      .then(setLinked)
      .finally(() => setLinkedLoading(false));
  }

  function handleFilePicked(
    event: React.ChangeEvent<HTMLInputElement>,
  ): void {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    void uploadNewGalleryVersion({ mediaId: info.id, file })
      .then(() => {
        onRecordChanged();
      })
      .catch((e: unknown) => {
        setUploadError(e instanceof Error ? e.message : "Upload failed.");
      })
      .finally(() => setUploading(false));
  }

  return (
    <Box>
      <Typography variant="h6" component="div" sx={{ mb: 1 }}>
        {info.name}
      </Typography>

      <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
        <Box sx={{ flex: "1 1 18rem" }}>
          <Table size="small">
            <TableBody>
              <MetaRow label="Unique Id">
                <GlobalIdLink globalId={info.oid.idString} />
              </MetaRow>
              <MetaRow label="Type">{info.type}</MetaRow>
              {info.version != null && (info.version > 1 || isRevisionView) ? (
                <MetaRow label="Version">{info.version}</MetaRow>
              ) : null}
              {info.size != null ? (
                <MetaRow label="File size">{formatFileSize(info.size)}</MetaRow>
              ) : null}
              {info.extension ? (
                <MetaRow label="Extension">{info.extension}</MetaRow>
              ) : null}
              <MetaRow label="Owner">{info.ownerFullName}</MetaRow>
              <MetaRow label="Creation Date">
                {info.creationDateWithClientTimezoneOffset}
              </MetaRow>
              <MetaRow label="Last Modified">
                {info.modificationDateWithClientTimezoneOffset}
              </MetaRow>
              {info.originalImageOid ? (
                <MetaRow label="Original Image">
                  <GlobalIdLink globalId={info.originalImageOid.idString} />
                </MetaRow>
              ) : null}
              {info.description ? (
                <MetaRow label="Caption">{info.description}</MetaRow>
              ) : null}
            </TableBody>
          </Table>
        </Box>

        {isImage ? (
          <Box sx={{ flex: "0 0 auto" }}>
            <Typography variant="subtitle2">Preview</Typography>
            <Box
              component="img"
              src={thumbnailSrc}
              alt={info.name}
              sx={{
                maxWidth: 250,
                maxHeight: 250,
                border: "1px solid",
                borderColor: "divider",
              }}
            />
          </Box>
        ) : null}
      </Box>

      <Box sx={{ display: "flex", gap: 1, mt: 1, flexWrap: "wrap" }}>
        <Button
          size="small"
          href={downloadHref}
          target="_blank"
          rel="noopener noreferrer"
        >
          Download
        </Button>
        {!isRevisionView ? (
          <Button
            size="small"
            disabled={linkedLoading}
            onClick={handleShowLinkedDocs}
          >
            Show linked docs
          </Button>
        ) : null}
        {canUpload ? (
          <>
            <Button
              size="small"
              disabled={uploading}
              onClick={() => fileInputRef.current?.click()}
            >
              {uploading ? "Uploading…" : "Upload new version"}
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              data-testid="gallery-upload-input"
              accept={info.extension ? `.${info.extension}` : undefined}
              style={{ display: "none" }}
              onChange={handleFilePicked}
            />
          </>
        ) : null}
      </Box>

      {uploadError ? (
        <Typography variant="body2" color="error" sx={{ mt: 1 }}>
          {uploadError}
        </Typography>
      ) : null}

      {linked !== null ? (
        <Box sx={{ mt: 1 }}>
          {linked.readable.length === 0 ? (
            <Typography variant="body2">
              There are no references to this file.
            </Typography>
          ) : (
            <>
              <Typography variant="body2">
                This file is referenced by:
              </Typography>
              <Box component="ul" sx={{ pl: 3, my: 0.5 }}>
                {linked.readable.map((r) => (
                  <li key={r.globalId}>
                    <GlobalIdLink globalId={r.globalId} />: {r.name}
                  </li>
                ))}
              </Box>
            </>
          )}
        </Box>
      ) : null}

      {showRelatedInventory ? (
        <RelatedInventoryItems
          globalId={info.oid.idString}
          recordTypeName="file"
        />
      ) : null}
    </Box>
  );
}
