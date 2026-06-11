import React, { useEffect, useState } from "react";
import DOMPurify from "dompurify";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import {
  type WorkspaceRecordInformation,
  type WorkspaceRecordEditStatus,
  type WorkspaceRecordSignatureStatus,
  type LinkedRecords,
} from "@/modules/workspace/schema";
import { getLinkedByRecords } from "@/modules/workspace/linkedRecords";
import { getPublicLink } from "@/modules/workspace/publicLink";
import { getStructuredDocumentPreviewHtml } from "@/modules/workspace/documentPreview";
import RelatedInventoryItems from "./RelatedInventoryItems";

export interface DocumentSectionsProps {
  info: WorkspaceRecordInformation;
  /** True for notebook (NB) targets; false for structured documents (SD). */
  isNotebook: boolean;
  /**
   * When the link is pinned to a specific SD version, the version number. When set, the
   * dialog is a "version view": a warning header is shown and the preview is hidden,
   * mirroring the ELN record-info panel.
   */
  pinnedVersion?: number | null;
}

// ELN status/signature labels (mirrors recordInfoPanel.js).
const STATUS_LABELS: Record<WorkspaceRecordEditStatus, string> = {
  VIEW_MODE: "viewable & editable",
  EDIT_MODE: "currently edited by you",
  CANNOT_EDIT_OTHER_EDITING: "edit in progress",
  CANNOT_EDIT_NO_PERMISSION: "viewable",
  CAN_NEVER_EDIT: "read-only",
};

const SIGNATURE_LABELS: Record<WorkspaceRecordSignatureStatus, string> = {
  UNSIGNED: "unsigned",
  SIGNED_AND_LOCKED: "signed",
  AWAITING_WITNESS: "signed, awaiting witness",
  WITNESSED: "signed and witnessed",
  UNSIGNABLE: "unsignable",
  SIGNED_AND_LOCKED_WITNESSES_DECLINED: "signed, all witnesses declined",
};

/** Status label, mirroring recordInfoPanel.js (appends the editor for in-progress edits). */
function statusLabel(
  status: WorkspaceRecordEditStatus,
  currentEditor: string | null | undefined,
): string {
  if (status === "CANNOT_EDIT_OTHER_EDITING" && currentEditor) {
    return `edit in progress by ${currentEditor}`;
  }
  return STATUS_LABELS[status];
}

