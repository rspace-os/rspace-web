import Box from "@mui/material/Box";
import visuallyHidden from "@mui/utils/visuallyHidden";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";

type RevealedFieldsAnnouncerProps = {
  /** Names of the fields with a display rule that are shown now, in display order. */
  shownFieldNames: ReadonlyArray<string>;
};

/**
 * A polite live region that tells screen reader users which fields an answer
 * has just revealed. Focus stays on the control they changed, so without this
 * they would not know the form grew. Nothing is announced on first render.
 */
export default function RevealedFieldsAnnouncer({ shownFieldNames }: RevealedFieldsAnnouncerProps): ReactNode {
  const { t, i18n } = useTranslation("common");
  const previous = useRef<ReadonlySet<string> | null>(null);
  const [message, setMessage] = useState("");
  const signature = JSON.stringify(shownFieldNames);

  // the signature stands for shownFieldNames, which may be a new array on every render
  useEffect(() => {
    const before = previous.current;
    previous.current = new Set(shownFieldNames);
    if (before === null) return;
    const revealed = shownFieldNames.filter((name) => !before.has(name));
    if (revealed.length === 0) return;
    setMessage(
      t("fieldDisplay.fieldsShown", {
        count: revealed.length,
        fields: formatList(revealed, i18n.resolvedLanguage ?? i18n.language),
      }),
    );
  }, [signature]);

  return (
    <Box role="status" aria-live="polite" sx={visuallyHidden}>
      {message}
    </Box>
  );
}
