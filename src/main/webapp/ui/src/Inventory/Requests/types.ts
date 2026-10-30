/**
 * How the owner chooses to ready a requested sample for the requester: "wizard" prepares a new
 * subsample via the Operations Wizard, "transfer" hands the whole sample (or subsample) over
 * directly. See ChooseMethodDialog.tsx and RequestDetailPanel.tsx's proceedWithPreparationMethod.
 */
export type PreparationMethod = "wizard" | "transfer";
