import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useEffect, useState } from "react";
import { getLinkedByRecords } from "@/modules/workspace/linkedRecords";
import { getPublicLink } from "@/modules/workspace/publicLink";
import type {
  LinkedRecords,
  WorkspaceRecordEditStatus,
  WorkspaceRecordInformation,
  WorkspaceRecordSignatureStatus,
} from "@/modules/workspace/schema";
import { GlobalIdLink, MetaRow } from "./MetaTable";
import RelatedInventoryItems from "./RelatedInventoryItems";

export interface DocumentSectionsProps {
  info: WorkspaceRecordInformation;
  /** True for notebook (NB) targets; false for structured documents (SD). */
  isNotebook: boolean;
  /**
   * When the link is pinned to a specific SD version, the version number. When set, the
   * dialog is a "version view": a warning header is shown, mirroring the ELN
   * record-info panel.
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
function statusLabel(status: WorkspaceRecordEditStatus, currentEditor: string | null | undefined): string {
  if (status === "CANNOT_EDIT_OTHER_EDITING" && currentEditor) {
    return `edit in progress by ${currentEditor}`;
  }
  return STATUS_LABELS[status];
}

/** Undo the server's tag encoding the way recordInfoPanel.js does. */
function formatTags(tags: string | null | undefined): string {
  if (!tags) return "";
  return tags.replaceAll(",", ", ").replaceAll("__rspactags_forsl__", "/").replaceAll("__rspactags_comma__", ",");
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
    return <Typography variant="body2">There are no links to this {recordTypeName}.</Typography>;
  }

  const docOrDocs = count === 1 ? "doc" : "docs";

  const showLinked = async (): Promise<void> => {
    setLoading(true);
    try {
      setLinked(await getLinkedByRecords(info.id));
    } catch {
      // degrade to the empty state rather than an unhandled rejection
      setLinked({ readable: [], privateByOwner: [] });
    } finally {
      setLoading(false);
    }
  };

  if (linked === null) {
    return (
      <Box>
        <Typography variant="body2">
          This {recordTypeName} is linked by {count} {docOrDocs}.
        </Typography>
        <Button size="small" disabled={loading} onClick={() => void showLinked()}>
          Show linked {docOrDocs}
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="body2">This {recordTypeName} is linked by:</Typography>
      <List dense disablePadding sx={{ pl: 3, my: 0.5, listStyleType: "disc" }}>
        {linked.readable.map((r) => (
          <ListItem key={r.globalId} disableGutters sx={{ display: "list-item", py: 0 }}>
            <GlobalIdLink globalId={r.globalId} />: {r.name}
          </ListItem>
        ))}
        {linked.privateByOwner.map((p) => (
          <ListItem key={p.ownerFullName} disableGutters sx={{ display: "list-item", py: 0 }}>
            {p.count} private {p.count === 1 ? "doc" : "docs"} belonging to {p.ownerFullName}
          </ListItem>
        ))}
      </List>
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
      // no public link is the normal case for most records; treat a lookup
      // failure the same rather than leaving an unhandled rejection
      .catch(() => {})
      .finally(() => {
        if (active) setPublicChecked(true);
      });
    return () => {
      active = false;
    };
  }, [info.oid.idString]);

  const isShared = Boolean(info.shared) || Boolean(info.implicitlyShared);

