import type { FlatNamespace } from "i18next";
import type React from "react";
import { Suspense } from "react";
import { I18nextProvider, useTranslation } from "react-i18next";
import i18n from "@/modules/common/i18n";
import {
  muiRichTextComponents,
  type RichTextComponents,
  RichTextComponentsContext,
} from "@/modules/common/i18n/TransRichText";

function NamespacePreloader({
  namespaces,
  children,
}: {
  namespaces?: readonly FlatNamespace[];
  children: React.ReactNode;
}): React.ReactNode {
  useTranslation(namespaces ? [...namespaces] : undefined);
  return children;
}

export default function I18nRoot({
  children,
  namespaces,
  componentMap = muiRichTextComponents,
  fallback = null,
}: {
  children: React.ReactNode;
  namespaces?: readonly FlatNamespace[];
  componentMap?: RichTextComponents;
  /**
   * Shown while namespaces load. Defaults to nothing, which is correct for
   * islands hidden until a user action (dialogs, toasts, menus). Callers that
   * mount into visible, in-flow page space should pass something that
   * occupies the same footprint (a `LoaderCircular` for full-page apps, a
   * `Skeleton` or static lookalike for islands) so nothing reflows once the
   * namespaces resolve.
   */
  fallback?: React.ReactNode;
}): React.ReactNode {
  return (
    <I18nextProvider i18n={i18n}>
      <RichTextComponentsContext.Provider value={componentMap}>
        <Suspense fallback={fallback}>
          <NamespacePreloader namespaces={namespaces}>{children}</NamespacePreloader>
        </Suspense>
      </RichTextComponentsContext.Provider>
    </I18nextProvider>
  );
}
