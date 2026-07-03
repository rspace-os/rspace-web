import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import useReferencingInventoryItems from "@/eln/gallery/useReferencingInventoryItems";

/**
 * The "Related inventory items" section of {@link ElnRecordInfoDialog}: the
 * Inventory items whose Link field points at the displayed ELN record. The
 * "Show linked docs" action only covers ELN references, so without this section
 * an inventory link to the record would be invisible from its info dialog.
 * Shared by the document/notebook and gallery-file bodies.
 */
export default function RelatedInventoryItems({
  globalId,
  recordTypeName,
}: {
  globalId: string;
  /** Lower-case noun for the empty message, e.g. "document", "notebook", "gallery file". */
  recordTypeName: string;
}): React.ReactElement {
  const { t } = useTranslation("inventory");
  const { items, loading, errorMessage } = useReferencingInventoryItems(globalId);

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
            recordTypeName,
          })}
        </Typography>
      )}
      {items.length > 0 && (
        <List dense disablePadding sx={{ pl: 3, my: 0.5, listStyleType: "disc" }}>
          {items.map((item, index) => (
            // one row per link FIELD: a source item linking through two
            // fields repeats its globalId, so the key needs the index too
            <ListItem key={`${item.globalId}-${index}`} disableGutters sx={{ display: "list-item", py: 0 }}>
              <Link href={`/globalId/${item.globalId}`} target="_blank" rel="noopener noreferrer">
                {item.globalId}
              </Link>
              {`: ${item.name}`}
              {item.relationType ? (
                <Typography variant="caption" component="em">{` (${item.relationType})`}</Typography>
              ) : null}
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  );
}
