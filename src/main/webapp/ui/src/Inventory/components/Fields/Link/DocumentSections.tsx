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
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { getLinkedByRecords } from "@/modules/workspace/linkedRecords";
import { getPublicLink } from "@/modules/workspace/publicLink";
import type { LinkedRecords, WorkspaceRecordInformation } from "@/modules/workspace/schema";
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
  const { t } = useTranslation("inventory");
  const [linked, setLinked] = useState<LinkedRecords | null>(null);
  const [loading, setLoading] = useState(false);
  const count = info.linkedByCount ?? 0;

  if (!count) {
    return (
      <Typography variant="body2">{t("fields.link.documentSections.linkedBy.noLinks", { recordTypeName })}</Typography>
    );
  }

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
          {t("fields.link.documentSections.linkedBy.linkedByCount", { recordTypeName, count })}
        </Typography>
        <Button size="small" disabled={loading} onClick={() => void showLinked()}>
          {t("fields.link.documentSections.linkedBy.showLinked", { count })}
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="body2">
        {t("fields.link.documentSections.linkedBy.isLinkedBy", { recordTypeName })}
      </Typography>
      <List dense disablePadding sx={{ pl: 3, my: 0.5, listStyleType: "disc" }}>
        {linked.readable.map((r) => (
          <ListItem key={r.globalId} disableGutters sx={{ display: "list-item", py: 0 }}>
            <GlobalIdLink globalId={r.globalId} />
            {`: ${r.name}`}
          </ListItem>
        ))}
        {linked.privateByOwner.map((p) => (
          <ListItem key={p.ownerFullName} disableGutters sx={{ display: "list-item", py: 0 }}>
            {t("fields.link.documentSections.linkedBy.privateDocs", { count: p.count, ownerFullName: p.ownerFullName })}
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
  const { t } = useTranslation("inventory");
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
      <Typography variant="subtitle2">{t("fields.link.documentSections.sharing.title")}</Typography>
      {!isShared ? (
        <Typography variant="body2">
          {t("fields.link.documentSections.sharing.notShared", { recordTypeName })}
        </Typography>
      ) : (
        <>
          <Typography variant="body2">
            {t("fields.link.documentSections.sharing.isShared", { recordTypeName })}
          </Typography>
          <List dense disablePadding sx={{ pl: 3, my: 0.5, listStyleType: "disc" }}>
            {Object.entries(info.sharedGroupsAndAccess ?? {}).map(([group, access]) => (
              <ListItem key={`g-${group}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                {t("fields.link.documentSections.sharing.withGroup", { group, access: access.toLowerCase() })}
              </ListItem>
            ))}
            {Object.entries(info.sharedUsersAndAccess ?? {}).map(([user, access]) => (
              <ListItem key={`u-${user}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                {t("fields.link.documentSections.sharing.withUser", { user, access: access.toLowerCase() })}
              </ListItem>
            ))}
            {Object.entries(info.sharedNotebooksAndOwners ?? {}).map(([nb, owner]) => (
              <ListItem key={`nb-${nb}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                <TransRichText
                  ns="inventory"
                  i18nKey="fields.link.documentSections.sharing.intoNotebook"
                  values={{ nb, owner }}
                  components={{ a: <Link href={`/globalId/${nb}`} target="_blank" rel="noopener noreferrer" /> }}
                />
              </ListItem>
            ))}
            {Object.entries(info.implicitShares ?? {}).map(([nb, owner]) => (
              <ListItem key={`im-${nb}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                <TransRichText
                  ns="inventory"
                  i18nKey="fields.link.documentSections.sharing.implicitlyInNotebook"
                  values={{ nb, owner }}
                  components={{ a: <Link href={`/globalId/${nb}`} target="_blank" rel="noopener noreferrer" /> }}
                />
              </ListItem>
            ))}
          </List>
        </>
      )}
      {publicChecked &&
        (publicLink === null ? (
          <Typography variant="body2">
            {t("fields.link.documentSections.sharing.notPublished", { recordTypeName })}
          </Typography>
        ) : (
          <Typography variant="body2">
            {publicLink.includes("initialRecordToDisplay")
              ? t("fields.link.documentSections.sharing.inPublishedNotebook")
              : t("fields.link.documentSections.sharing.isPublished", { recordTypeName })}{" "}
            <Link
              href={`${window.location.origin}/public/publishedView/${
                publicLink.includes("initialRecordToDisplay") || isNotebook ? "notebook" : "document"
              }/${publicLink}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t("fields.link.documentSections.sharing.publicLink")}
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
  const { t } = useTranslation("inventory");
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
            <TransRichText
              ns="inventory"
              i18nKey="fields.link.documentSections.versionNote"
              values={{ pinnedVersion, globalId: unversionedGlobalId }}
            />
          </Typography>
        </Box>
      ) : null}
      <Box>
        <Typography variant="h6" component="div" sx={{ mb: 1 }}>
          {info.name}
        </Typography>
        <Table size="small">
          <TableBody>
            <MetaRow label={t("fields.link.documentSections.meta.uniqueId")}>
              <GlobalIdLink globalId={isVersionView ? `${unversionedGlobalId}v${pinnedVersion}` : info.oid.idString} />
            </MetaRow>
            <MetaRow label={t("fields.link.documentSections.meta.type")}>{info.type}</MetaRow>
            {info.path ? <MetaRow label={t("fields.link.documentSections.meta.path")}>{info.path}</MetaRow> : null}
            {isStructuredDocument && info.version != null ? (
              <MetaRow label={t("fields.link.documentSections.meta.version")}>{info.version}</MetaRow>
            ) : null}
            <MetaRow label={t("fields.link.documentSections.meta.owner")}>{info.ownerFullName}</MetaRow>
            <MetaRow label={t("fields.link.documentSections.meta.creationDate")}>
              {info.creationDateWithClientTimezoneOffset}
            </MetaRow>
            <MetaRow label={t("fields.link.documentSections.meta.lastModified")}>
              {info.modificationDateWithClientTimezoneOffset}
            </MetaRow>
            {isStructuredDocument && info.status ? (
              <MetaRow label={t("fields.link.documentSections.meta.status")}>
                {info.status === "CANNOT_EDIT_OTHER_EDITING" && info.currentEditor
                  ? t("fields.link.documentSections.statusLabels.cannotEditOtherEditingBy", {
                      currentEditor: info.currentEditor,
                    })
                  : {
                      VIEW_MODE: t("fields.link.documentSections.statusLabels.viewMode"),
                      EDIT_MODE: t("fields.link.documentSections.statusLabels.editMode"),
                      CANNOT_EDIT_OTHER_EDITING: t("fields.link.documentSections.statusLabels.cannotEditOtherEditing"),
                      CANNOT_EDIT_NO_PERMISSION: t("fields.link.documentSections.statusLabels.cannotEditNoPermission"),
                      CAN_NEVER_EDIT: t("fields.link.documentSections.statusLabels.canNeverEdit"),
                    }[info.status]}
              </MetaRow>
            ) : null}
            {isStructuredDocument && info.signatureStatus ? (
              <MetaRow label={t("fields.link.documentSections.meta.signatureStatus")}>
                {
                  {
                    UNSIGNED: t("fields.link.documentSections.signatureLabels.unsigned"),
                    SIGNED_AND_LOCKED: t("fields.link.documentSections.signatureLabels.signed"),
                    AWAITING_WITNESS: t("fields.link.documentSections.signatureLabels.awaitingWitness"),
                    WITNESSED: t("fields.link.documentSections.signatureLabels.witnessed"),
                    UNSIGNABLE: t("fields.link.documentSections.signatureLabels.unsignable"),
                    SIGNED_AND_LOCKED_WITNESSES_DECLINED: t(
                      "fields.link.documentSections.signatureLabels.signedWitnessesDeclined",
                    ),
                  }[info.signatureStatus]
                }
              </MetaRow>
            ) : null}
            {isStructuredDocument && info.templateFormName ? (
              <MetaRow label={t("fields.link.documentSections.meta.createdFrom")}>{info.templateFormName}</MetaRow>
            ) : null}
            {isStructuredDocument && info.templateFormId ? (
              <MetaRow label={t("fields.link.documentSections.meta.formId")}>
                <GlobalIdLink globalId={info.templateFormId.idString} />
              </MetaRow>
            ) : null}
            {isStructuredDocument && info.templateOid ? (
              <MetaRow label={t("fields.link.documentSections.meta.templateName")}>
                <GlobalIdLink globalId={info.templateOid}>{info.templateName ?? info.templateOid}</GlobalIdLink>
              </MetaRow>
            ) : null}
            {tags ? <MetaRow label={t("fields.link.documentSections.meta.tags")}>{tags}</MetaRow> : null}
          </TableBody>
        </Table>
      </Box>

      <LinkedByDocs info={info} recordTypeName={recordTypeName} />

      <RelatedInventoryItems globalId={info.oid.idString} recordTypeName={recordTypeName} />

      <SharingAndPublication info={info} isNotebook={isNotebook} />
    </Stack>
  );
}
