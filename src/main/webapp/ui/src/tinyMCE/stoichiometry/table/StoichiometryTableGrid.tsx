import React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Radio from "@mui/material/Radio";
import {
  DataGrid,
  type GridCellParams,
  type GridColDef,
} from "@mui/x-data-grid";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import StoichiometryTableInventoryLinkCell from "@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell";
import StoichiometryTableRoleChip from "@/tinyMCE/stoichiometry/StoichiometryTableRoleChip";
import StoichiometryTableToolbar from "@/tinyMCE/stoichiometry/StoichiometryTableToolbar";
import StoichiometryTableTypeDropdown from "@/tinyMCE/stoichiometry/table/StoichiometryTableTypeDropdown";
import { STOICHIOMETRY_TABLE_CLASS } from "@/tinyMCE/stoichiometry/theme";
import type { EditableMolecule } from "../types";
import { calculateMoles, getInventoryUpdateEligibility } from "../utils";
import type { StoichiometryTableGridProps } from "./types";

const STOICHIOMETRY_TABLE_SLOTS = {
  toolbar: StoichiometryTableToolbar,
};

const getMoleculeRowId = (row: EditableMolecule) => row.id;

export default function StoichiometryTableGrid({
  editable,
  allMolecules,
  hasChanges = false,
  activeChemId = null,
  linkedInventoryQuantityInfoByGlobalId = new Map<
    string,
    InventoryQuantityQueryResult
  >(),
  onAddReagent,
  onUpdateInventoryStock,
  onDeleteReagent,
  onPickInventoryItem,
  onRemoveInventoryLink,
  onUndoRemoveInventoryLink,
  onSelectLimitingReagent,
  onProcessRowUpdate,
}: StoichiometryTableGridProps): React.ReactNode {
  const [activeTypeEditorRowId, setActiveTypeEditorRowId] = React.useState<
    number | null
  >(null);
  const roleColumnEditable = editable && activeChemId === null;
  const openTypeCellEditor = React.useCallback((rowId: number) => {
    setActiveTypeEditorRowId(rowId);
  }, []);
  React.useEffect(() => {
    if (!roleColumnEditable) {
      setActiveTypeEditorRowId(null);
    }
  }, [roleColumnEditable]);
  const limitingReagent = allMolecules.find(
    (molecule) =>
      molecule.limitingReagent && molecule.role.toLowerCase() === "reactant",
  );
  const limitingReagentId = limitingReagent?.id;

  const linkedIds = allMolecules
    .map(
      (molecule) =>
        molecule.inventoryLink?.inventoryItemGlobalId ??
        molecule.deletedInventoryLink?.inventoryItemGlobalId,
    )
    .filter((id): id is string => Boolean(id));

  const linkedInventoryItemGlobalIdsByMoleculeId = new Map(
    allMolecules.map((molecule) => [
      molecule.id,
      linkedIds.filter(
        (inventoryItemGlobalId) =>
          inventoryItemGlobalId !==
          (molecule.inventoryLink?.inventoryItemGlobalId ??
            molecule.deletedInventoryLink?.inventoryItemGlobalId),
      ),
    ]),
  );

  const columns = React.useMemo<GridColDef<EditableMolecule>[]>(
    () => [
      {
        field: "actions",
        headerName: "Actions",
        sortable: false,
        minWidth: 130,
        align: "center",
        headerAlign: "center",
        renderCell: ({ row }) =>
          editable ? (
            <Button
              variant="outlined"
              color="error"
              size="small"
              disabled={activeChemId !== null && row.role !== "AGENT"}
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
        renderCell: ({ row }) => (
          <StoichiometryTableInventoryLinkCell
            inventoryLink={row.inventoryLink}
            isDeleted={Boolean(row.deletedInventoryLink) && !row.inventoryLink}
            moleculeName={row.name}
            editable={editable}
            showInsufficientStockWarning={
              getInventoryUpdateEligibility(
                row,
                linkedInventoryQuantityInfoByGlobalId,
              ).showInsufficientStockWarning
            }
            linkedInventoryItemGlobalIds={
              linkedInventoryItemGlobalIdsByMoleculeId.get(row.id) ?? []
            }
            onPickInventoryItem={(id, inventoryItemGlobalId) =>
              onPickInventoryItem?.(row.id, id, inventoryItemGlobalId)
            }
            onRemoveInventoryLink={() => {
              onRemoveInventoryLink?.(row.id);
            }}
            onUndoRemoveInventoryLink={() => {
              onUndoRemoveInventoryLink?.(row.id);
            }}
          />
        ),
      },
      {
        field: "role",
        headerName: "Type",
        sortable: false,
        minWidth: 130,
        renderCell: ({ row }) => {
          if (!roleColumnEditable) {
            return <StoichiometryTableRoleChip role={row.role || ""} />;
          }

          if (activeTypeEditorRowId === row.id) {
            return (
              <StoichiometryTableTypeDropdown
                rowName={row.name}
                value={row.role}
                onChangeValue={(nextValue) => {
                  onProcessRowUpdate?.(
                    {
                      ...row,
                      role: nextValue,
                    },
                    row,
                  );
                }}
                onClose={() => {
                  setActiveTypeEditorRowId(null);
                }}
              />
            );
          }

          return (
            <Box
              component="button"
              type="button"
              aria-label={`Edit type for ${row.name ?? "molecule"}`}
              onClick={() => {
                openTypeCellEditor(row.id);
              }}
              sx={{
                p: 0,
                border: 0,
                background: "transparent",
                cursor: "pointer",
              }}
            >
              <StoichiometryTableRoleChip role={row.role || ""} />
            </Box>
          );
        },
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
              onChange={(event) => {
                if (event.target.checked && editable) {
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
          if (limitingReagentId !== undefined && params.id === limitingReagentId) {
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
          if (limitingReagentId !== undefined && params.id !== limitingReagentId) {
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
          if (limitingReagentId !== undefined && params.id !== limitingReagentId) {
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
          if (limitingReagentId !== undefined && params.id === limitingReagentId) {
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
    ],
    [
      editable,
      roleColumnEditable,
      activeTypeEditorRowId,
      limitingReagentId,
      linkedInventoryQuantityInfoByGlobalId,
      linkedInventoryItemGlobalIdsByMoleculeId,
      onDeleteReagent,
      openTypeCellEditor,
      onPickInventoryItem,
      onRemoveInventoryLink,
      onUndoRemoveInventoryLink,
      onSelectLimitingReagent,
    ],
  );

  const isCellEditable = (params: GridCellParams<EditableMolecule>) => {
    if (!editable) {
      return false;
    }

    const { field, row } = params;
    if (
      limitingReagentId !== undefined &&
      (field === "mass" || field === "moles")
    ) {
      return row.id === limitingReagentId;
    }
    return true;
  };

  const toolbarSlotProps = React.useMemo(
    () => ({
      toolbar: {
        onAddReagent,
        onUpdateInventoryStock,
        editable,
        allMolecules,
        hasChanges,
        linkedInventoryQuantityInfoByGlobalId,
      },
    }),
    [
      allMolecules,
      editable,
      hasChanges,
      linkedInventoryQuantityInfoByGlobalId,
      onAddReagent,
      onUpdateInventoryStock,
    ],
  );

  return (
    <Box sx={{ width: "100%", minHeight: "1px", height: 1 }}>
      <DataGrid
        rows={allMolecules}
        columns={columns}
        disableVirtualization={true}
        isCellEditable={isCellEditable}
        hideFooter
        disableColumnFilter
        getRowId={getMoleculeRowId}
        processRowUpdate={onProcessRowUpdate}
        slots={STOICHIOMETRY_TABLE_SLOTS}
        showToolbar={true}
        slotProps={toolbarSlotProps}
        className={STOICHIOMETRY_TABLE_CLASS}
      />
    </Box>
  );
}
