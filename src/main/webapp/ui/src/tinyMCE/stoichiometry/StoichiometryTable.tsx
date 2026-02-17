import React from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Radio from "@mui/material/Radio";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { useGetStoichiometryQuery } from "@/modules/stoichiometry/queries";
import { toEditableMolecules } from "@/tinyMCE/stoichiometry/editableMolecules";
import { useStoichiometryTableController } from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import StoichiometryTableInventoryLinkCell from "@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell";
import StoichiometryTableRoleChip from "@/tinyMCE/stoichiometry/StoichiometryTableRoleChip";
import StoichiometryTableToolbar from "@/tinyMCE/stoichiometry/StoichiometryTableToolbar";
import StoichiometryTableLoadingDialog from "@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog";
import { STOICHIOMETRY_TABLE_CLASS } from "@/tinyMCE/stoichiometry/theme";
import type { EditableMolecule } from "./types";
import { calculateMoles } from "./utils";

type StoichiometryTableProps = {
  stoichiometryId: number;
  stoichiometryRevision: number;
  editable?: boolean;
};

type StoichiometryTableGridProps = {
  editable: boolean;
  allMolecules: ReadonlyArray<EditableMolecule>;
  isGettingMoleculeInfo?: boolean;
  onAddReagent?: (
    smilesString: string,
    name: string,
    source: string,
  ) => Promise<void>;
  onDeleteReagent?: (moleculeId: number) => void;
  onPickInventoryItem?: (
    moleculeId: number,
    inventoryItemId: number,
    inventoryItemGlobalId: string,
  ) => void;
  onRemoveInventoryLink?: (moleculeId: number) => void;
  onSelectLimitingReagent?: (molecule: EditableMolecule) => void;
  onProcessRowUpdate?: (
    newRow: EditableMolecule,
    oldRow: EditableMolecule,
  ) => EditableMolecule;
};

