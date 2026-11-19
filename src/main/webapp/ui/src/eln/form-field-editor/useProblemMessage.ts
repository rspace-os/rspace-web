import { useTranslation } from "react-i18next";
import { type FieldProblem, NAME_MAX_LENGTH } from "./editorModel";

/** The message for a client check that failed. */
export function useProblemMessage(): (problem: FieldProblem | undefined) => string | undefined {
  const { t } = useTranslation("workspace");
  return (problem) => {
    switch (problem) {
      case undefined:
        return undefined;
      case "nameRequired":
        return t("formFieldEditor.problems.nameRequired");
      case "nameTooLong":
        return t("formFieldEditor.problems.nameTooLong", {
          max: NAME_MAX_LENGTH,
        });
      case "optionsRequired":
        return t("formFieldEditor.problems.optionsRequired");
      case "optionsInvalid":
        return t("formFieldEditor.problems.optionsInvalid");
      case "optionCharacters":
        return t("formFieldEditor.problems.optionCharacters");
      case "optionsTooLong":
        return t("formFieldEditor.problems.optionsTooLong");
      case "notANumber":
        return t("formFieldEditor.problems.notANumber");
      case "decimalPlaces":
        return t("formFieldEditor.problems.decimalPlaces");
      case "minAboveMax":
        return t("formFieldEditor.problems.minAboveMax");
      case "belowMin":
        return t("formFieldEditor.problems.belowMin");
      case "aboveMax":
        return t("formFieldEditor.problems.aboveMax");
    }
  };
}
