import type React from "react";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";

const RECORD_TYPE_LABEL_KEYS = {
  "Structured Document": "legacyjs.core.recordInfoPanel.type.structuredDocument",
  Folder: "legacyjs.core.recordInfoPanel.type.folder",
  Notebook: "legacyjs.core.recordInfoPanel.type.notebook",
  "System Folder": "legacyjs.core.recordInfoPanel.type.systemFolder",
  Template: "legacyjs.core.recordInfoPanel.type.template",
  Image: "legacyjs.core.recordInfoPanel.type.image",
  Video: "legacyjs.core.recordInfoPanel.type.video",
  Audio: "legacyjs.core.recordInfoPanel.type.audio",
  Document: "legacyjs.core.recordInfoPanel.type.document",
  Chemistry: "legacyjs.core.recordInfoPanel.type.chemistry",
  PdfDocuments: "legacyjs.core.recordInfoPanel.type.exports",
  Miscellaneous: "legacyjs.core.recordInfoPanel.type.miscellaneous",
  DMPs: "legacyjs.core.recordInfoPanel.type.dmps",
  Snippet: "legacyjs.core.recordInfoPanel.type.snippet",
  Form: "legacyjs.core.recordInfoPanel.type.form",
} as const;

/** Translate a known workspace record category while preserving unknown server values. */
export default function RecordTypeLabel({ type }: { type: string }): React.ReactElement {
  // server.legacyJs is not preloaded by the Inventory root; without a local boundary its first
  // load would suspend the whole app.
  return (
    <Suspense fallback={type}>
      <TranslatedRecordType type={type} />
    </Suspense>
  );
}

function TranslatedRecordType({ type }: { type: string }): React.ReactElement {
  const { t } = useTranslation("server.legacyJs");
  const key = Object.hasOwn(RECORD_TYPE_LABEL_KEYS, type)
    ? RECORD_TYPE_LABEL_KEYS[type as keyof typeof RECORD_TYPE_LABEL_KEYS]
    : undefined;

  return <>{key ? t(key) : type}</>;
}