  return (
    <Box>
      <Typography variant="subtitle2">Sharing</Typography>
      {!isShared ? (
        <Typography variant="body2">This {recordTypeName} is not shared.</Typography>
      ) : (
        <>
          <Typography variant="body2">This {recordTypeName} is shared:</Typography>
          <List dense disablePadding sx={{ pl: 3, my: 0.5, listStyleType: "disc" }}>
            {Object.entries(info.sharedGroupsAndAccess ?? {}).map(([group, access]) => (
              <ListItem key={`g-${group}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                with {group} (group) for {access.toLowerCase()}
              </ListItem>
            ))}
            {Object.entries(info.sharedUsersAndAccess ?? {}).map(([user, access]) => (
              <ListItem key={`u-${user}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                with {user} (user) for {access.toLowerCase()}
              </ListItem>
            ))}
            {Object.entries(info.sharedNotebooksAndOwners ?? {}).map(([nb, owner]) => (
              <ListItem key={`nb-${nb}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                into Notebook <GlobalIdLink globalId={nb} /> (owner: {owner})
              </ListItem>
            ))}
            {Object.entries(info.implicitShares ?? {}).map(([nb, owner]) => (
              <ListItem key={`im-${nb}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                implicitly - is in shared Notebook <GlobalIdLink globalId={nb} /> (shared with: {owner})
              </ListItem>
            ))}
          </List>
        </>
      )}
      {publicChecked &&
        (publicLink === null ? (
          <Typography variant="body2">This {recordTypeName} is not published.</Typography>
        ) : (
          <Typography variant="body2">
            {publicLink.includes("initialRecordToDisplay")
              ? "This document is in a published notebook:"
              : `This ${recordTypeName} is published:`}{" "}
            <Link
              href={`${window.location.origin}/public/publishedView/${
                publicLink.includes("initialRecordToDisplay") || isNotebook ? "notebook" : "document"
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

/**
 * Document/notebook body for {@link ElnRecordInfoDialog}. Mirrors the ELN
 * `#recordInfoDialog` for SD/NB targets: the core metadata table, the three link groups
 * (self / linked-by / forms+template), related inventory items, and sharing +
 * publication.
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
    <Stack spacing={2}>
      {isVersionView ? (
        <Box
          role="note"
          sx={{
            border: "1px solid",
            borderColor: "warning.main",
            borderRadius: 1,
            p: 1,
          }}
        >
          <Typography variant="body2">
            The information below describes <strong>version {pinnedVersion}</strong> of a document {unversionedGlobalId}
            , which may not be the latest version.
          </Typography>
        </Box>
      ) : null}
      <Box>
        <Typography variant="h6" component="div" sx={{ mb: 1 }}>
          {info.name}
        </Typography>
        <Table size="small">
          <TableBody>
            <MetaRow label="Unique Id">
              <GlobalIdLink globalId={isVersionView ? `${unversionedGlobalId}v${pinnedVersion}` : info.oid.idString} />
            </MetaRow>
            <MetaRow label="Type">{info.type}</MetaRow>
            {info.path ? <MetaRow label="Path">{info.path}</MetaRow> : null}
            {isStructuredDocument && info.version != null ? <MetaRow label="Version">{info.version}</MetaRow> : null}
            <MetaRow label="Owner">{info.ownerFullName}</MetaRow>
            <MetaRow label="Creation Date">{info.creationDateWithClientTimezoneOffset}</MetaRow>
            <MetaRow label="Last Modified">{info.modificationDateWithClientTimezoneOffset}</MetaRow>
            {isStructuredDocument && info.status ? (
              <MetaRow label="Status">{statusLabel(info.status, info.currentEditor)}</MetaRow>
            ) : null}
            {isStructuredDocument && info.signatureStatus ? (
              <MetaRow label="Signature Status">{SIGNATURE_LABELS[info.signatureStatus]}</MetaRow>
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
                <GlobalIdLink globalId={info.templateOid}>{info.templateName ?? info.templateOid}</GlobalIdLink>
              </MetaRow>
            ) : null}
            {tags ? <MetaRow label="Tags">{tags}</MetaRow> : null}
          </TableBody>
        </Table>
      </Box>

      <LinkedByDocs info={info} recordTypeName={recordTypeName} />

      <RelatedInventoryItems globalId={info.oid.idString} recordTypeName={recordTypeName} />

      <SharingAndPublication info={info} isNotebook={isNotebook} />
    </Stack>
  );
}
