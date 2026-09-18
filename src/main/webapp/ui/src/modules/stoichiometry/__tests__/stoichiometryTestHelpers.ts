import type { TinyMceEditor } from "@/__tests__/e2e/components/document/TinyMceEditor";
import type { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/document/DocumentEditorPage";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { fixturePath } from "@/__tests__/e2e/testData";
import type { StoichiometryDialogComponent } from "./pageObjects/StoichiometryDialogComponent";

export const REACTION_CDXML = fixturePath(import.meta.url, "fixtures/basic_reaction.cdxml");
export const REACTION_FILE_NAME = "basic_reaction.cdxml";

/** Creates a document, inserts the shared reaction fixture, and calculates its stoichiometry table. */
export async function insertReactionAndCalculate(pageWorkspace: WorkspacePage): Promise<{
  docEditor: DocumentEditorPage;
  field: TinyMceEditor;
  stoichiometryDialog: StoichiometryDialogComponent;
}> {
  await pageWorkspace.open();
  const docEditor = await pageWorkspace.createBasicDocument();
  const picker = await docEditor.openGalleryPicker();
  await picker.goToSection("Chemistry");
  await picker.uploadFile(REACTION_CDXML, REACTION_FILE_NAME);
  await picker.selectItem(REACTION_FILE_NAME);
  await picker.add();

  const field = await docEditor.getField("New List of Materials");
  const stoichiometryDialog = await field.chemistry.openStoichiometryDialog();
  await stoichiometryDialog.calculate();
  return { docEditor, field, stoichiometryDialog };
}
