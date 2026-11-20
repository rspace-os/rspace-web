import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React, { createRef } from "react";
import { createRoot } from "react-dom/client";
import ConfirmProvider from "@/components/ConfirmProvider";
import ErrorBoundary from "@/components/ErrorBoundary";
import LoadingCircular from "@/components/LoadingCircular";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import materialTheme from "@/theme";
import FormFieldEditorRoot, {
  type FormFieldEditorHandle,
  FormFieldEditorUnavailableError,
} from "./FormFieldEditorRoot";

declare global {
  interface Window {
    /** The form field editor on the form editor page; see `form.js` for the toolbar that drives it. */
    formFieldEditor?: FormFieldEditorHandle;
  }
}

const queryClient = new QueryClient();
const handle = createRef<FormFieldEditorHandle>();

// Until React has mounted the fields cannot be checked, so the toolbar's actions are refused, and
// nothing can ask before Revert, so the draft is kept.
window.formFieldEditor = {
  save: () => handle.current?.save() ?? Promise.reject(new FormFieldEditorUnavailableError()),
  isDirty: () => handle.current?.isDirty() ?? false,
  fieldCount: () => handle.current?.fieldCount() ?? 0,
  confirmAbandon: () => handle.current?.confirmAbandon() ?? Promise.resolve(false),
  markPublished: () => handle.current?.markPublished(),
  setReadOnly: (readOnly) => handle.current?.setReadOnly(readOnly),
  reportConflict: () => handle.current?.reportConflict(),
};

const container = document.getElementById("formFieldEditor");
if (container) {
  createRoot(container).render(
    <React.StrictMode>
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <CssBaseline />
          <QueryClientProvider client={queryClient}>
            <ErrorBoundary>
              <I18nRoot namespaces={["workspace", "common"]} fallback={<LoadingCircular />}>
                <ConfirmProvider>
                  <FormFieldEditorRoot
                    formId={Number(container.dataset.formId)}
                    editable={container.dataset.editable === "true"}
                    ref={handle}
                  />
                </ConfirmProvider>
              </I18nRoot>
            </ErrorBoundary>
          </QueryClientProvider>
        </ThemeProvider>
      </StyledEngineProvider>
    </React.StrictMode>,
  );
}
