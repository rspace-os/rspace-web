import React from "react";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
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
  /** Lower-case noun for the empty message, e.g. "document", "notebook", "file". */
  recordTypeName: string;
}): React.ReactElement {
  const { items, loading, errorMessage } =
    useReferencingInventoryItems(globalId);

  return (
    <Box sx={{ mt: 1 }}>
      <Typography variant="subtitle2">Related inventory items</Typography>
      {loading && <Typography variant="body2">Loading…</Typography>}
      {errorMessage && (
        <Typography variant="body2" color="error">
          {errorMessage}
        </Typography>
      )}
      {!loading && !errorMessage && items.length === 0 && (
        <Typography variant="body2">
          No Inventory items link to this {recordTypeName}.
        </Typography>
      )}
      {items.length > 0 && (
        <Box component="ul" sx={{ pl: 3, my: 0.5 }}>
          {items.map((item, index) => (
            // one row per link FIELD: a source item linking through two
            // fields repeats its globalId, so the key needs the index too
            <li key={`${item.globalId}-${index}`}>
              <Link
                href={`/globalId/${item.globalId}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                {item.globalId}
              </Link>
              : {item.name}
              {item.relationType ? (
                <Typography variant="caption" component="em">
                  {" "}
                  ({item.relationType})
                </Typography>
              ) : null}
            </li>
          ))}
        </Box>
      )}
    </Box>
  );
}
