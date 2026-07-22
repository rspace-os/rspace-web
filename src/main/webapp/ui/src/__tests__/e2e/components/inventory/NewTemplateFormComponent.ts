import type { Page } from "@playwright/test";
import { delegateToForm, NewItemFormShell } from "./NewItemFormShell";
import { TemplateFieldsEditor, type TemplateFieldType } from "./TemplateFieldsEditor";

export function newTemplateFormComponent(page: Page, title: "New Template" | "New Instrument Template") {
  const form = new NewItemFormShell(page, title);
  const fields = () => new TemplateFieldsEditor(page, form.section("Custom Fields"));

  return {
    root: form.root,
    ...delegateToForm(form),

    async addCustomField(type: TemplateFieldType, name: string, option?: string): Promise<void> {
      await fields().addCustomField(type, name, option);
    },

    fieldRegion(name: string) {
      return fields().fieldRegion(name);
    },

    async setFieldMandatory(name: string): Promise<void> {
      await fields().setFieldMandatory(name);
    },

    async addAllowedRelationType(name: string, relationType: string): Promise<void> {
      await fields().addAllowedRelationType(name, relationType);
    },
  };
}

export type NewTemplateFormComponent = ReturnType<typeof newTemplateFormComponent>;
