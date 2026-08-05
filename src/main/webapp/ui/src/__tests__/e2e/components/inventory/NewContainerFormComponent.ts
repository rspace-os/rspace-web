import { expect, type Page } from "@playwright/test";
import { addTagField, setDescriptionField, uploadPreviewImageField } from "./DetailsSectionFields";
import { clickAndWaitDetached, openDialog, uploadViaFileChooser } from "./DialogHelpers";
import { EditLocationsDialog } from "./EditLocationsDialog";
import { delegateToForm, NewItemFormShell } from "./NewItemFormShell";

export type ContainerType = "List" | "Grid" | "Visual";

export type GridAxisLabelType = "ABC" | "CBA" | "123" | "321";

export function newContainerFormComponent(page: Page) {
  const form = new NewItemFormShell(page, "New Container");

  const gridLabelsRegion = () => form.root.getByRole("region", { name: "Configure Grid Labels" });

  return {
    root: form.root,
    ...delegateToForm(form),

    async setType(type: ContainerType): Promise<void> {
      await form.root.getByRole("radio", { name: type }).check();
    },

    async setGridDimensions(rows: number, columns: number): Promise<void> {
      await form.root.getByRole("spinbutton", { name: "rows" }).fill(String(rows));
      await form.root.getByRole("spinbutton", { name: "columns" }).fill(String(columns));
    },

    async chooseCommonGridDimensions(presetName: string): Promise<void> {
      await form.root.getByRole("combobox").first().click();
      await page.getByRole("option", { name: presetName, exact: true }).click();
    },

    async expandConfigureGridLabels(): Promise<void> {
      await gridLabelsRegion().getByRole("button", { name: "Expand grid dimension controls group" }).click();
      await gridLabelsRegion().getByRole("group", { name: "Row Labels" }).waitFor({ state: "visible" });
    },

    async setRowLabelType(type: GridAxisLabelType): Promise<void> {
      const radio = gridLabelsRegion().getByRole("group", { name: "Row Labels" }).getByRole("radio", { name: type });
      await radio.click();
      await expect(radio).toBeChecked();
    },

    async setColumnLabelType(type: GridAxisLabelType): Promise<void> {
      const radio = gridLabelsRegion().getByRole("group", { name: "Column Labels" }).getByRole("radio", { name: type });
      await radio.click();
      await expect(radio).toBeChecked();
    },

    async insertDescriptionLink(url: string): Promise<void> {
      const description = form.section("Details").getByRole("group", { name: "Description" });
      await description.getByRole("button", { name: "Insert/edit link" }).click();
      const dialog = page.getByRole("dialog", { name: "Insert/Edit Link" });
      await dialog.getByRole("combobox", { name: "URL" }).fill(url);
      await clickAndWaitDetached(dialog.getByRole("button", { name: "Save", exact: true }), dialog);
    },

    async setDescription(text: string): Promise<void> {
      await setDescriptionField(form.section("Details"), text);
    },

    async addTag(name: string): Promise<void> {
      await addTagField(page, form.section("Details"), name);
    },

    async uploadPreviewImage(path: string): Promise<void> {
      await uploadPreviewImageField(page, form.root, path);
    },

    async uploadLocationsImage(path: string): Promise<void> {
      const locationsGroup = form.root.getByRole("group", { name: "Locations Image" });
      await uploadViaFileChooser(page, locationsGroup.getByRole("button", { name: "Add Image" }), path);
      await locationsGroup.getByRole("button", { name: "Replace Image" }).waitFor({ state: "visible" });
      const setPreviewTooButton = page.getByRole("button", { name: "YES" });
      if (await setPreviewTooButton.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await setPreviewTooButton.click();
      }
    },

    async openEditLocations(): Promise<EditLocationsDialog> {
      const editLocationsButton = form.root
        .getByRole("group", { name: "Locations Image" })
        .getByRole("button", { name: "Edit Locations" });
      await expect(editLocationsButton).toBeEnabled();
      return openDialog(() => editLocationsButton.click(), new EditLocationsDialog(page));
    },
  };
}

export type NewContainerFormComponent = ReturnType<typeof newContainerFormComponent>;
