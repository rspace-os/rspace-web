import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { type ReactNode, type Ref, Suspense, useImperativeHandle, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useConfirm } from "@/components/ConfirmProvider";
import ErrorBoundary from "@/components/ErrorBoundary";
import LoadingCircular from "@/components/LoadingCircular";
import { adoptSavedIds, type EditorField, fromServer, sameFields, toSaveItem } from "./editorModel";
import FormFieldEditor, { type FieldEditorHandle, type ServerFields } from "./FormFieldEditor";
import {
  type FormFieldList,
  FormFieldsConflictError,
  FormFieldsSaveError,
  fetchFormFields,
  saveFormFields,
} from "./formFieldsApi";

/**
 * The fields have not loaded, or could not be, so they cannot be saved or
 * checked; the toolbar must not update or publish the form. `form.js` tells
 * the author by this error's name.
 */
export class FormFieldEditorUnavailableError extends Error {
  constructor() {
    super("The form's fields are not loaded, so they cannot be saved or checked.");
    this.name = "FormFieldEditorUnavailableError";
  }
}

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

type LoadedFields = ServerFields;

function LoadedEditor({ formId, editable, ref }: LoadedEditorProps): ReactNode {
  const queryClient = useQueryClient();
  const queryKey = ["eln-form-fields", formId] as const;
  const toLoaded = ({ fields, version }: FormFieldList): LoadedFields => ({ fields: fields.map(fromServer), version });
  const { data } = useSuspenseQuery({
    queryKey,
    queryFn: async () => toLoaded(await fetchFormFields(formId)),
    staleTime: Number.POSITIVE_INFINITY,
  });
  // each save is made from the version the last one returned, so a save from elsewhere in between is refused
  const currentVersion = () => queryClient.getQueryData<LoadedFields>(queryKey)?.version ?? data.version;
  const fetchLoaded = async () => toLoaded(await fetchFormFields(formId));
  // The last save sent whose outcome is unknown: it failed without the server saying why, so it may
  // have been saved with its response lost.
  const unknownOutcome = useRef<ReadonlyArray<EditorField> | null>(null);
  const send = async (fields: ReadonlyArray<EditorField>) => {
    try {
      const saved = toLoaded(await saveFormFields(formId, fields.map(toSaveItem), currentVersion()));
      unknownOutcome.current = null;
      return saved;
    } catch (error) {
      // the server answered, so nothing was saved
      if (error instanceof FormFieldsSaveError) {
        if (!(error instanceof FormFieldsConflictError)) unknownOutcome.current = null;
      } else {
        unknownOutcome.current = fields;
      }
      throw error;
    }
  };
  const save = useMutation({
    mutationFn: async (current: ReadonlyArray<EditorField>) => {
      try {
        return await send(current);
      } catch (error) {
        if (!(error instanceof FormFieldsConflictError)) throw error;
        const server = await fetchLoaded().catch(() => null);
        if (!server) throw error;
        // a save whose response was lost may be what moved the version on
        if (sameFields(server.fields, current)) {
          unknownOutcome.current = null;
          return server;
        }
        const lost = unknownOutcome.current;
        if (lost === null || !sameFields(server.fields, lost)) throw error;
        // this save follows that one, from its version and with the ids it gave new fields
        unknownOutcome.current = null;
        queryClient.setQueryData(queryKey, server);
        return send(adoptSavedIds(current, server.fields, lost));
      }
    },
    onSuccess: (saved) => queryClient.setQueryData(queryKey, saved),
  });
  const check = async (current: ReadonlyArray<EditorField>) => {
    const server = await fetchLoaded();
    if (server.version === currentVersion()) return { version: server.version, fields: null };
    if (!sameFields(server.fields, current)) throw new FormFieldsConflictError([]);
    queryClient.setQueryData(queryKey, server);
    return server;
  };
  return (
    <FormFieldEditor
      initialFields={data.fields}
      onSave={(current) => save.mutateAsync(current)}
      onCheck={check}
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
 * through React Query. Until the fields load, or if they fail to, they cannot
 * be checked, so `save()` rejects and the toolbar neither updates nor
 * publishes; Revert still asks before discarding the draft.
 */
export default function FormFieldEditorRoot({ formId, editable, ref }: FormFieldEditorRootProps): ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const confirm = useConfirm();
  const editor = useRef<FieldEditorHandle>(null);

  useImperativeHandle(ref, () => ({
    save: () => editor.current?.save() ?? Promise.reject(new FormFieldEditorUnavailableError()),
    isDirty: () => editor.current?.isDirty() ?? false,
    fieldCount: () => editor.current?.fieldCount() ?? 0,
    markPublished: () => editor.current?.markPublished(),
    setReadOnly: (readOnly) => editor.current?.setReadOnly(readOnly),
    reportConflict: () => editor.current?.reportConflict(),
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