/** Undo the server's tag encoding the way recordInfoPanel.js does. */
function formatTags(tags: string | null | undefined): string {
  if (!tags) return "";
  return tags
    .replaceAll(",", ", ")
    .replaceAll("__rspactags_forsl__", "/")
    .replaceAll("__rspactags_comma__", ",");
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

/** The lazy "linked by N docs" section (Link group 2). */
function LinkedByDocs({
  info,
  recordTypeName,
}: {
  info: WorkspaceRecordInformation;
  recordTypeName: string;
}): React.ReactElement {
  const [linked, setLinked] = useState<LinkedRecords | null>(null);
  const [loading, setLoading] = useState(false);
  const count = info.linkedByCount ?? 0;

  if (!count) {
    return (
      <Typography variant="body2">
        There are no links to this {recordTypeName}.
      </Typography>
    );
  }

  const docOrDocs = count === 1 ? "doc" : "docs";

  if (linked === null) {
    return (
      <Box>
        <Typography variant="body2">
          This {recordTypeName} is linked by {count} {docOrDocs}.
        </Typography>
        <Button
          size="small"
          disabled={loading}
          onClick={() => {
            setLoading(true);
            void getLinkedByRecords(info.id)
              .then(setLinked)
              .finally(() => setLoading(false));
          }}
        >
          Show linked {docOrDocs}
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="body2">
        This {recordTypeName} is linked by:
      </Typography>
      <Box component="ul" sx={{ pl: 3, my: 0.5 }}>
        {linked.readable.map((r) => (
          <li key={r.globalId}>
            <GlobalIdLink globalId={r.globalId} />: {r.name}
          </li>
        ))}
        {linked.privateByOwner.map((p) => (
          <li key={p.ownerFullName}>
            {p.count} private {p.count === 1 ? "doc" : "docs"} belonging to{" "}
            {p.ownerFullName}
          </li>
        ))}
      </Box>
    </Box>
  );
}

/** The sharing + publication section (mirrors recordInfoPanel.js sharing HTML). */
function SharingAndPublication({
  info,
  isNotebook,
}: {
  info: WorkspaceRecordInformation;
  isNotebook: boolean;
}): React.ReactElement {
  const recordTypeName = isNotebook ? "notebook" : "document";
  const [publicLink, setPublicLink] = useState<string | null>(null);
  const [publicChecked, setPublicChecked] = useState(false);

  useEffect(() => {
    let active = true;
    void getPublicLink(info.oid.idString)
      .then((link) => {
        if (active) setPublicLink(link);
      })
      .finally(() => {
        if (active) setPublicChecked(true);
      });
    return () => {
      active = false;
    };
  }, [info.oid.idString]);

  const isShared = Boolean(info.shared) || Boolean(info.implicitlyShared);

  return (
    <Box sx={{ mt: 1 }}>
      <Typography variant="subtitle2">Sharing</Typography>
      {!isShared ? (
        <Typography variant="body2">
          This {recordTypeName} is not shared.
        </Typography>
      ) : (
        <>
          <Typography variant="body2">
            This {recordTypeName} is shared:
          </Typography>
          <Box component="ul" sx={{ pl: 3, my: 0.5 }}>
            {Object.entries(info.sharedGroupsAndAccess ?? {}).map(
              ([group, access]) => (
                <li key={`g-${group}`}>
                  with {group} (group) for {access.toLowerCase()}
                </li>
              ),
            )}
            {Object.entries(info.sharedUsersAndAccess ?? {}).map(
              ([user, access]) => (
                <li key={`u-${user}`}>
                  with {user} (user) for {access.toLowerCase()}
                </li>
              ),
            )}
            {Object.entries(info.sharedNotebooksAndOwners ?? {}).map(
              ([nb, owner]) => (
                <li key={`nb-${nb}`}>
                  into Notebook <GlobalIdLink globalId={nb} /> (owner: {owner})
                </li>
              ),
            )}
            {Object.entries(info.implicitShares ?? {}).map(([nb, owner]) => (
              <li key={`im-${nb}`}>
                implicitly - is in shared Notebook{" "}
                <GlobalIdLink globalId={nb} /> (shared with: {owner})
              </li>
            ))}
          </Box>
        </>
      )}
      {publicChecked &&
        (publicLink === null ? (
          <Typography variant="body2">
            This {recordTypeName} is not published.
          </Typography>
        ) : (
          <Typography variant="body2">
            {publicLink.includes("initialRecordToDisplay")
              ? "This document is in a published notebook:"
              : `This ${recordTypeName} is published:`}{" "}
            <Link
              href={`${window.location.origin}/public/publishedView/${
                publicLink.includes("initialRecordToDisplay") || isNotebook
                  ? "notebook"
                  : "document"
              }/${publicLink}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              public link
            </Link>
          </Typography>
        ))}
    </Box>
  );
}

// ELN content stylesheets, referenced at their served URLs from the preview iframe
// head (enumerated from structuredDocumentMainPanel.jsp + tinymce5_configuration.js).
const PREVIEW_STYLESHEETS = [
  "/styles/structuredDocument.css",
  "/styles/simplicity/typoEdit.css",
  "/scripts/tinymce/tinymce5109/plugins/codesample/css/prism.css",
];

/**
 * Scaled HTML preview for SD targets. The preview endpoint returns a bare content
 * fragment, so we render it inside a same-origin iframe whose head links the existing
 * ELN content stylesheets. The iframe is sandboxed WITHOUT `allow-scripts` (so embedded
 * document JS does not execute) but WITH `allow-same-origin` (so the linked CSS/images
 * load), and the body is scaled to 0.25 like the ELN preview. The fragment is
 * DOMPurify-sanitised before injection.
 */
function DocumentPreview({
  documentId,
}: {
  documentId: number;
}): React.ReactElement {
  const [srcDoc, setSrcDoc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    setFailed(false);
    setSrcDoc(null);
    void getStructuredDocumentPreviewHtml(documentId)
      .then((html) => {
        if (!active) return;
        const clean = DOMPurify.sanitize(html, { ADD_ATTR: ["target"] });
        const links = PREVIEW_STYLESHEETS.map(
          (href) => `<link rel="stylesheet" href="${href}" />`,
        ).join("");
        setSrcDoc(
          `<!DOCTYPE html><html><head><base target="_blank" />${links}` +
            `<style>body{transform:scale(0.25);transform-origin:0 0;width:900px;}</style>` +
            `</head><body>${clean}</body></html>`,
        );
      })
      .catch(() => {
        if (active) {
          setFailed(true);
        }
      });
    return () => {
      active = false;
    };
  }, [documentId]);

  return (
    <Box sx={{ mt: 1 }}>
      <Typography variant="subtitle2">Preview</Typography>
      {failed ? (
        <Typography variant="body2" color="error">
          The preview could not be loaded.
        </Typography>
      ) : srcDoc === null ? (
        <CircularProgress size={20} aria-label="Loading preview" />
      ) : (
        <Box
          component="iframe"
          title="Preview"
          srcDoc={srcDoc}
          // No allow-scripts: the embedded document JS must not run. allow-same-origin
          // lets the linked stylesheets and images load.
          sandbox="allow-same-origin"
          sx={{
            width: 250,
            height: 280,
            border: "1px solid",
            borderColor: "divider",
          }}
        />
      )}
    </Box>
  );
}

/**
 * Document/notebook body for {@link EnElnRecordInfoDialog}. Mirrors the ELN
 * `#recordInfoDialog` for SD/NB targets: the core metadata table, the three link groups
 * (self / linked-by / forms+template), related inventory items, sharing + publication,
 * and (SD only) the scaled HTML preview.
 */
export default function DocumentSections({
  info,
  isNotebook,
  pinnedVersion,
}: DocumentSectionsProps): React.ReactElement {
  const recordTypeName = isNotebook ? "notebook" : "document";
  const isStructuredDocument = !isNotebook;
  const isVersionView = pinnedVersion != null;
  // The version-stripped global id, used for the "latest" link in the version header.
  const unversionedGlobalId = info.oid.idString.replace(/v\d+$/, "");
  const tags = formatTags(info.tags);

  return (
    <Box>
      {isVersionView ? (
        <Box
          role="note"
          sx={{
            border: "1px solid",
            borderColor: "warning.main",
            borderRadius: 1,
            p: 1,
            mb: 1,
          }}
        >
          <Typography variant="body2">
            The information below describes{" "}
            <strong>version {pinnedVersion}</strong> of a document{" "}
            {unversionedGlobalId}, which may not be the latest version.
          </Typography>
        </Box>
      ) : null}
      <Typography variant="h6" component="div" sx={{ mb: 1 }}>
        {info.name}
      </Typography>
      <Table size="small">
        <TableBody>
          <MetaRow label="Unique Id">
            <GlobalIdLink
              globalId={
                isVersionView
                  ? `${unversionedGlobalId}v${pinnedVersion}`
                  : info.oid.idString
              }
            />
          </MetaRow>
          <MetaRow label="Type">{info.type}</MetaRow>
          {info.path ? <MetaRow label="Path">{info.path}</MetaRow> : null}
          {isStructuredDocument && info.version != null ? (
            <MetaRow label="Version">{info.version}</MetaRow>
          ) : null}
          <MetaRow label="Owner">{info.ownerFullName}</MetaRow>
          <MetaRow label="Creation Date">
            {info.creationDateWithClientTimezoneOffset}
          </MetaRow>
          <MetaRow label="Last Modified">
            {info.modificationDateWithClientTimezoneOffset}
          </MetaRow>
          {isStructuredDocument && info.status ? (
            <MetaRow label="Status">
              {statusLabel(info.status, info.currentEditor)}
            </MetaRow>
          ) : null}
          {isStructuredDocument && info.signatureStatus ? (
            <MetaRow label="Signature Status">
              {SIGNATURE_LABELS[info.signatureStatus]}
            </MetaRow>
          ) : null}
          {isStructuredDocument && info.templateFormName ? (
            <MetaRow label="Created from">{info.templateFormName}</MetaRow>
          ) : null}
          {isStructuredDocument && info.templateFormId ? (
            <MetaRow label="Form ID">
              <GlobalIdLink globalId={info.templateFormId.idString} />
            </MetaRow>
          ) : null}
          {isStructuredDocument && info.templateOid ? (
            <MetaRow label="Template Name">
              <GlobalIdLink globalId={info.templateOid}>
                {info.templateName ?? info.templateOid}
              </GlobalIdLink>
            </MetaRow>
          ) : null}
          {tags ? <MetaRow label="Tags">{tags}</MetaRow> : null}
        </TableBody>
      </Table>

      <Box sx={{ mt: 1 }}>
        <LinkedByDocs info={info} recordTypeName={recordTypeName} />
      </Box>

      <RelatedInventoryItems
        globalId={info.oid.idString}
        recordTypeName={recordTypeName}
      />

      <SharingAndPublication info={info} isNotebook={isNotebook} />

      {isStructuredDocument && !isVersionView && (
        <DocumentPreview documentId={info.id} />
      )}
    </Box>
  );
}
