import Box from "@mui/material/Box";
import visuallyHidden from "@mui/utils/visuallyHidden";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";

type RevealedFieldsAnnouncerProps = {
  /** Names of the fields with a display rule that are shown now, in display order. */
  shownFieldNames: ReadonlyArray<string>;
  /** Names of the fields with a display rule that are hidden now. */
  hiddenFieldNames: ReadonlyArray<string>;
};

/**
 * A polite live region that tells screen reader users which fields an answer
 * has just shown or hidden. Focus stays on the control they changed, so
 * without this they would not know the form changed. Only fields that were on
 * the other side before are announced, so nothing is said on first render or
 * when a template brings in new fields; hosts remount it (with a key) when
 * they show another record.
 */
export default function RevealedFieldsAnnouncer({
  shownFieldNames,
  hiddenFieldNames,
}: RevealedFieldsAnnouncerProps): ReactNode {
  const { t, i18n } = useTranslation("common");
  const previous = useRef({ shown: new Set(shownFieldNames), hidden: new Set(hiddenFieldNames) });
  const [message, setMessage] = useState("");
  const signature = JSON.stringify([shownFieldNames, hiddenFieldNames]);

  // the signature stands for both name lists, which are new arrays on every render
  useEffect(() => {
    const before = previous.current;
    previous.current = { shown: new Set(shownFieldNames), hidden: new Set(hiddenFieldNames) };
    const revealed = shownFieldNames.filter((name) => before.hidden.has(name));
    const concealed = hiddenFieldNames.filter((name) => before.shown.has(name));
    if (revealed.length === 0 && concealed.length === 0) return;
    const language = i18n.resolvedLanguage ?? i18n.language;
    const parts: Array<string> = [];
    if (revealed.length > 0)
      parts.push(t("fieldDisplay.fieldsShown", { count: revealed.length, fields: formatList(revealed, language) }));
    if (concealed.length > 0)
      parts.push(t("fieldDisplay.fieldsHidden", { count: concealed.length, fields: formatList(concealed, language) }));
    setMessage(parts.join(" "));
  }, [signature]);

  return (
    <Box role="status" aria-live="polite" sx={visuallyHidden}>
      {message}
    </Box>
  );
}
