import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import React from "react";
import { useTranslation } from "react-i18next";
import type { ChemSearchType } from "../../stores/definitions/Search";
import { IsInvalid, IsValid, type ValidationResult } from "../ValidatingSubmitButton";

/*
 * Controls shared by every structure search dialog (Workspace and Inventory),
 * so that both offer the same match types and accept the same input.
 */

type StructureSearchTypeControlArgs = {
  value: ChemSearchType;
  onChange: (value: ChemSearchType) => void;
};

export function StructureSearchTypeControl({ value, onChange }: StructureSearchTypeControlArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const labelId = React.useId();
  return (
    <FormControl>
      <FormLabel id={labelId}>{t("ketcher.structureSearch.searchType")}</FormLabel>
      <RadioGroup
        row
        aria-labelledby={labelId}
        name="structure-search-type"
        value={value}
        onChange={(e) => {
          onChange(e.target.value === "EXACT" ? "EXACT" : "SUBSTRUCTURE");
        }}
      >
        <FormControlLabel
          value="SUBSTRUCTURE"
          control={<Radio />}
          label={t("ketcher.structureSearch.searchTypeSubstructure")}
        />
        <FormControlLabel value="EXACT" control={<Radio />} label={t("ketcher.structureSearch.searchTypeExact")} />
      </RadioGroup>
    </FormControl>
  );
}

type KetcherLike = { getKet: () => Promise<string> };

/**
 * A structure search takes exactly one molecule. Call `validate` with the
 * Ketcher instance whenever the canvas changes, and pass `validationResult` to
 * the Ketcher dialog.
 */
export function useSingleMoleculeValidation(): {
  validationResult: ValidationResult;
  validate: (ketcher: KetcherLike | null | undefined) => void;
} {
  const { t } = useTranslation("common");
  const [validationResult, setValidationResult] = React.useState(IsValid());

  const validate = React.useCallback(
    (ketcher: KetcherLike | null | undefined) => {
      if (!ketcher) {
        setValidationResult(IsValid());
        return;
      }
      void ketcher.getKet().then((ketData) => {
        const molecules = Object.keys(JSON.parse(ketData) as object).filter((key) => key.startsWith("mol"));
        if (molecules.length === 0) {
          setValidationResult(IsInvalid(t("ketcher.structureSearch.validationNoMolecule")));
        } else if (molecules.length > 1) {
          setValidationResult(IsInvalid(t("ketcher.structureSearch.validationMultipleMolecules")));
        } else {
          setValidationResult(IsValid());
        }
      });
    },
    [t],
  );

  return { validationResult, validate };
}
