import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { DataGrid, useGridApiRef } from "@mui/x-data-grid";
import React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "../../../components/GlobalId";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { DataGridColumn } from "../../../util/table";
import { type Document, useLinkedDocumentsQuery } from "../queries";
import type { GalleryFile } from "../useGalleryListing";

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
export function LinkedDocumentsPanel({ file }: { file: GalleryFile }): React.ReactNode {
  const { t } = useTranslation("gallery");
  const apiRef = useGridApiRef();
  const linkedDocuments = useLinkedDocumentsQuery(file);
  const documents = linkedDocuments.data?.documents ?? [];
  const privateByOwner = linkedDocuments.data?.privateByOwner ?? [];
  const { trackEvent } = React.useContext(AnalyticsContext);

  React.useEffect(() => {
    setTimeout(() => {
      void apiRef.current?.autosizeColumns({
        includeHeaders: true,
        includeOutliers: true,
      });
    }, 10); // 10ms for react to re-render
  }, [documents, apiRef]);

  return (
    <Box component="section" sx={{ flexGrow: 1, mt: 0.5, "--DataGrid-overlayHeight": "40px" }}>
      <Typography variant="h4" component="h4">
        {t("linkedDocumentsPanel.heading")}
      </Typography>
      <DataGrid
        columns={[
          DataGridColumn.newColumnWithFieldName<"name", Document>("name", {
            headerName: t("linkedDocumentsPanel.columns.name"),
            flex: 1,
            sortable: false,
            resizable: true,
          }),
          DataGridColumn.newColumnWithFieldName<"globalId", Document>("globalId", {
            headerName: t("linkedDocumentsPanel.columns.globalId"),
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
        rows={documents}
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
          // when only private placeholders exist the per-owner counts below explain the
          // empty grid, so "No Linked Documents" would contradict them
          noRowsLabel: linkedDocuments.isError
            ? t("linkedDocumentsPanel.loadFailed")
            : privateByOwner.length > 0
              ? ""
              : t("linkedDocumentsPanel.noRows"),
        }}
        loading={linkedDocuments.isPending && linkedDocuments.fetchStatus !== "idle"}
        getRowId={(row) => row.id}
        sx={{
          ml: 2,
        }}
      />
      {privateByOwner.map((p) => (
        <Typography key={p.ownerFullName} variant="body2" sx={{ ml: 2, mt: 0.5 }}>
          {t("linkedDocumentsPanel.privateDocs", {
            count: p.count,
            ownerFullName: p.ownerFullName,
          })}
        </Typography>
      ))}
    </Box>
  );
}
