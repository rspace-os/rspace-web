import React from "react";

export type CurrentStoichiometry = {
  id: number;
  revision: number;
};

export type SetCurrentStoichiometry = React.Dispatch<
  React.SetStateAction<CurrentStoichiometry | null>
>;

export type RegisterCloseHandler = (
  handler: (() => Promise<void>) | null,
) => void;

export const STOICHIOMETRY_DIALOG_ACTION_BUTTON_SX = {
  minHeight: 36,
  height: 36,
} as const;
