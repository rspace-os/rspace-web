import type React from "react";
import { DEFAULT_EXPORT_FORM_VALUES, type ExportForm, type ExportFormValues, useExportForm } from "../exportForm";

/**
 * Creates the export form for a pane rendered outside of the export dialog.
 */
function WithExportForm({
  values,
  formRef,
  children,
}: {
  // replaces the default values of these slices of the form
  values?: Partial<ExportFormValues>;
  // receives the form, so that a test can read the values a pane has set
  formRef?: { current: ExportForm | null };
  children: (form: ExportForm) => React.ReactNode;
}): React.ReactNode {
  const form = useExportForm({ ...DEFAULT_EXPORT_FORM_VALUES, ...values });
  if (formRef) formRef.current = form;
  return children(form);
}

export default WithExportForm;
