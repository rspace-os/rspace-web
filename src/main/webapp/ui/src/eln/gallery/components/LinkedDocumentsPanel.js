//@flow

import React, { type Node, type ComponentType } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { type GalleryFile } from "../useGalleryListing";
import useLinkedDocuments, { type Document } from "../useLinkedDocuments";
import { DataGrid, useGridApiRef } from "@mui/x-data-grid";
import { DataGridColumn } from "../../../util/table";
import GlobalId from "../../../components/GlobalId";
import AnalyticsContext from "../../../stores/contexts/Analytics";

/**
 * This table lists all of the ELN documents that reference the passed
 * GalleryFile, providing a backlink. For some files, this graph of references
 * is a nice addition whilst in other cases it forms the basis for the
 * justification of storing files in the RSpace Gallery. Take Data Management
 * Plans (DMPs), for example: being able to mark experiments as recording data
 * that adheres to a DMP means that finding all such experiments becomes
 * trivial with the aid of this table.
 *
 * @param file The GalleryFile that can be referenced by ELN documents.
 */
export const LinkedDocumentsPanel: ComponentType<{| file: GalleryFile |}> = ({
  file,
}): Node => {
  const apiRef = useGridApiRef();
  const linkedDocuments = useLinkedDocuments(file);
  const { trackEvent } = React.useContext(AnalyticsContext);

  React.useEffect(() => {
    setTimeout(() => {
      apiRef.current?.autosizeColumns({
        includeHeaders: true,
        includeOutliers: true,
      });
    }, 10); // 10ms for react to re-render
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - apiRef wont change
     */
  }, [linkedDocuments.documents]);

  return (
    <Box
      component="section"
      sx={{ mt: 0.5, "--DataGrid-overlayHeight": "40px" }}
      flexGrow={1}
    >
      <Typography variant="h4" component="h4">
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
            renderCell: ({ row }) => (
              <GlobalId
                record={row.linkableRecord}
                onClick={() => {
                  trackEvent("user:click:globalId:galleryLinkedDocuments");
                }}
              />
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
        autoHeight
        apiRef={apiRef}
        slots={{
          pagination: null,
        }}
        localeText={{
          noRowsLabel: linkedDocuments.errorMessage ?? "No Linked Documents",
        }}
        loading={linkedDocuments.loading}
        getRowId={(row) => row.id}
        sx={{
          ml: 2,
        }}
      />
    </Box>
  );
};
