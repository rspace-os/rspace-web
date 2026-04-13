import * as v from "valibot";

export const STOICHIOMETRY_ROLES = {
  REACTANT: "REACTANT",
  PRODUCT: "PRODUCT",
  AGENT: "AGENT",
} as const;

export const StoichiometryRoleSchema = v.picklist(
  Object.values(STOICHIOMETRY_ROLES),
);

export const RsChemElementSchema = v.objectWithRest(
  {
    id: v.number(),
    parentId: v.nullable(v.number()),
    ecatChemFileId: v.nullable(v.string()),
    dataImage: v.nullable(v.string()),
    chemElements: v.string(),
    smilesString: v.nullable(v.string()),
    chemId: v.nullable(v.string()),
    reactionId: v.nullable(v.string()),
    rgroupId: v.nullable(v.string()),
    metadata: v.nullable(v.string()),
    chemElementsFormat: v.string(),
    creationDate: v.number(),
    imageFileProperty: v.nullable(v.unknown()),
  },
  v.unknown(),
);
export type RsChemElement = v.InferOutput<typeof RsChemElementSchema>;

export const InventoryLinkSchema = v.objectWithRest(
  {
    id: v.number(),
    inventoryItemGlobalId: v.string(),
    stockDeducted: v.optional(v.boolean()),
  },
  v.unknown(),
);
export type InventoryLink = v.InferOutput<typeof InventoryLinkSchema>;

export const InventoryLinkRequestSchema = v.objectWithRest(
  {
    inventoryItemGlobalId: v.string(),
  },
  v.unknown(),
);
export type InventoryLinkRequest = v.InferOutput<typeof InventoryLinkRequestSchema>;

export const StoichiometryMoleculeSchema = v.object({
  id: v.number(),
  rsChemElement: v.optional(v.nullable(RsChemElementSchema)),
  rsChemElementId: v.optional(v.nullable(v.number())),
  inventoryLink: v.optional(v.nullable(InventoryLinkSchema)),
  role: StoichiometryRoleSchema,
  formula: v.nullable(v.string()),
  name: v.nullable(v.string()),
  smiles: v.string(),
  coefficient: v.nullable(v.number()),
  molecularWeight: v.nullable(v.number()),
  mass: v.nullable(v.number()),
  moles: v.optional(v.nullable(v.number())),
  actualAmount: v.nullable(v.number()),
  actualYield: v.nullable(v.number()),
  limitingReagent: v.nullable(v.boolean()),
  notes: v.nullable(v.string()),
});

export type StoichiometryMolecule = v.InferOutput<
  typeof StoichiometryMoleculeSchema
>;

export const ParentReactionSchema = v.objectWithRest(
  {
    id: v.number(),
    parentId: v.number(),
    ecatChemFileId: v.nullable(v.string()),
    dataImage: v.string(),
    chemElements: v.string(),
    smilesString: v.string(),
    chemId: v.nullable(v.string()),
    reactionId: v.nullable(v.string()),
    rgroupId: v.nullable(v.string()),
    metadata: v.string(),
    chemElementsFormat: v.string(),
    creationDate: v.number(),
    imageFileProperty: v.unknown(),
  },
  v.unknown(),
);
export type ParentReaction = v.InferOutput<typeof ParentReactionSchema>;

export const StoichiometryResponseSchema = v.objectWithRest(
  {
    id: v.number(),
    revision: v.number(),
    parentReaction: v.optional(v.nullable(ParentReactionSchema)),
    parentReactionId: v.optional(v.nullable(v.number())),
    recordId: v.optional(v.nullable(v.number())),
    molecules: v.array(StoichiometryMoleculeSchema),
  },
  v.unknown(),
);
export type StoichiometryResponse = v.InferOutput<
  typeof StoichiometryResponseSchema
>;

export const ExistingMoleculeUpdateSchema = v.objectWithRest(
  {
    id: v.number(),
    role: v.optional(StoichiometryRoleSchema),
    smiles: v.optional(v.string()),
    name: v.optional(v.nullable(v.string())),
    formula: v.optional(v.string()),
    molecularWeight: v.optional(v.number()),
    coefficient: v.optional(v.number()),
    mass: v.optional(v.nullable(v.number())),
    actualAmount: v.optional(v.nullable(v.number())),
    actualYield: v.optional(v.nullable(v.number())),
    limitingReagent: v.optional(v.boolean()),
    notes: v.optional(v.nullable(v.string())),
    inventoryLink: v.optional(v.nullable(InventoryLinkRequestSchema)),
  },
  v.unknown(),
);
export type ExistingMoleculeUpdate = v.InferOutput<
  typeof ExistingMoleculeUpdateSchema
>;

export const NewMoleculeSchema = v.objectWithRest(
  {
    role: StoichiometryRoleSchema,
    smiles: v.string(),
    name: v.optional(v.nullable(v.string())),
    formula: v.optional(v.string()),
    molecularWeight: v.optional(v.number()),
    coefficient: v.optional(v.number()),
    mass: v.optional(v.nullable(v.number())),
    actualAmount: v.optional(v.nullable(v.number())),
    actualYield: v.optional(v.nullable(v.number())),
    limitingReagent: v.optional(v.boolean()),
    notes: v.optional(v.nullable(v.string())),
    inventoryLink: v.optional(v.nullable(InventoryLinkRequestSchema)),
  },
  v.unknown(),
);
export type NewMolecule = v.InferOutput<typeof NewMoleculeSchema>;

export const StoichiometryRequestSchema = v.object({
  id: v.number(),
  molecules: v.array(v.union([ExistingMoleculeUpdateSchema, NewMoleculeSchema])),
});
export type StoichiometryRequest = v.InferOutput<
  typeof StoichiometryRequestSchema
>;

export const MoleculeInfoSchema = v.objectWithRest(
  {
    molecularWeight: v.number(),
    formula: v.string(),
  },
  v.unknown(),
);
export type MoleculeInfo = v.InferOutput<typeof MoleculeInfoSchema>;

export const StoichiometryMessageErrorResponseSchema = v.object({
  message: v.string(),
});
export type StoichiometryMessageErrorResponse = v.InferOutput<
  typeof StoichiometryMessageErrorResponseSchema
>;

export const DeleteStoichiometryResponseSchema = v.union([
  v.boolean(),
  v.object({
    success: v.boolean(),
  }),
]);
export type DeleteStoichiometryResponse = v.InferOutput<
  typeof DeleteStoichiometryResponseSchema
>;

export const StockDeductionRequestSchema = v.object({
  stoichiometryId: v.number(),
  linkIds: v.pipe(v.array(v.number()), v.minLength(1)),
});
export type StockDeductionRequest = v.InferOutput<
  typeof StockDeductionRequestSchema
>;

export const StockDeductionIndividualResultSchema = v.objectWithRest(
  {
    linkId: v.number(),
    success: v.boolean(),
    errorMessage: v.optional(v.nullable(v.string())),
  },
  v.unknown(),
);
export type StockDeductionIndividualResult = v.InferOutput<
  typeof StockDeductionIndividualResultSchema
>;

export const StockDeductionResultSchema = v.objectWithRest(
  {
    stoichiometryId: v.number(),
    revisionNumber: v.number(),
    results: v.array(StockDeductionIndividualResultSchema),
  },
  v.unknown(),
);
export type StockDeductionResult = v.InferOutput<
  typeof StockDeductionResultSchema
>;

