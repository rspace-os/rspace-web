import type { Page } from "@playwright/test";
import { delegateToForm, NewItemFormShell } from "./NewItemFormShell";
import { SearchableResultsTable } from "./SearchableResultsTable";

export function newInstrumentFormComponent(page: Page) {
  const form = new NewItemFormShell(page, "New Instrument");
  return {
    root: form.root,
    ...delegateToForm(form),

    async selectTemplate(name: string): Promise<void> {
      await new SearchableResultsTable(form.root).select(name);
    },
  };
}

export type NewInstrumentFormComponent = ReturnType<typeof newInstrumentFormComponent>;
