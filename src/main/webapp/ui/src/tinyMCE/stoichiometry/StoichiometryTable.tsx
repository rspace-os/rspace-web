import React, { useCallback } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Radio from "@mui/material/Radio";
import { produce } from "immer";
import AnalyticsContext from "../../stores/contexts/Analytics";
import type { EditableMolecule } from "./types";
import {
  calculateMoles,
  calculateUpdatedMolecules,
  hasDuplicateInventoryLink,
} from "./utils";
import useOauthToken from "@/hooks/auth/useOauthToken";
import {
  useDeleteStoichiometryMutation,
  useGetMoleculeInfoMutation,
  useUpdateStoichiometryMutation,
} from "@/modules/stoichiometry/mutations";
import { useGetStoichiometryQuery } from "@/modules/stoichiometry/queries";
import type {
  RsChemElement,
  ExistingMoleculeUpdate,
  NewReagent,
  StoichiometryRequest,
  StoichiometryResponse,
} from "@/modules/stoichiometry/schema";
import StoichiometryTableInventoryLinkCell from "@/tinyMCE/stoichiometry/StoichiometryTableInventoryLinkCell";
import StoichiometryTableRoleChip from "@/tinyMCE/stoichiometry/StoichiometryTableRoleChip";
import StoichiometryTableToolbar from "@/tinyMCE/stoichiometry/StoichiometryTableToolbar";
import StoichiometryTableLoadingDialog from "@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog";
import { STOICHIOMETRY_TABLE_CLASS } from "@/tinyMCE/stoichiometry/theme";

function toEditableMolecules(
  stoichiometry: StoichiometryResponse,
): ReadonlyArray<EditableMolecule> {
  const molecules = stoichiometry.molecules.map((molecule) => ({
    ...molecule,
    /*
     * We add these properties to facilitate editing the computed values;
     * they will only ever not be null during the onChange event handler.
     * The displayed value for these fields is always computed on the fly.
     */
    moles: null,
    actualMoles: null,
  }));
  const hasLimitingReagent = molecules.some(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );

  if (hasLimitingReagent) {
    return molecules;
  }

  const firstReactant = molecules.find(
    (m) => m.role.toLowerCase() === "reactant",
  );
  if (!firstReactant) {
    return molecules;
  }

  return produce(molecules, (draftMolecules) => {
    const firstReactantDraft = draftMolecules.find(
      (m) => m.id === firstReactant.id,
    );
    if (firstReactantDraft) {
      firstReactantDraft.limitingReagent = true;
    }
  });
}

type StoichiometryTableProps = {
  stoichiometryId: number;
  stoichiometryRevision: number;
  editable?: boolean;
  onChangesUpdate?: (hasChanges: boolean) => void;
  saveRequestId?: number;
  deleteRequestId?: number;
  onSaveSuccess?: (revision: number) => void;
  onSaveError?: (error: Error) => void;
  onDeleteSuccess?: () => void;
  onDeleteError?: (error: Error) => void;
};

