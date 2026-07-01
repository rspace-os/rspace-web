import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Radio from "@mui/material/Radio";
import { DataGrid, type GridCellParams, type GridColDef } from "@mui/x-data-grid";
import React from "react";
import { useTranslation } from "react-i18next";
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
const EM_DASH = "—";
const getMoleculeRowId = (row: EditableMolecule) => row.id;
const getInventoryLinkExportValue = (row: EditableMolecule): string =>
  row.inventoryLink?.inventoryItemGlobalId ?? row.deletedInventoryLink?.inventoryItemGlobalId ?? "";
const getRoleExportValue = (
  role: string | null | undefined,
  translateRole: (
    key:
      | "stoichiometry.table.roles.reactant"
      | "stoichiometry.table.roles.product"
      | "stoichiometry.table.roles.reagent",
  ) => string,
): string => {
  if (!role) {
    return "";
  }
  switch (role.toUpperCase()) {
    case "AGENT":
      return translateRole("stoichiometry.table.roles.reagent");
    case "REACTANT":
      return translateRole("stoichiometry.table.roles.reactant");
    case "PRODUCT":
      return translateRole("stoichiometry.table.roles.product");
    default:
      return `${role.slice(0, 1)}${role.slice(1).toLowerCase()}`;
  }
};
export default function StoichiometryTableGrid({
  editable,
  allMolecules,
  hasChanges = false,
  activeChemId = null,
  linkedInventoryQuantityInfoByGlobalId = new Map<string, InventoryQuantityQueryResult>(),
  onAddReagent,
  onUpdateInventoryStock,
  onDeleteReagent,
  onPickInventoryItem,
  onRemoveInventoryLink,
  onUndoRemoveInventoryLink,
  onSelectLimitingReagent,
  onProcessRowUpdate,
}: StoichiometryTableGridProps): React.ReactNode {
  const { t } = useTranslation("common");
  const roleColumnEditable = editable && activeChemId === null;
  const limitingReagent = allMolecules.find(
    (molecule) => molecule.limitingReagent && molecule.role.toLowerCase() === "reactant",
  );
  const limitingReagentId = limitingReagent?.id;
  const linkedIds = allMolecules
    .map(
      (molecule) =>
        molecule.inventoryLink?.inventoryItemGlobalId ?? molecule.deletedInventoryLink?.inventoryItemGlobalId,
    )
    .filter((id): id is string => Boolean(id));
  const linkedInventoryItemGlobalIdsByMoleculeId = new Map(
    allMolecules.map((molecule) => [
      molecule.id,
      linkedIds.filter(
        (inventoryItemGlobalId) =>
          inventoryItemGlobalId !==
          (molecule.inventoryLink?.inventoryItemGlobalId ?? molecule.deletedInventoryLink?.inventoryItemGlobalId),
      ),
    ]),
  );
  const columns = React.useMemo<GridColDef<EditableMolecule>[]>(
    () => [
      {
        field: "actions",
        headerName: t("stoichiometry.table.columns.actions"),
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
              aria-label={t("stoichiometry.table.label.deleteReagent", { name: row.name })}
              onClick={() => {
                onDeleteReagent?.(row.id);
              }}
            >
              {t("actions.delete")}
            </Button>
          ) : (
            EM_DASH
          ),
      },
      {
        field: "name",
        headerName: t("stoichiometry.table.columns.name"),
        sortable: false,
        minWidth: 180,
      },
      {
        field: "inventoryLink",
        headerName: t("stoichiometry.table.columns.inventoryLink"),
        sortable: false,
        minWidth: 200,
        valueGetter: (_value, row) => getInventoryLinkExportValue(row),
        renderCell: ({ row }) => (
          <StoichiometryTableInventoryLinkCell
            inventoryLink={row.inventoryLink}
            isDeleted={Boolean(row.deletedInventoryLink) && !row.inventoryLink}
            moleculeName={row.name}
            editable={editable}
            showInsufficientStockWarning={
              getInventoryUpdateEligibility(row, linkedInventoryQuantityInfoByGlobalId).showInsufficientStockWarning
            }
            linkedInventoryItemGlobalIds={linkedInventoryItemGlobalIdsByMoleculeId.get(row.id) ?? []}
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
        headerName: t("stoichiometry.table.columns.type"),
        sortable: false,
        minWidth: 160,
        valueFormatter: (value) => getRoleExportValue(value, (key) => t(key)),
        renderCell: ({ row }) => {
          if (!roleColumnEditable) {
            return <StoichiometryTableRoleChip role={row.role || ""} />;
          }
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
            />
          );
        },
      },
      {
        field: "limitingReagent",
        headerName: t("stoichiometry.table.columns.limitingReagent"),
        sortable: false,
        minWidth: 180,
        align: "center",
        renderCell: (params) =>
          params.row.role.toLowerCase() === "reactant" ? (
            <Radio
              checked={params.row.limitingReagent || false}
              disabled={!editable}
              onChange={(event) => {
                if (event.target.checked && editable) {
                  onSelectLimitingReagent?.(params.row);
                }
              }}
              slotProps={{
                input: {
                  "aria-label": t("stoichiometry.table.label.selectLimitingReagent", { name: params.row.name }),
                },
              }}
            />
          ) : (
            EM_DASH
          ),
      },
      {
        field: "coefficient",
        headerName: t("stoichiometry.table.columns.equivalent"),
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
        headerName: t("stoichiometry.table.columns.molecularWeight"),
        sortable: false,
        minWidth: 220,
        type: "number",
        headerAlign: "left",
        renderCell: (params) =>
          params.value !== null && params.value !== undefined ? Number(params.value).toFixed(3) : EM_DASH,
      },
      {
        field: "mass",
        headerName: t("stoichiometry.table.columns.mass"),
        sortable: false,
        headerAlign: "left",
        minWidth: 120,
        type: "number",
        editable,
        renderCell: (params) =>
          params.value !== null && params.value !== undefined ? Number(params.value).toFixed(3) : EM_DASH,
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
        headerName: t("stoichiometry.table.columns.moles"),
        sortable: false,
        headerAlign: "left",
        type: "number",
        minWidth: 120,
        editable,
        renderCell: (params) =>
          params.value !== null && params.value !== undefined ? Number(params.value).toFixed(3) : EM_DASH,
        cellClassName: (params) => {
          if (limitingReagentId !== undefined && params.id !== limitingReagentId) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
      {
        field: "actualAmount",
        headerName: t("stoichiometry.table.columns.actualMass"),
        sortable: false,
        headerAlign: "left",
        minWidth: 150,
        editable,
        type: "number",
        renderCell: (params) =>
          params.value !== null && params.value !== undefined ? Number(params.value).toFixed(3) : EM_DASH,
      },
      {
        field: "actualMoles",
        valueGetter: (_value, row) => calculateMoles(row.actualAmount, row.molecularWeight),
        headerName: t("stoichiometry.table.columns.actualMoles"),
        sortable: false,
        headerAlign: "left",
        editable,
        minWidth: 180,
        type: "number",
        renderCell: (params) => {
          return params.value !== null ? Number(params.value).toFixed(3) : EM_DASH;
        },
      },
      {
        field: "actualYield",
        headerName: t("stoichiometry.table.columns.yieldExcess"),
        sortable: false,
        headerAlign: "left",
        minWidth: 150,
        type: "number",
        editable: false,
        renderCell: (params) => {
          if (limitingReagentId !== undefined && params.id === limitingReagentId) {
            return EM_DASH;
          }
          const value = params.value as number | null | undefined;
          return value !== null && value !== undefined
            ? `${Number((Number(Number(value).toFixed(3)) * 100).toFixed(3))}%`
            : EM_DASH;
        },
      },
      {
        field: "notes",
        headerName: t("stoichiometry.table.columns.notes"),
        sortable: false,
        minWidth: 200,
        type: "string",
        editable,
        renderCell: (params) => {
          const value = params.value as string | null | undefined;
          return value ?? EM_DASH;
        },
      },
    ],
    [
      editable,
      roleColumnEditable,
      limitingReagentId,
      linkedInventoryQuantityInfoByGlobalId,
      linkedInventoryItemGlobalIdsByMoleculeId,
      onDeleteReagent,
      onProcessRowUpdate,
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
    if (limitingReagentId !== undefined && (field === "mass" || field === "moles")) {
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
    [allMolecules, editable, hasChanges, linkedInventoryQuantityInfoByGlobalId, onAddReagent, onUpdateInventoryStock],
  );
  return (
    <Box
      sx={{
        width: "100%",
        minHeight: "1px",
        height: 1,
      }}
    >
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
