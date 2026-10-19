import { createContext } from "react";
import type { IntegrationStates } from "./useIntegrationsEndpoint";

export type FrozenModes = Readonly<Record<keyof IntegrationStates, IntegrationStates[keyof IntegrationStates]["mode"]>>;

/**
 * Enabling or disabling an integration moves its card to another section of
 * the Apps page, which remounts it. So that the open dialog, and any input
 * not yet saved, survive the toggle, the cards stay in the sections they were
 * in while a dialog is open, and move once it has closed.
 */
const CardPlacementContext = createContext<{
  frozenModes: FrozenModes | null;
  dialogOpened: () => void;
  dialogClosed: () => void;
}>({
  frozenModes: null,
  dialogOpened: () => {},
  dialogClosed: () => {},
});

export default CardPlacementContext;
