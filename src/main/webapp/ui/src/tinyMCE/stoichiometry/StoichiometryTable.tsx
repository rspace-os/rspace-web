import React, { useCallback } from "react";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Radio from "@mui/material/Radio";
import { lighten, useTheme } from "@mui/material/styles";
import useStoichiometry, {
  type StoichiometryResponse,
  type RsChemElement,
  StoichiometryRequest,
} from "../../hooks/api/useStoichiometry";
import AnalyticsContext from "../../stores/contexts/Analytics";
import type { EditableMolecule, StoichiometryTableRef } from "./types";
import { calculateMoles, calculateUpdatedMolecules } from "./utils";
import StoichiometryTableRoleChip from "@/tinyMCE/stoichiometry/StoichiometryTableRoleChip";
import StoichiometryTableToolbar from "@/tinyMCE/stoichiometry/StoichiometryTableToolbar";
import StoichiometryTableLoadingDialog from "@/tinyMCE/stoichiometry/StoichiometryTableLoadingDialog";

const StoichiometryTable = React.forwardRef<
  StoichiometryTableRef,
  {
    stoichiometryId: number;
    stoichiometryRevision: number;
    editable?: boolean;
    onChangesUpdate?: (hasChanges: boolean) => void;
  }
>(function StoichiometryTable(
  { editable = false, onChangesUpdate, stoichiometryId, stoichiometryRevision },
  ref,
) {
  const {
    getStoichiometry,
    updateStoichiometry,
    deleteStoichiometry,
    getMoleculeInfo,
  } = useStoichiometry();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const theme = useTheme();
  const [data, setData] = React.useState<StoichiometryResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [allMolecules, setAllMolecules] = React.useState<
    ReadonlyArray<EditableMolecule>
  >([]);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const [moleculeInfoLoading, setMoleculeInfoLoading] = React.useState(false);

  React.useEffect(() => {
    setLoading(true);
    setError(null);
    void (async () => {
      try {
        if (!stoichiometryId) throw new Error("stoichiometryId is required");
        const result = await getStoichiometry({
          stoichiometryId,
          revision: stoichiometryRevision,
        });
        setData(result);
      } catch (e) {
        console.error(e);
        setError("Failed to load stoichiometry data");
      } finally {
        setLoading(false);
      }
    })();
  }, [stoichiometryId]);

  React.useEffect(() => {
    if (data?.molecules) {
      const molecules = data.molecules.map((molecule) => ({
        ...molecule,
        /*
         * We add these properties to facilitate editing the computed values;
         * they will only ever not be null during the onChange event handler.
         * The displayed value for these field is always computed on the fly.
         */
        moles: null,
        actualMoles: null,
      }));
      const hasLimitingReagent = molecules.some(
        (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
      );

      // Usability enhancement: default first reactant as limiting reagent since it's usually the limiting one
      if (!hasLimitingReagent) {
        const firstReactant = molecules.find(
          (m) => m.role.toLowerCase() === "reactant",
        );

        if (firstReactant) {
          const updatedMolecules = molecules.map((m) =>
            m.id === firstReactant.id ? { ...m, limitingReagent: true } : m,
          );
          setAllMolecules(updatedMolecules);
        } else {
          setAllMolecules(molecules);
        }
      } else {
        setAllMolecules(molecules);
      }
    }
  }, [data]);

  React.useImperativeHandle(
    ref,
    () => ({
      save: async () => {
        if (!data || !data.id) {
          throw new Error("No stoichiometry data to save");
        }

        const updatedData: StoichiometryRequest = {
          ...data,
          molecules: allMolecules.map((m) => {
            if (m.id >= 0) return m;
            const smiles = m.rsChemElement.smilesString;
            if (!smiles)
              throw new Error("New reagents must have a SMILES string");
            if (!m.name) throw new Error("New reagents must have a name");
            /*
             * Remove the temporary ID we assigned to new molecules; the server
             * will assign a proper ID when it creates the new molecule.
             */
            const copy = { ...m };
            delete (copy as Partial<EditableMolecule>).id;
            return copy;
          }),
        };

        const { revision } = await updateStoichiometry({
          stoichiometryId: data.id,
          stoichiometryData: updatedData,
        });
        onChangesUpdate?.(false);
        return revision;
      },
      delete: async () => {
        if (!data || !data.id) {
          return;
        }
        await deleteStoichiometry({ stoichiometryId: data.id });
        setData(null);
        setAllMolecules([]);
        onChangesUpdate?.(false);
      },
    }),
    [data, allMolecules, updateStoichiometry],
  );

  const handleAddReagent = useCallback(
    async (smilesString: string, name: string, source: string) => {
      trackEvent("user:add:stoichiometry_reagent:document_editor", {
        source,
      });

      // Prevent concurrent requests
      if (moleculeInfoLoading) {
        throw new Error(
          "Please wait for the current reagent to be processed before adding another.",
        );
      }

      setMoleculeInfoLoading(true);

      try {
        const moleculeInfo = await getMoleculeInfo({ smiles: smilesString });

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
          role: "AGENT",
          formula: moleculeInfo.formula,
          name: name,
          smiles: smilesString,
          coefficient: 1,
          molecularWeight: moleculeInfo.molecularWeight,
          mass: ratio ? ratio * moleculeInfo.molecularWeight : 0,
          moles: null,
          expectedAmount: null,
          actualAmount: null,
          actualMoles: null,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        };

        setAllMolecules((prev) => [...prev, newMolecule]);
        onChangesUpdate?.(true);
      } catch (error) {
        // Error handling is done by getMoleculeInfo hook, just log it here
        console.error("Failed to fetch molecule information:", error);
      } finally {
        setMoleculeInfoLoading(false);
      }
    },
    [
      allMolecules,
      moleculeInfoLoading,
      getMoleculeInfo,
      onChangesUpdate,
      trackEvent,
    ],
  );

  const limitingReagent = allMolecules.find(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );

  const columns: GridColDef<EditableMolecule>[] = [
    {
      field: "name",
      headerName: "Name",
      sortable: false,
      flex: 1.5,
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
      flex: 1,
      renderCell: ({ row }) => <StoichiometryTableRoleChip role={row.role || ""} />,
    },
    {
      field: "limitingReagent",
      headerName: "Limiting Reagent",
      sortable: false,
      flex: 1,
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
      flex: 1,
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
      flex: 1.2,
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
      flex: 1,
      headerAlign: "left",
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
      flex: 1,
      headerAlign: "left",
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
      field: "actualAmount",
      headerName: "Actual Mass (g)",
      sortable: false,
      flex: 1,
      headerAlign: "left",
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
      flex: 1,
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
      flex: 1,
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
      flex: 1.5,
      type: "string",
      editable,
      renderCell: (params) => {
        const value = params.value as string | null | undefined;
        return value ?? <>&mdash;</>;
      },
    },
  ];

  if (loading) {
    return (
      <Box
        display="flex"
        flexDirection="column"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
        gap={1}
      >
        <CircularProgress size={24} aria-label="Loading stoichiometry table" />
        <Typography variant="body2" color="textSecondary">
          Loading stoichiometry table...
        </Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
      >
        <Typography color="error" variant="body2">
          {error}
        </Typography>
      </Box>
    );
  }

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
              if (value !== null && value !== undefined && Number(value) < 0) {
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
        slotProps={{
          toolbar: {
            setColumnsMenuAnchorEl,
            onAddReagent: handleAddReagent,
            editable,
            allMolecules,
          },
          panel: {
            anchorEl: columnsMenuAnchorEl,
          },
        }}
        sx={{
          border: "none",
          "& .MuiDataGrid-columnHeaders": {
            backgroundColor: "#f8f9fa",
            borderBottom: "2px solid #e0e0e0",
          },
          "& .MuiDataGrid-cell": {
            borderBottom: "1px solid #f0f0f0",
          },
          "& .MuiDataGrid-row:hover": {
            backgroundColor: "#f8f9fa",
          },
          "& .stoichiometry-disabled-cell": {
            backgroundColor: `${lighten(theme.palette.primary.background, 0.3)} !important`,
            color: `${theme.palette.primary.contrastText} !important`,
            fontStyle: "italic",
          },
        }}
      />
      <StoichiometryTableLoadingDialog open={moleculeInfoLoading} />
    </>
  );
});

export default StoichiometryTable;
