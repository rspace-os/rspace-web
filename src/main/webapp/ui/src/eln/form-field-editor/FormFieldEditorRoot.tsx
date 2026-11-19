import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { type ReactNode, type Ref, Suspense, useImperativeHandle, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useConfirm } from "@/components/ConfirmProvider";
import ErrorBoundary from "@/components/ErrorBoundary";
import LoadingCircular from "@/components/LoadingCircular";
import { type EditorField, fromServer, toSaveItem } from "./editorModel";
import FormFieldEditor, { type FieldEditorHandle } from "./FormFieldEditor";
import { type FormFieldList, fetchFormFields, saveFormFields } from "./formFieldsApi";

/** What the legacy toolbar uses; see `form.js`. */
export type FormFieldEditorHandle = FieldEditorHandle & {
  /** Asks before the toolbar's Revert discards the draft, whether or not the fields have loaded. */
  confirmAbandon: () => Promise<boolean>;
};

type LoadedEditorProps = {
  formId: number;
  editable: boolean;
  ref: Ref<FieldEditorHandle>;
};

type LoadedFields = { fields: ReadonlyArray<EditorField>; version: string };

function LoadedEditor({ formId, editable, ref }: LoadedEditorProps): ReactNode {
  const queryClient = useQueryClient();
  const queryKey = ["eln-form-fields", formId] as const;
  const toLoaded = ({ fields, version }: FormFieldList): LoadedFields => ({ fields: fields.map(fromServer), version });
  const { data } = useSuspenseQuery({
    queryKey,
    queryFn: async () => toLoaded(await fetchFormFields(formId)),
    staleTime: Number.POSITIVE_INFINITY,
  });
  const save = useMutation({
    mutationFn: async (current: ReadonlyArray<EditorField>) => {
      // each save is made from the version the last one returned, so a save from elsewhere in between is refused
      const version = queryClient.getQueryData<LoadedFields>(queryKey)?.version ?? data.version;
      return toLoaded(await saveFormFields(formId, current.map(toSaveItem), version));
    },
    onSuccess: (saved) => queryClient.setQueryData(queryKey, saved),
  });
  return (
    <FormFieldEditor
      initialFields={data.fields}
      onSave={async (current) => (await save.mutateAsync(current)).fields}
      editable={editable}
      ref={ref}
    />
  );
}

type FormFieldEditorRootProps = {
  formId: number;
  editable: boolean;
  ref?: Ref<FormFieldEditorHandle>;
};

/**
 * Loads the form's fields, then edits them; server state is read and written
 * through React Query. Until the fields load, or if they fail to, there is
 * nothing to save, but Revert still asks before discarding the draft.
 */
export default function FormFieldEditorRoot({ formId, editable, ref }: FormFieldEditorRootProps): ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const confirm = useConfirm();
  const editor = useRef<FieldEditorHandle>(null);

  useImperativeHandle(ref, () => ({
    save: () => editor.current?.save() ?? Promise.resolve(),
    isDirty: () => editor.current?.isDirty() ?? false,
    fieldCount: () => editor.current?.fieldCount() ?? 0,
    markPublished: () => editor.current?.markPublished(),
    setReadOnly: (readOnly) => editor.current?.setReadOnly(readOnly),
    confirmAbandon: () =>
      confirm(
        t("formFieldEditor.confirm.abandon"),
        t("formFieldEditor.confirm.abandonMessage"),
        t("formFieldEditor.confirm.abandonAction"),
        t("common:actions.cancel"),
      ),
  }));

  return (
    <ErrorBoundary message={t("formFieldEditor.loadFailed")}>
      <Suspense fallback={<LoadingCircular />}>
        <LoadedEditor formId={formId} editable={editable} ref={editor} />
      </Suspense>
    </ErrorBoundary>
  );
}
