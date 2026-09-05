import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import { useReferencingInventoryItemsQuery } from "@/eln/gallery/queries";
import TransRichText from "@/modules/common/i18n/TransRichText";

/**
 * The "Related inventory items" section of {@link ElnRecordInfoDialog}: the
 * Inventory items whose Link field points at the displayed ELN record. The
 * "Show linked docs" action only covers ELN references, so without this section
 * an inventory link to the record would be invisible from its info dialog.
 * Shared by the document/notebook and gallery-file bodies.
 */
export default function RelatedInventoryItems({
  globalId,
  recordType,
}: {
  globalId: string;
  recordType: "document" | "notebook" | "galleryFile";
}): React.ReactElement {
  const { t } = useTranslation(["inventory", "gallery"]);
  const referencingItems = useReferencingInventoryItemsQuery({
    globalId,
  });
  const items = referencingItems.data ?? [];
  const loading = referencingItems.isPending;
  const errorMessage = referencingItems.isError ? t("gallery:referencingInventoryItems.loadFailed") : null;

  return (
    <Box>
      <Typography variant="subtitle2">{t("fields.link.relatedInventoryItems.title")}</Typography>
      {loading && <Typography variant="body2">{t("fields.link.relatedInventoryItems.loading")}</Typography>}
      {errorMessage && (
        <Typography variant="body2" color="error">
          {errorMessage}
        </Typography>
      )}
      {!loading && !errorMessage && items.length === 0 && (
        <Typography variant="body2">
          {t("fields.link.relatedInventoryItems.none", {
            recordType,
          })}
        </Typography>
      )}
      {items.length > 0 && (
        <List dense disablePadding sx={{ pl: 3, my: 0.5, listStyleType: "disc" }}>
          {items.map((item, index) => {
            const relationKind = item.isAttachment ? "attachment" : item.relationType ? "relation" : "none";
            return (
              // one row per link FIELD: a source item linking through two
              // fields repeats its globalId, so the key needs the index too
              <ListItem key={`${item.globalId}-${index}`} disableGutters sx={{ display: "list-item", py: 0 }}>
                <TransRichText
                  i18nKey="inventory:fields.link.relatedInventoryItems.row"
                  values={{
                    globalId: item.globalId,
                    name: item.name,
                    relationKind,
                    relationType: item.relationType,
                  }}
                  components={{
                    globalId: <Link href={`/globalId/${item.globalId}`} target="_blank" rel="noopener noreferrer" />,
                    relation: <Typography variant="caption" component="em" />,
                  }}
                />
              </ListItem>
            );
          })}
        </List>
      )}
    </Box>
  );
}
