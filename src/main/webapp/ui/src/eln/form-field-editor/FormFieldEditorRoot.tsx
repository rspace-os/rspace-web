import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode, Ref } from "react";
import { useTranslation } from "react-i18next";
import LoadingCircular from "@/components/LoadingCircular";
import { type EditorField, fromServer, toSaveItem } from "./editorModel";
import FormFieldEditor, { type FormFieldEditorHandle } from "./FormFieldEditor";
import { fetchFormFields, saveFormFields } from "./formFieldsApi";

type FormFieldEditorRootProps = {
  formId: number;
  editable: boolean;
  ref?: Ref<FormFieldEditorHandle>;
};

/** Loads the form's fields, then edits them; the server state is read and written through React Query. */
export default function FormFieldEditorRoot({ formId, editable, ref }: FormFieldEditorRootProps): ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const queryClient = useQueryClient();
  const queryKey = ["eln-form-fields", formId] as const;
  const fields = useQuery({
    queryKey,
    queryFn: async () => (await fetchFormFields(formId)).map(fromServer),
    staleTime: Number.POSITIVE_INFINITY,
    refetchOnWindowFocus: false,
  });
  const save = useMutation({
    mutationFn: async (current: ReadonlyArray<EditorField>) =>
      (await saveFormFields(formId, current.map(toSaveItem))).map(fromServer),
    onSuccess: (saved) => queryClient.setQueryData(queryKey, saved),
  });

  if (fields.isPending) return <LoadingCircular />;
  if (fields.isError)
    return (
      <Alert
        severity="error"
        action={<Button onClick={() => void fields.refetch()}>{t("common:actions.retry")}</Button>}
      >
        {t("formFieldEditor.loadFailed")}
      </Alert>
    );
  return (
    <FormFieldEditor
      initialFields={fields.data}
      onSave={(current) => save.mutateAsync(current)}
      editable={editable}
      ref={ref}
    />
  );
}
