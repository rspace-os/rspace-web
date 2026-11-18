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
 * has just revealed. Focus stays on the control they changed, so without this
 * they would not know the form grew. Only fields that were hidden before are
 * announced, so nothing is said on first render or when a template brings in
 * new fields; hosts remount it (with a key) when they show another record. The
 * message is cleared when a field is hidden, so revealing it again is
 * announced again.
 */
export default function RevealedFieldsAnnouncer({
  shownFieldNames,
  hiddenFieldNames,
}: RevealedFieldsAnnouncerProps): ReactNode {
  const { t, i18n } = useTranslation("common");
  const previouslyHidden = useRef<ReadonlySet<string>>(new Set(hiddenFieldNames));
  const [message, setMessage] = useState("");
  const signature = JSON.stringify([shownFieldNames, hiddenFieldNames]);

  // the signature stands for both name lists, which are new arrays on every render
  useEffect(() => {
    const before = previouslyHidden.current;
    previouslyHidden.current = new Set(hiddenFieldNames);
    const revealed = shownFieldNames.filter((name) => before.has(name));
    if (revealed.length > 0) {
      setMessage(
        t("fieldDisplay.fieldsShown", {
          count: revealed.length,
          fields: formatList(revealed, i18n.resolvedLanguage ?? i18n.language),
        }),
      );
    } else if (hiddenFieldNames.some((name) => !before.has(name))) {
      setMessage("");
    }
  }, [signature]);

  return (
    <Box role="status" aria-live="polite" sx={visuallyHidden}>
      {message}
    </Box>
  );
}
