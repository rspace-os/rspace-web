import React, { useCallback } from "react";
import { produce } from "immer";
import { useQueryClient } from "@tanstack/react-query";
import AnalyticsContext from "../../stores/contexts/Analytics";
import useOauthToken from "@/hooks/auth/useOauthToken";
import {
  inventoryQueryKeys,
  useSubSampleQuantitiesQuery,
} from "@/modules/inventory/queries";
import {
  useDeductStockMutation,
  useDeleteStoichiometryMutation,
  useGetMoleculeInfoMutation,
  useUpdateStoichiometryMutation,
} from "@/modules/stoichiometry/mutations";
import {
  getStoichiometry,
  stoichiometryQueryKeys,
  useGetStoichiometryQuery,
} from "@/modules/stoichiometry/queries";
import type {
  ExistingMoleculeUpdate,
  NewMolecule,
  RsChemElement,
  StoichiometryRequest,
} from "@/modules/stoichiometry/schema";
import { resolveToken } from "@/modules/common/utils/auth";
import { toEditableMolecules } from "@/tinyMCE/stoichiometry/editableMolecules";
import type { StoichiometryTableController } from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import {
  calculateMoles,
  calculateUpdatedMolecules,
  getInventoryUpdateEligibility,
  hasDuplicateInventoryLink,
} from "@/tinyMCE/stoichiometry/utils";

