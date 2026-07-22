import type { Page } from "@playwright/test";
import { addTagField, setDescriptionField, uploadPreviewImageField } from "./DetailsSectionFields";
import { dismissMobileDatePickerIfPresent } from "./DialogHelpers";
import { delegateToForm, NewItemFormShell } from "./NewItemFormShell";
import { SearchableResultsTable } from "./SearchableResultsTable";

export type SampleSource = "Lab Created" | "Vendor Supplied" | "Other";

export function newSampleFormComponent(page: Page) {
  const form = new NewItemFormShell(page, "New Sample");
  const withSubsamplesRadio = form.root.getByRole("radio", { name: "Sample with subsamples" });
  const quantityInput = form.root.getByRole("spinbutton", { name: "Quantity" });

  return {
    root: form.root,
    saveButton: form.saveButton,
    ...delegateToForm(form),

    async selectTemplate(name: string): Promise<void> {
      await new SearchableResultsTable(form.root).select(name);
    },

    async setWithSubsamples(quantity?: number): Promise<void> {
      await withSubsamplesRadio.check();
      if (quantity !== undefined) {
        await quantityInput.fill(String(quantity));
      }
    },

    async uploadImage(path: string): Promise<void> {
      await uploadPreviewImageField(page, form.root, path);
    },

    async setSource(source: SampleSource): Promise<void> {
      await form.section("Details").getByRole("radio", { name: source }).click();
    },

    async setStorageTemperatureMin(value: string): Promise<void> {
      await form
        .section("Details")
        .getByRole("group", { name: "Storage Temperature" })
        .getByRole("spinbutton")
        .first()
        .fill(value);
    },

    async setDescription(text: string): Promise<void> {
      await setDescriptionField(form.section("Details"), text);
    },

    async addTag(name: string): Promise<void> {
      await addTagField(page, form.section("Details"), name);
    },

    async setExpiryDateToday(day: string): Promise<void> {
      const dateGroup = form.section("Details").getByRole("group", { name: "Expiry Date" });
      await dateGroup.getByRole("button", { name: "Choose date" }).click();
      const datePicker = page.getByRole("dialog");
      await datePicker.getByRole("gridcell", { name: day, exact: true }).click();
      await dismissMobileDatePickerIfPresent(datePicker);
    },

    async expiryDateText(): Promise<string> {
      const dateGroup = form.section("Details").getByRole("group", { name: "Expiry Date" });
      const year = await dateGroup.getByRole("spinbutton", { name: "Year" }).innerText();
      const month = await dateGroup.getByRole("spinbutton", { name: "Month" }).innerText();
      const day = await dateGroup.getByRole("spinbutton", { name: "Day" }).innerText();
      return `${year}-${month}-${day}`;
    },
  };
}

export type NewSampleFormComponent = ReturnType<typeof newSampleFormComponent>;