function StoichiometryTableGrid({
  editable,
  allMolecules,
  isGettingMoleculeInfo = false,
  onAddReagent,
  onDeleteReagent,
  onPickInventoryItem,
  onRemoveInventoryLink,
  onSelectLimitingReagent,
  onProcessRowUpdate,
}: StoichiometryTableGridProps) {
  const limitingReagent = allMolecules.find(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );

  const columns: GridColDef<EditableMolecule>[] = [
    {
      field: "actions",
      headerName: "Actions",
      sortable: false,
      minWidth: 130,
      align: "center",
      headerAlign: "center",
      renderCell: ({ row }) =>
        row.role.toLowerCase() === "agent" ? (
          <Button
            variant="outlined"
            color="error"
            size="small"
            disabled={!editable}
            aria-label={`Delete reagent ${row.name}`}
            onClick={() => {
              onDeleteReagent?.(row.id);
            }}
          >
            Delete
          </Button>
        ) : (
          <>&mdash;</>
        ),
    },
    {
      field: "name",
      headerName: "Name",
      sortable: false,
      minWidth: 180,
    },
    {
      field: "inventoryLink",
      headerName: "Inventory Link",
      sortable: false,
      minWidth: 200,
      renderCell: ({ row }) => {
        const linkedInventoryItemGlobalIds = allMolecules
          .filter((molecule) => molecule.id !== row.id)
          .map((molecule) => molecule.inventoryLink?.inventoryItemGlobalId)
          .filter((id): id is string => Boolean(id));

        return (
          <StoichiometryTableInventoryLinkCell
            inventoryLink={row.inventoryLink}
            moleculeName={row.name}
            editable={editable}
            linkedInventoryItemGlobalIds={linkedInventoryItemGlobalIds}
            onPickInventoryItem={(id, inventoryItemGlobalId) =>
              onPickInventoryItem?.(row.id, id, inventoryItemGlobalId)
            }
            onRemoveInventoryLink={() => {
              onRemoveInventoryLink?.(row.id);
            }}
          />
        );
      },
    },
    {
      field: "role",
      valueGetter: (_value, { role }) =>
        (({
          REACTANT: "Reactant",
          PRODUCT: "Product",
          AGENT: "Reagent",
        } as Record<string, string>)[role] ?? "Unknown role"),
      headerName: "Role",
      sortable: false,
      minWidth: 130,
      renderCell: ({ row }) => <StoichiometryTableRoleChip role={row.role || ""} />,
    },
    {
      field: "limitingReagent",
      headerName: "Limiting Reagent",
      sortable: false,
      minWidth: 180,
      align: "center",
      renderCell: (params) =>
        params.row.role.toLowerCase() === "reactant" ? (
          <Radio
            checked={params.row.limitingReagent || false}
            disabled={!editable}
            inputProps={{
              "aria-label": `Select ${params.row.name} as limiting reagent`,
            }}
            onChange={(e) => {
              if (e.target.checked && editable) {
                onSelectLimitingReagent?.(params.row);
              }
            }}
          />
        ) : (
          <>&mdash;</>
        ),
    },
    {
      field: "coefficient",
      headerName: "Equivalent",
      sortable: false,
      minWidth: 120,
      type: "number",
      editable,
      headerAlign: "left",
      cellClassName: (params) => {
        if (limitingReagent && params.id === limitingReagent.id) {
          return "stoichiometry-disabled-cell";
        }
        return "";
      },
    },
    {
      field: "molecularWeight",
      headerName: "Molecular Weight (g/mol)",
      sortable: false,
      minWidth: 220,
      type: "number",
      headerAlign: "left",
      renderCell: (params) =>
        params.value !== null && params.value !== undefined ? (
          Number(params.value).toFixed(3)
        ) : (
          <>&#8212;</>
        ),
    },
    {
      field: "mass",
      headerName: "Mass (g)",
      sortable: false,
      headerAlign: "left",
      minWidth: 120,
      type: "number",
      editable,
      renderCell: (params) =>
        params.value !== null && params.value !== undefined ? (
          Number(params.value).toFixed(3)
        ) : (
          <>&#8212;</>
        ),
      cellClassName: (params) => {
        if (limitingReagent && params.id !== limitingReagent.id) {
          return "stoichiometry-disabled-cell";
        }
        return "";
      },
    },
    {
      field: "moles",
      valueGetter: (_value, row) => calculateMoles(row.mass, row.molecularWeight),
      headerName: "Moles (mol)",
      sortable: false,
      headerAlign: "left",
      type: "number",
      minWidth: 120,
      editable,
      renderCell: (params) =>
        params.value !== null && params.value !== undefined ? (
          Number(params.value).toFixed(3)
        ) : (
          <>&#8212;</>
        ),
      cellClassName: (params) => {
        if (limitingReagent && params.id !== limitingReagent.id) {
          return "stoichiometry-disabled-cell";
        }
        return "";
      },
    },
    {
      field: "actualAmount",
      headerName: "Actual Mass (g)",
      sortable: false,
      headerAlign: "left",
      minWidth: 150,
      editable,
      type: "number",
      renderCell: (params) =>
        params.value !== null && params.value !== undefined ? (
          Number(params.value).toFixed(3)
        ) : (
          <>&mdash;</>
        ),
    },
    {
      field: "actualMoles",
      valueGetter: (_value, row) =>
        calculateMoles(row.actualAmount, row.molecularWeight),
      headerName: "Actual Moles (mol)",
      sortable: false,
      headerAlign: "left",
      editable,
      minWidth: 180,
      type: "number",
      renderCell: (params) => {
        return params.value !== null ? (
          Number(params.value).toFixed(3)
        ) : (
          <>&mdash;</>
        );
      },
    },
    {
      field: "actualYield",
      headerName: "Yield/Excess (%)",
      sortable: false,
      headerAlign: "left",
      minWidth: 150,
      type: "number",
      editable: false,
      renderCell: (params) => {
        if (limitingReagent && params.id === limitingReagent.id) {
          return <>&#8212;</>;
        }
        const value = params.value as number | null | undefined;
        return value !== null && value !== undefined ? (
          `${Number((Number(Number(value).toFixed(3)) * 100).toFixed(3))}%`
        ) : (
          <>&#8212;</>
        );
      },
    },
    {
      field: "notes",
      headerName: "Notes",
      sortable: false,
      minWidth: 200,
      type: "string",
      editable,
      renderCell: (params) => {
        const value = params.value as string | null | undefined;
        return value ?? <>&mdash;</>;
      },
    },
  ];

  if (!allMolecules.length) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
      >
        <Typography variant="body2">No stoichiometry data available</Typography>
      </Box>
    );
  }

  return (
    <>
      <Box sx={{ width: "100%" }}>
        <DataGrid
          rows={allMolecules}
          columns={columns}
          disableVirtualization
          isCellEditable={(params) => {
            if (!editable) {
              return false;
            }

            const { field, row } = params as {
              field: string;
              row: EditableMolecule;
            };
            if (limitingReagent && (field === "mass" || field === "moles")) {
              return row.id === limitingReagent.id;
            }
            return true;
          }}
          hideFooter
          disableColumnFilter
          getRowId={(row) => row.id}
          processRowUpdate={onProcessRowUpdate}
          slots={{
            toolbar: StoichiometryTableToolbar,
          }}
          showToolbar={true}
          slotProps={{
            toolbar: {
              onAddReagent,
              editable,
              allMolecules,
            },
          }}
          autosizeOnMount
          className={STOICHIOMETRY_TABLE_CLASS}
        />
      </Box>
      <StoichiometryTableLoadingDialog open={isGettingMoleculeInfo} />
    </>
  );
}

function StoichiometryTableWithQueryData({
  stoichiometryId,
  stoichiometryRevision,
}: StoichiometryTableProps) {
  const { getToken } = useOauthToken();
  const { data } = useGetStoichiometryQuery({
    stoichiometryId,
    revision: stoichiometryRevision,
    getToken,
  });

  return (
    <StoichiometryTableGrid
      editable={false}
      allMolecules={toEditableMolecules(data)}
    />
  );
}

function StoichiometryTable({
  stoichiometryId,
  stoichiometryRevision,
  editable = false,
}: StoichiometryTableProps) {
  const tableController = useStoichiometryTableController();

  if (editable && tableController) {
    return (
      <StoichiometryTableGrid
        editable
        allMolecules={tableController.allMolecules}
        isGettingMoleculeInfo={tableController.isGettingMoleculeInfo}
        onAddReagent={tableController.addReagent}
        onDeleteReagent={tableController.deleteReagent}
        onPickInventoryItem={tableController.pickInventoryLink}
        onRemoveInventoryLink={tableController.removeInventoryLink}
        onSelectLimitingReagent={tableController.selectLimitingReagent}
        onProcessRowUpdate={tableController.processRowUpdate}
      />
    );
  }

  return (
    <StoichiometryTableWithQueryData
      stoichiometryId={stoichiometryId}
      stoichiometryRevision={stoichiometryRevision}
      editable={editable}
    />
  );
}

export default StoichiometryTable;
