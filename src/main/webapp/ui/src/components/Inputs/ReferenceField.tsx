import type React from "react";
import { useTranslation } from "react-i18next";
import type { emptyObject } from "../../util/types";

export type ReferenceFieldArgs = emptyObject;

export default function ReferenceField(_props: ReferenceFieldArgs): React.ReactNode {
  const { t } = useTranslation("common");
  return <em>{t("inputs.referenceField.notYetSupported")}</em>;
}
