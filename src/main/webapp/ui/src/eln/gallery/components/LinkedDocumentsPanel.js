//@flow

import React, { type Node, type ComponentType } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { type GalleryFile } from "../useGalleryListing";
import useLinkedDocuments, { type Document } from "../useLinkedDocuments";
import { DataGrid } from "@mui/x-data-grid";
import { DataGridColumn } from "../../../util/table";
import Link from "@mui/material/Link";

export const LinkedDocumentsPanel: ComponentType<{| file: GalleryFile |}> = ({
  file,
}): Node => {
  const linkedDocuments = useLinkedDocuments(file);

  return (
    <Box component="section" sx={{ mt: 0.5 }} flexGrow={1}>
      <Typography variant="h6" component="h4">
        Linked Documents
      </Typography>
      <DataGrid
        columns={[
          DataGridColumn.newColumnWithFieldName<Document, _>("name", {
            headerName: "Name",
            flex: 2,
            sortable: false,
          }),
          DataGridColumn.newColumnWithFieldName<Document, _>("globalId", {
            headerName: "ID",
            flex: 1,
            sortable: false,
            renderCell: ({ row, value }) => (
              <Link href={row.permalinkHref}>{value}</Link>
            ),
          }),
        ]}
        rows={linkedDocuments.documents}
        initialState={{
          columns: {},
        }}
        density="compact"
        disableColumnFilter
        hideFooter
        slots={{
          pagination: null,
        }}
        localeText={{
          noRowsLabel: linkedDocuments.errorMessage ?? "No Linked Documents",
        }}
        loading={linkedDocuments.loading}
        getRowId={(row) => row.id}
      />
    </Box>
  );
};