function StoichiometryTable({
  editable = false,
  onChangesUpdate,
  stoichiometryId,
  stoichiometryRevision,
  saveRequestId,
  deleteRequestId,
  onSaveSuccess,
  onSaveError,
  onDeleteSuccess,
  onDeleteError,
}: StoichiometryTableProps) {
  const { getToken } = useOauthToken();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [localData, setLocalData] = React.useState<StoichiometryResponse | null>(
    null,
  );
  const [allMolecules, setAllMolecules] = React.useState<
    ReadonlyArray<EditableMolecule>
  >([]);
  const [lastHandledSaveRequestId, setLastHandledSaveRequestId] =
    React.useState<number | undefined>(undefined);
  const [lastHandledDeleteRequestId, setLastHandledDeleteRequestId] =
    React.useState<number | undefined>(undefined);
  const updateStoichiometryMutation = useUpdateStoichiometryMutation({
    getToken,
  });
  const deleteStoichiometryMutation = useDeleteStoichiometryMutation({
    getToken,
  });
  const getMoleculeInfoMutation = useGetMoleculeInfoMutation({
    getToken,
  });

  const { data: queriedStoichiometry } = useGetStoichiometryQuery({
    stoichiometryId,
    revision: stoichiometryRevision,
    getToken,
  });
  const data = localData ?? queriedStoichiometry;

  React.useEffect(() => {
    setLocalData((currentData) => {
      if (!currentData) {
        return queriedStoichiometry;
      }
      if (currentData.id !== queriedStoichiometry.id) {
        return queriedStoichiometry;
      }
      return queriedStoichiometry.revision >= currentData.revision
        ? queriedStoichiometry
        : currentData;
    });
  }, [queriedStoichiometry]);

  React.useEffect(() => {
    if (data?.molecules) {
      setAllMolecules(toEditableMolecules(data));
    }
  }, [data]);

  const saveTable = useCallback(async () => {
    if (!data || !data.id) {
      throw new Error("No stoichiometry data to save");
    }

    const updatedData: StoichiometryRequest = {
      id: data.id,
      molecules: allMolecules.map((m) => {
        if (m.id >= 0) {
          const existingMoleculeUpdate: ExistingMoleculeUpdate = {
            id: m.id,
            role: m.role,
            smiles: m.smiles,
            name: m.name,
            formula: m.formula,
            molecularWeight: m.molecularWeight,
            coefficient: m.coefficient,
            mass: m.mass,
            actualAmount: m.actualAmount,
            actualYield: m.actualYield,
            limitingReagent: m.limitingReagent,
            notes: m.notes,
            inventoryLink: m.inventoryLink ?? null,
          };
          return existingMoleculeUpdate;
        }

        const smiles = m.smiles.trim();
        if (!smiles) {
          throw new Error("New reagents must have a SMILES string");
        }

        const name = m.name?.trim();
        if (!name) {
          throw new Error("New reagents must have a name");
        }

        const newReagent: NewReagent = {
          role: "AGENT",
          smiles,
          name,
        };
        return newReagent;
      }),
    };

    const updatedStoichiometry = await updateStoichiometryMutation.mutateAsync({
      stoichiometryId: data.id,
      stoichiometryData: updatedData,
    });

    setLocalData(updatedStoichiometry);
    setAllMolecules(toEditableMolecules(updatedStoichiometry));
    onChangesUpdate?.(false);
    return updatedStoichiometry.revision;
  }, [allMolecules, data, onChangesUpdate, updateStoichiometryMutation]);

  const deleteTable = useCallback(async () => {
    if (!data || !data.id) {
      return;
    }

    await deleteStoichiometryMutation.mutateAsync({
      stoichiometryId: data.id,
    });
    setLocalData(null);
    setAllMolecules([]);
    onChangesUpdate?.(false);
  }, [data, deleteStoichiometryMutation, onChangesUpdate]);

  React.useEffect(() => {
    if (
      saveRequestId === undefined ||
      saveRequestId === lastHandledSaveRequestId
    ) {
      return;
    }

    setLastHandledSaveRequestId(saveRequestId);
    void (async () => {
      try {
        const revision = await saveTable();
        onSaveSuccess?.(revision);
      } catch (error) {
        onSaveError?.(
          error instanceof Error
            ? error
            : new Error("Failed to save stoichiometry table"),
        );
      }
    })();
  }, [
    lastHandledSaveRequestId,
    onSaveError,
    onSaveSuccess,
    saveRequestId,
    saveTable,
  ]);

  React.useEffect(() => {
    if (
      deleteRequestId === undefined ||
      deleteRequestId === lastHandledDeleteRequestId
    ) {
      return;
    }

    setLastHandledDeleteRequestId(deleteRequestId);
    void (async () => {
      try {
        await deleteTable();
        onDeleteSuccess?.();
      } catch (error) {
        onDeleteError?.(
          error instanceof Error
            ? error
            : new Error("Failed to delete stoichiometry table"),
        );
      }
    })();
  }, [
    deleteRequestId,
    deleteTable,
    lastHandledDeleteRequestId,
    onDeleteError,
    onDeleteSuccess,
  ]);

  const handleAddReagent = useCallback(
    async (smilesString: string, name: string, source: string) => {
      trackEvent("user:add:stoichiometry_reagent:document_editor", {
        source,
      });

      // Prevent concurrent requests
      if (getMoleculeInfoMutation.isPending) {
        throw new Error(
          "Please wait for the current reagent to be processed before adding another.",
        );
      }

      try {
        const moleculeInfo = await getMoleculeInfoMutation.mutateAsync({
          smiles: smilesString,
        });

        // Generate a temporary unique ID for the new molecule (negative to distinguish from server IDs)
        const tempId = -(allMolecules.length + 1);

        const mockRsChemElement: RsChemElement = {
          id: tempId,
          parentId: null,
          ecatChemFileId: null,
          dataImage: null,
          chemElements: smilesString,
          smilesString: smilesString,
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: null,
          chemElementsFormat: "SMILES",
          creationDate: Date.now(),
          imageFileProperty: null,
        };

        const limitingReagent = allMolecules.find((m) => m.limitingReagent);
        if (!limitingReagent) throw new Error("No limiting reagent defined");
        if (!limitingReagent.mass || !limitingReagent.molecularWeight || !limitingReagent.coefficient) {
          throw new Error(
            "Limiting reagent must have mass, molecular weight and coefficient defined",
          );
        }
        const limitingReagentMoles = calculateMoles(
          limitingReagent.mass,
          limitingReagent.molecularWeight,
        );
        const ratio =
          limitingReagentMoles === null
            ? null
            : limitingReagentMoles / limitingReagent.coefficient;

        const newMolecule: EditableMolecule = {
          id: tempId,
          rsChemElement: mockRsChemElement,
          inventoryLink: null,
          role: "AGENT",
          formula: moleculeInfo.formula,
          name: name,
          smiles: smilesString,
          coefficient: 1,
          molecularWeight: moleculeInfo.molecularWeight,
          mass: ratio ? ratio * moleculeInfo.molecularWeight : 0,
          moles: null,
          actualAmount: null,
          actualMoles: null,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        };

        setAllMolecules((prevMolecules) =>
          produce(prevMolecules, (draftMolecules) => {
            draftMolecules.push(newMolecule);
          }),
        );
        onChangesUpdate?.(true);
      } catch (error) {
        // Error handling is done by getMoleculeInfo hook, just log it here
        console.error("Failed to fetch molecule information:", error);
      }
    },
    [
      allMolecules,
      getMoleculeInfoMutation,
      onChangesUpdate,
      trackEvent,
    ],
  );

  const limitingReagent = allMolecules.find(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );

  const handleInventoryLinkPick = useCallback(
    (moleculeId: number, inventoryItemGlobalId: string) => {
      if (!editable) {
        return;
      }
      if (
        hasDuplicateInventoryLink(
          allMolecules,
          moleculeId,
          inventoryItemGlobalId,
        )
      ) {
        return;
      }

      setAllMolecules((prevMolecules) =>
        produce(prevMolecules, (draftMolecules) => {
          const molecule = draftMolecules.find((m) => m.id === moleculeId);
          if (!molecule) {
            return;
          }

          const existingLink = molecule.inventoryLink;
          molecule.inventoryLink = {
            id: existingLink?.id ?? 0,
            inventoryItemGlobalId,
            stoichiometryMoleculeId: molecule.id,
            quantity: existingLink?.quantity ?? {
              numericValue: 1,
              unitId: 1,
            },
            reducesStock: existingLink?.reducesStock,
          };
        }),
      );
      onChangesUpdate?.(true);
    },
    [allMolecules, editable, onChangesUpdate],
  );

  const handleInventoryLinkDelete = useCallback(
    (moleculeId: number) => {
      if (!editable) {
        return;
      }
      setAllMolecules((prevMolecules) =>
        produce(prevMolecules, (draftMolecules) => {
          const molecule = draftMolecules.find((m) => m.id === moleculeId);
          if (molecule) {
            molecule.inventoryLink = null;
          }
        }),
      );
      onChangesUpdate?.(true);
    },
    [editable, onChangesUpdate],
  );

  const handleDeleteReagent = useCallback(
    (moleculeId: number) => {
      if (!editable) {
        return;
      }
      const moleculeToDelete = allMolecules.find((m) => m.id === moleculeId);
      if (!moleculeToDelete || moleculeToDelete.role.toLowerCase() !== "agent") {
        return;
      }
      setAllMolecules((prevMolecules) =>
        prevMolecules.filter((m) => m.id !== moleculeId),
      );
      onChangesUpdate?.(true);
    },
    [allMolecules, editable, onChangesUpdate],
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
              handleDeleteReagent(row.id);
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
            onPickInventoryItem={(inventoryItemGlobalId) =>
              handleInventoryLinkPick(row.id, inventoryItemGlobalId)
            }
            onRemoveInventoryLink={() => {
              handleInventoryLinkDelete(row.id);
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
                const updatedRow = { ...params.row, limitingReagent: true };
                const newMolecules = calculateUpdatedMolecules(
                  allMolecules,
                  updatedRow,
                );
                setAllMolecules(newMolecules);
                onChangesUpdate?.(true);
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
        params.value !== null && params.value !== undefined
          ? Number(params.value).toFixed(3)
          : <>&#8212;</>,
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
        params.value !== null && params.value !== undefined
          ? Number(params.value).toFixed(3)
          : <>&#8212;</>,
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
        params.value !== null && params.value !== undefined
          ? Number(params.value).toFixed(3)
          : <>&#8212;</>,
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
        params.value !== null && params.value !== undefined
          ? Number(params.value).toFixed(3)
          : <>&mdash;</>,
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
        // Don't show yield for limiting reagent
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

  if (!data || !data.molecules?.length) {
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
          rows={allMolecules ?? []}
          columns={columns}
          isCellEditable={(params) => {
            const { field, row } = params as {
              field: string;
              row: EditableMolecule;
            };
            /*
             * When a limiting reagent is defined, the user may only edit its
             * mass or moles, with the other masses and moles being computed
             * based on the coefficients. As such, we're preventing those cells
             * from being editable here. All other cells of all other editable
             * columns are unaffected with MUI applying an implicit conjunction
             * between the result of this function and the column's `editable`
             * property.
             */
            if (limitingReagent && (field === "mass" || field === "moles"))
              return row.id === limitingReagent.id;
            return true;
          }}
          hideFooter
          disableColumnFilter
          getRowId={(row) => row.id}
          processRowUpdate={(newRow, oldRow) => {
            try {
              /*
               * Validate for negative values in numerical fields.
               *
               * Note: We implement validation here in processRowUpdate rather than
               * using preProcessEditCellProps because the latter can interfere with
               * normal cell editing operations, even when not actively setting errors.
               * Using processRowUpdate allows us to properly validate and revert
               * changes by returning the old row, providing a cleaner user experience.
               */
              const numericalFields = [
                "coefficient",
                "mass",
                "moles",
                "actualAmount",
                "actualMoles",
              ];
              for (const field of numericalFields) {
                const value = newRow[field as keyof EditableMolecule];
                if (
                  value !== null &&
                  value !== undefined &&
                  Number(value) < 0
                ) {
                  throw new Error(`${field} cannot be negative`);
                }
              }

              const newMolecules = calculateUpdatedMolecules(
                allMolecules,
                newRow,
              );
              setAllMolecules(newMolecules);
              onChangesUpdate?.(true);
              return newMolecules.find((m) => m.id === newRow.id) || newRow;
            } catch (error) {
              console.error("Error updating row:", (error as Error).message);
              return oldRow; // Return the old row to revert the change
            }
          }}
          slots={{
            toolbar: StoichiometryTableToolbar,
          }}
          showToolbar={true}
          slotProps={{
            toolbar: {
              onAddReagent: handleAddReagent,
              editable,
              allMolecules,
            },
          }}
          autosizeOnMount
          className={STOICHIOMETRY_TABLE_CLASS}
        />
      </Box>
      <StoichiometryTableLoadingDialog
        open={getMoleculeInfoMutation.isPending}
      />
    </>
  );
}

export default StoichiometryTable;
