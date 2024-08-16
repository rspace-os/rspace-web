//@flow

import React, { type Node, type ComponentType } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { type GalleryFile } from "../useGalleryListing";
import useLinkedDocuments, { type Document } from "../useLinkedDocuments";
import { DataGrid } from "@mui/x-data-grid";
import { DataGridColumn } from "../../../util/table";
import GlobalId from "../../../components/GlobalId";

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
            flex: 1,
            sortable: false,
            resizable: true,
          }),
          DataGridColumn.newColumnWithFieldName<Document, _>("globalId", {
            headerName: "Global ID",
            flex: 0,
            resizable: true,
            sortable: false,
            renderCell: ({ row }) => <GlobalId record={row.linkableRecord} />,
          }),
        ]}
        rows={linkedDocuments.documents}
        initialState={{
          columns: {},
        }}
        density="compact"
        disableColumnFilter
        hideFooter
        autoHeight
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
