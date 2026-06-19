import Link from "@mui/material/Link";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import type React from "react";

/**
 * Shared building blocks for the core metadata table rendered by both
 * {@link DocumentSections} and {@link GallerySections} in the link info dialog.
 */

/** One label/value row of the core metadata table. */
export function MetaRow({ label, children }: { label: string; children: React.ReactNode }): React.ReactElement {
  return (
    <TableRow>
      <TableCell component="th" scope="row" sx={{ color: "text.secondary", width: "10rem", verticalAlign: "top" }}>
        {label}
      </TableCell>
      <TableCell>{children}</TableCell>
    </TableRow>
  );
}

/** A `/globalId/<id>` link that opens in a new tab. */
export function GlobalIdLink({
  globalId,
  children,
}: {
  globalId: string;
  children?: React.ReactNode;
}): React.ReactElement {
  return (
    <Link href={`/globalId/${globalId}`} target="_blank" rel="noopener noreferrer">
      {children ?? globalId}
    </Link>
  );
}
