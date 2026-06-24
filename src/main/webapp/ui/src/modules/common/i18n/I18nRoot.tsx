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

/** Preloads the optional `namespaces` so they resolve in the root's single Suspense pass. */
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

/**
 * Provides the i18n instance and a Suspense boundary for a React root.
 * Namespaces resolve automatically — each component calls `useTranslation(ns)`
 * and the boundary waits while the chunk loads, so cross-namespace components
 * need nothing declared here. `namespaces` is an OPTIONAL preload hint: pass a
 * root's main namespaces to load them up front in one suspense (avoids
 * mid-render loading flashes); omit it and components load their own on demand.
 *
 * `componentMap` selects the rich-text vocabulary `TransRichText` renders with.
 * Defaults to MUI; a Base UI root passes its own map to override it for the
 * whole subtree.
 */
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
