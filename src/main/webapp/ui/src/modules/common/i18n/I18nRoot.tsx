import type { FlatNamespace } from "i18next";
import type React from "react";
import { Suspense } from "react";
import { I18nextProvider, useTranslation } from "react-i18next";
import LoaderCircular from "@/components/LoadingCircular";
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
}: {
  children: React.ReactNode;
  namespaces?: readonly FlatNamespace[];
  componentMap?: RichTextComponents;
}): React.ReactNode {
  return (
    <I18nextProvider i18n={i18n}>
      <RichTextComponentsContext.Provider value={componentMap}>
        <Suspense fallback={<LoaderCircular />}>
          <NamespacePreloader namespaces={namespaces}>{children}</NamespacePreloader>
        </Suspense>
      </RichTextComponentsContext.Provider>
    </I18nextProvider>
  );
}
