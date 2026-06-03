import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { type GalleryFile } from "../useGalleryListing";
import useReferencingInventoryItems, {
  type ReferencingInventoryItem,
} from "../useReferencingInventoryItems";
import { DataGrid, useGridApiRef } from "@mui/x-data-grid";
import { DataGridColumn } from "../../../util/table";
import GlobalId from "../../../components/GlobalId";
import AnalyticsContext from "../../../stores/contexts/Analytics";

/**
 * Lists the Inventory items (samples, subsamples, containers, instruments) whose
 * Link extra-field points at this GalleryFile. The mirror image of
 * {@link LinkedDocumentsPanel}: where that shows ELN documents linking in, this
 * shows Inventory items linking in. The relation is displayed with the Inventory
 * item as the subject (e.g. "SA1 IsPartOf this file").
 *
 * @param file The GalleryFile that can be referenced by Inventory items.
 */
export function ReferencingInventoryItemsPanel({
  file,
}: {
  file: GalleryFile;
}): React.ReactNode {
  const apiRef = useGridApiRef();
  const referencing = useReferencingInventoryItems(
    typeof file.globalId === "string" ? file.globalId : null,
  );
  const { trackEvent } = React.useContext(AnalyticsContext);

  React.useEffect(() => {
    setTimeout(() => {
      void apiRef.current?.autosizeColumns({
        includeHeaders: true,
        includeOutliers: true,
      });
    }, 10); // 10ms for react to re-render
  }, [referencing.items, apiRef]);

  return (
    <Box
      component="section"
      sx={{ mt: 0.5, "--DataGrid-overlayHeight": "40px" }}
      flexGrow={1}
    >
      <Typography variant="h4" component="h4">
        Related inventory items
      </Typography>
      <DataGrid
        columns={[
          DataGridColumn.newColumnWithFieldName<"name", ReferencingInventoryItem>(
            "name",
            {
              headerName: "Name",
              flex: 1,
              sortable: false,
              resizable: true,
            },
          ),
          DataGridColumn.newColumnWithFieldName<
            "relationType",
            ReferencingInventoryItem
          >("relationType", {
            headerName: "Relation",
            flex: 0,
            sortable: false,
            resizable: true,
          }),
          DataGridColumn.newColumnWithFieldName<
            "globalId",
            ReferencingInventoryItem
          >("globalId", {
            headerName: "Global ID",
            flex: 0,
            resizable: true,
            sortable: false,
            renderCell: ({ row }) => (
              <GlobalId
                record={row.linkableRecord}
                onClick={() => {
                  trackEvent(
                    "user:click:globalId:galleryReferencingInventoryItems",
                  );
                }}
              />
            ),
          }),
        ]}
        rows={referencing.items}
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
          noRowsLabel:
            referencing.errorMessage ?? "No related inventory items",
        }}
        loading={referencing.loading}
        getRowId={(row) => row.globalId}
        sx={{
          ml: 2,
        }}
      />
    </Box>
  );
}