export function useEditableStoichiometryTable({
  stoichiometryId,
  stoichiometryRevision,
}: {
  stoichiometryId: number;
  stoichiometryRevision: number;
}) {
  const { getToken } = useOauthToken();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const queryClient = useQueryClient();
  const updateStoichiometryMutation = useUpdateStoichiometryMutation({
    getToken,
  });
  const deductStockMutation = useDeductStockMutation({
    getToken,
  });
  const deleteStoichiometryMutation = useDeleteStoichiometryMutation({
    getToken,
  });
  const getMoleculeInfoMutation = useGetMoleculeInfoMutation({
    getToken,
  });
  const [allMolecules, setAllMolecules] = React.useState<
    ReadonlyArray<EditableMolecule>
  >([]);
  const [hasChanges, setHasChanges] = React.useState(false);
  const { data: queriedStoichiometry, isFetching } = useGetStoichiometryQuery({
    stoichiometryId,
    revision: stoichiometryRevision,
    getToken,
  });
  const data = queriedStoichiometry;
  const stoichiometryVersionKey = `${queriedStoichiometry.id}:${queriedStoichiometry.revision}`;
  const initialMolecules = React.useMemo(
    () => toEditableMolecules(queriedStoichiometry),
    [stoichiometryVersionKey],
  );
  const initialisedVersionRef = React.useRef<string | null>(null);
  const isSaving = updateStoichiometryMutation.isPending;
  const isUpdatingInventoryStock = deductStockMutation.isPending;
  const isDeleting = deleteStoichiometryMutation.isPending;
  const isGettingMoleculeInfo = getMoleculeInfoMutation.isPending;
  const isBusy =
    isSaving ||
    isUpdatingInventoryStock ||
    isDeleting ||
    isGettingMoleculeInfo ||
    isFetching;
  const linkedInventoryItemGlobalIds = React.useMemo(
    () =>
      Array.from(
        new Set(
          allMolecules
            .map((molecule) => molecule.inventoryLink?.inventoryItemGlobalId)
            .filter((id): id is string => Boolean(id)),
        ),
      ),
    [allMolecules],
  );
  const subSampleQuantitiesByGlobalId = useSubSampleQuantitiesQuery({
    inventoryItemGlobalIds: linkedInventoryItemGlobalIds,
    getToken,
  });

  React.useEffect(() => {
    if (initialisedVersionRef.current === stoichiometryVersionKey) {
      return;
    }

    initialisedVersionRef.current = stoichiometryVersionKey;
    setAllMolecules(initialMolecules);
    setHasChanges(false);
  }, [initialMolecules, stoichiometryVersionKey]);

  const markChanged = useCallback(() => {
    setHasChanges(true);
  }, []);

  const save = useCallback(async () => {
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
            name: m.name ?? undefined,
            formula: m.formula ?? undefined,
            molecularWeight: m.molecularWeight ?? undefined,
            coefficient: m.coefficient ?? undefined,
            mass: m.mass,
            actualAmount: m.actualAmount,
            actualYield: m.actualYield,
            limitingReagent: m.limitingReagent ?? undefined,
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

        return {
          role: "AGENT",
          smiles,
          name,
          coefficient: m.coefficient ?? undefined,
          mass: m.mass,
          actualAmount: m.actualAmount,
          actualYield: m.actualYield,
          limitingReagent: m.limitingReagent ?? undefined,
          notes: m.notes,
          inventoryLink: m.inventoryLink ?? null,
        } as NewMolecule;
      }),
    };

    const updatedStoichiometry = await updateStoichiometryMutation.mutateAsync({
      stoichiometryId: data.id,
      stoichiometryData: updatedData,
    });

    setHasChanges(false);
    return updatedStoichiometry.revision;
  }, [allMolecules, data, updateStoichiometryMutation]);

  const deleteTable = useCallback(async () => {
    if (!data || !data.id) {
      return;
    }

    await deleteStoichiometryMutation.mutateAsync({
      stoichiometryId: data.id,
    });
    setAllMolecules([]);
    setHasChanges(false);
  }, [data, deleteStoichiometryMutation]);

  const addReagent = useCallback(
    async (smilesString: string, name: string, source: string) => {
      trackEvent("user:add:stoichiometry_reagent:document_editor", {
        source,
      });

      if (isGettingMoleculeInfo) {
        throw new Error(
          "Please wait for the current reagent to be processed before adding another.",
        );
      }

      try {
        const moleculeInfo = await getMoleculeInfoMutation.mutateAsync({
          smiles: smilesString,
        });

        const tempId = -(allMolecules.length + 1);

        const mockRsChemElement: RsChemElement = {
          id: tempId,
          parentId: null,
          ecatChemFileId: null,
          dataImage: null,
          chemElements: smilesString,
          smilesString,
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: null,
          chemElementsFormat: "SMILES",
          creationDate: Date.now(),
          imageFileProperty: null,
        };

        const limitingReagent = allMolecules.find((m) => m.limitingReagent);

        const limitingReagentMoles = limitingReagent ? calculateMoles(
          limitingReagent.mass,
          limitingReagent.molecularWeight,
        ) : null;
        const ratio =
          !limitingReagent?.coefficient || limitingReagentMoles === null
            ? null
            : limitingReagentMoles / limitingReagent.coefficient;

        const newMolecule: EditableMolecule = {
          id: tempId,
          rsChemElement: mockRsChemElement,
          inventoryLink: null,
          role: "AGENT",
          formula: moleculeInfo.formula,
          name,
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
        markChanged();
      } catch (error) {
        console.error("Failed to fetch molecule information:", error);
      }
    },
    [
      allMolecules,
      getMoleculeInfoMutation,
      isGettingMoleculeInfo,
      markChanged,
      trackEvent,
    ],
  );

  const pickInventoryLink = useCallback(
    (moleculeId: number, inventoryItemId: number, inventoryItemGlobalId: string) => {
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
            id: existingLink?.id ?? inventoryItemId,
            inventoryItemGlobalId,
            stoichiometryMoleculeId: molecule.id,
            quantity: existingLink?.quantity ?? {
              numericValue: 1,
              unitId: 1,
            },
            stockDeducted: existingLink?.stockDeducted,
          };
        }),
      );
      markChanged();
    },
    [allMolecules, markChanged],
  );

  const removeInventoryLink = useCallback(
    (moleculeId: number) => {
      setAllMolecules((prevMolecules) =>
        produce(prevMolecules, (draftMolecules) => {
          const molecule = draftMolecules.find((m) => m.id === moleculeId);
          if (molecule) {
            molecule.inventoryLink = null;
          }
        }),
      );
      markChanged();
    },
    [markChanged],
  );

  const invalidateLinkedInventoryAmounts = useCallback(
    async (inventoryItemGlobalIds: ReadonlyArray<string>) => {
      const uniqueInventoryItemGlobalIdSet = new Set(
        inventoryItemGlobalIds.filter(Boolean),
      );

      if (uniqueInventoryItemGlobalIdSet.size === 0) {
        return;
      }

      await queryClient.invalidateQueries({
        queryKey: inventoryQueryKeys.all,
        predicate: (query) => {
          const [, keyType, inventoryItemGlobalId] = query.queryKey;

          return (
            keyType === "subSampleQuantity" &&
            typeof inventoryItemGlobalId === "string" &&
            uniqueInventoryItemGlobalIdSet.has(inventoryItemGlobalId)
          );
        },
      });
    },
    [queryClient],
  );

  const updateInventoryStock = useCallback(
    async (selectedMoleculeIds: number[]) => {
      if (!data?.id) {
        throw new Error("No stoichiometry data available to update stock");
      }

      const preflightResults: Array<{
        moleculeId: number;
        moleculeName: string;
        success: boolean;
        errorMessage: string | null;
      }> = [];
      const deductibleLinks: Array<{
        linkId: number;
        moleculeId: number;
        moleculeName: string;
        inventoryItemGlobalId: string;
      }> = [];

      for (const moleculeId of selectedMoleculeIds) {
        const molecule = allMolecules.find(({ id }) => id === moleculeId);
        const moleculeName = molecule?.name ?? "Unnamed molecule";
        const inventoryItemGlobalId = molecule?.inventoryLink?.inventoryItemGlobalId;

        if (
          !molecule ||
          !inventoryItemGlobalId ||
          typeof molecule.inventoryLink?.id !== "number"
        ) {
          preflightResults.push({
            moleculeId,
            moleculeName,
            success: false,
            errorMessage: "Link an inventory item before updating stock.",
          });
          continue;
        }

        const eligibility = getInventoryUpdateEligibility(
          molecule,
          subSampleQuantitiesByGlobalId,
        );

        if (eligibility.disabledReason !== null) {
          preflightResults.push({
            moleculeId,
            moleculeName,
            success: false,
            errorMessage:
              eligibility.helperText ??
              eligibility.stockDisplay.warningText ??
              "This molecule cannot be updated.",
          });
          continue;
        }

        deductibleLinks.push({
          linkId: molecule.inventoryLink.id,
          moleculeId,
          moleculeName,
          inventoryItemGlobalId,
        });
      }

      if (deductibleLinks.length === 0) {
        return {
          results: preflightResults,
        };
      }

      const attemptedInventoryItemGlobalIds = deductibleLinks.map(
        ({ inventoryItemGlobalId }) => inventoryItemGlobalId,
      );

      try {
        const deductionResult = await deductStockMutation.mutateAsync({
          stoichiometryId: data.id,
          linkIds: deductibleLinks.map(({ linkId }) => linkId),
        });
        const deductionResultByLinkId = new Map(
          deductionResult.results.map((result) => [result.linkId, result]),
        );
        const batchResults = deductibleLinks.map(
          ({ linkId, moleculeId, moleculeName }) => {
            const result = deductionResultByLinkId.get(linkId);

            if (!result) {
              return {
                moleculeId,
                moleculeName,
                success: false,
                errorMessage:
                  "No deduction result was returned for this molecule.",
              };
            }

            return {
              moleculeId,
              moleculeName,
              success: result.success,
              errorMessage: result.success
                ? null
                : result.errorMessage ?? "Failed to update inventory stock.",
            };
          },
        );

        await queryClient.invalidateQueries({
          queryKey: stoichiometryQueryKeys.all,
        });

        const refreshedStoichiometry = await queryClient.fetchQuery({
          queryKey: stoichiometryQueryKeys.byId(
            deductionResult.stoichiometryId,
            deductionResult.revisionNumber,
          ),
          queryFn: async () =>
            getStoichiometry({
              stoichiometryId: deductionResult.stoichiometryId,
              revision: deductionResult.revisionNumber,
              token: await resolveToken({ getToken }),
            }),
        });

        queryClient.setQueryData(
          stoichiometryQueryKeys.byId(
            deductionResult.stoichiometryId,
            deductionResult.revisionNumber,
          ),
          refreshedStoichiometry,
        );

        await invalidateLinkedInventoryAmounts(linkedInventoryItemGlobalIds);

        return {
          refreshedStoichiometry: {
            id: refreshedStoichiometry.id,
            revision: refreshedStoichiometry.revision,
          },
          results: [...preflightResults, ...batchResults],
        };
      } catch (error) {
        await invalidateLinkedInventoryAmounts(attemptedInventoryItemGlobalIds);
        throw error;
      }
    },
    [
      allMolecules,
      data,
      deductStockMutation,
      getToken,
      invalidateLinkedInventoryAmounts,
      linkedInventoryItemGlobalIds,
      queryClient,
      subSampleQuantitiesByGlobalId,
    ],
  );

  const deleteReagent = useCallback(
    (moleculeId: number) => {
      const moleculeToDelete = allMolecules.find((m) => m.id === moleculeId);
      if (!moleculeToDelete || moleculeToDelete.role.toLowerCase() !== "agent") {
        return;
      }
      setAllMolecules((prevMolecules) =>
        prevMolecules.filter((m) => m.id !== moleculeId),
      );
      markChanged();
    },
    [allMolecules, markChanged],
  );

  const selectLimitingReagent = useCallback(
    (molecule: EditableMolecule) => {
      const updatedRow = { ...molecule, limitingReagent: true };
      const newMolecules = calculateUpdatedMolecules(allMolecules, updatedRow);
      setAllMolecules(newMolecules);
      markChanged();
    },
    [allMolecules, markChanged],
  );

  const processRowUpdate = useCallback(
    (newRow: EditableMolecule, oldRow: EditableMolecule) => {
      try {
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

        const newMolecules = calculateUpdatedMolecules(allMolecules, newRow);
        setAllMolecules(newMolecules);
        markChanged();
        return newMolecules.find((m) => m.id === newRow.id) || oldRow;
      } catch (error) {
        console.error("Error updating row:", (error as Error).message);
        return oldRow;
      }
    },
    [allMolecules, markChanged],
  );

  const tableController = React.useMemo<StoichiometryTableController>(
    () => ({
      allMolecules,
      linkedInventoryQuantityInfoByGlobalId: subSampleQuantitiesByGlobalId,
      isGettingMoleculeInfo,
      addReagent,
      deleteReagent,
      updateInventoryStock,
      pickInventoryLink,
      removeInventoryLink,
      selectLimitingReagent,
      processRowUpdate,
    }),
    [
      addReagent,
      allMolecules,
      deleteReagent,
      isGettingMoleculeInfo,
      pickInventoryLink,
      processRowUpdate,
      removeInventoryLink,
      selectLimitingReagent,
      subSampleQuantitiesByGlobalId,
      updateInventoryStock,
    ],
  );

  return {
    data,
    allMolecules,
    hasChanges,
    isBusy,
    isDeleting,
    isGettingMoleculeInfo,
    isSaving,
    tableController,
    save,
    deleteTable,
  };
}

