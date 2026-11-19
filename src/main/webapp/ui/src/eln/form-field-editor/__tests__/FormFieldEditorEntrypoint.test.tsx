import { expect, test } from "vitest";
import { FormFieldEditorUnavailableError } from "../FormFieldEditorRoot";

test("the toolbar's bridge refuses to save before the editor has mounted, as nothing can be checked", async () => {
  // no #formFieldEditor container, so nothing mounts
  await import("../FormFieldEditorEntrypoint");

  await expect(window.formFieldEditor?.save()).rejects.toThrow(FormFieldEditorUnavailableError);
});
