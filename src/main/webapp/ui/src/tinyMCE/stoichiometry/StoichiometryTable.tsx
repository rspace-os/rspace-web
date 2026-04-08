import React from "react";
import {
  DataGrid,
  type GridCellParams,
  type GridColDef,
} from "@mui/x-data-grid";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Radio from "@mui/material/Radio";
import useOauthToken from "@/hooks/auth/useOauthToken";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import { useGetStoichiometryQuery } from "@/modules/stoichiometry/queries";
import { toEditableMolecules } from "@/tinyMCE/stoichiometry/editableMolecules";
import StoichiometryTableInventoryLinkCell from "@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell";
import StoichiometryTableRoleChip from "@/tinyMCE/stoichiometry/StoichiometryTableRoleChip";
import type { InventoryStockUpdateResult } from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateDialog";
import StoichiometryTableToolbar from "@/tinyMCE/stoichiometry/StoichiometryTableToolbar";
import StoichiometryTableLoadingDialog from "@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog";
import { STOICHIOMETRY_TABLE_CLASS } from "@/tinyMCE/stoichiometry/theme";
import type { EditableMolecule } from "./types";
import { calculateMoles, getInventoryUpdateEligibility } from "./utils";
import { useStoichiometryTableController } from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";

type StoichiometryTableProps = {
  stoichiometryId: number;
  stoichiometryRevision: number;
  editable?: boolean;
  hasChanges?: boolean;
};

type StoichiometryTableGridProps = {
  editable: boolean;
  allMolecules: ReadonlyArray<EditableMolecule>;
  hasChanges?: boolean;
  linkedInventoryQuantityInfoByGlobalId?: ReadonlyMap<
    string,
    InventoryQuantityQueryResult
  >;
  isGettingMoleculeInfo?: boolean;
  onAddReagent?: (
    smilesString: string,
    name: string,
    source: string,
  ) => Promise<void>;
  onUpdateInventoryStock?: (
    selectedMoleculeIds: number[],
  ) => Promise<InventoryStockUpdateResult>;
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

const STOICHIOMETRY_TABLE_SLOTS = {
  toolbar: StoichiometryTableToolbar,
};

const getMoleculeRowId = (row: EditableMolecule) => row.id;

const StoichiometryTableGrid = ({
  editable,
  allMolecules,
  hasChanges = false,
  linkedInventoryQuantityInfoByGlobalId = new Map<
    string,
    InventoryQuantityQueryResult
  >(),
  isGettingMoleculeInfo = false,
  onAddReagent,
  onUpdateInventoryStock,
  onDeleteReagent,
  onPickInventoryItem,
  onRemoveInventoryLink,
  onSelectLimitingReagent,
  onProcessRowUpdate,
}: StoichiometryTableGridProps) => {
  const limitingReagent = allMolecules.find(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );
  const limitingReagentId = limitingReagent?.id;

  const linkedInventoryItemGlobalIdsByMoleculeId = React.useMemo(() => {
    const linkedIds = allMolecules
      .map((molecule) => molecule.inventoryLink?.inventoryItemGlobalId)
      .filter((id): id is string => Boolean(id));

    return new Map(
      allMolecules.map((molecule) => [
        molecule.id,
        linkedIds.filter(
          (inventoryItemGlobalId) =>
            inventoryItemGlobalId !== molecule.inventoryLink?.inventoryItemGlobalId,
        ),
      ]),
    );
  }, [allMolecules]);

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
        renderCell: ({ row }) => (
          <StoichiometryTableInventoryLinkCell
            inventoryLink={row.inventoryLink}
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
          />
        ),
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
        renderCell: ({ row }) => (
          <StoichiometryTableRoleChip role={row.role || ""} />
        ),
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
      limitingReagentId,
      linkedInventoryQuantityInfoByGlobalId,
      linkedInventoryItemGlobalIdsByMoleculeId,
      onDeleteReagent,
      onPickInventoryItem,
      onRemoveInventoryLink,
      onSelectLimitingReagent,
    ],
  );

  const isCellEditable = React.useCallback(
    (params: GridCellParams<EditableMolecule>) => {
      if (!editable) {
        return false;
      }

      const { field, row } = params;
      if (limitingReagentId !== undefined && (field === "mass" || field === "moles")) {
        return row.id === limitingReagentId;
      }
      return true;
    },
    [editable, limitingReagentId],
  );

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
      <StoichiometryTableLoadingDialog open={isGettingMoleculeInfo} />
    </>
  );
};

const StoichiometryTableWithQueryData = ({
  stoichiometryId,
  stoichiometryRevision,
}: StoichiometryTableProps) => {
  const { getToken } = useOauthToken();
  const { data } = useGetStoichiometryQuery({
    stoichiometryId,
    revision: stoichiometryRevision,
    getToken,
  });
  const molecules = React.useMemo(
    () => toEditableMolecules(data),
    [data.id, data.revision],
  );

  return (
    <StoichiometryTableGrid
      editable={false}
      allMolecules={molecules}
    />
  );
};

const StoichiometryTable = ({
  stoichiometryId,
  stoichiometryRevision,
  editable = false,
  hasChanges = false,
}: StoichiometryTableProps) => {
  const tableController = useStoichiometryTableController();

  if (editable && tableController) {
    return (
      <StoichiometryTableGrid
        editable
        allMolecules={tableController.allMolecules}
        hasChanges={hasChanges}
        linkedInventoryQuantityInfoByGlobalId={
          tableController.linkedInventoryQuantityInfoByGlobalId
        }
        isGettingMoleculeInfo={tableController.isGettingMoleculeInfo}
        onAddReagent={tableController.addReagent}
        onUpdateInventoryStock={tableController.updateInventoryStock}
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
};

export default StoichiometryTable;
