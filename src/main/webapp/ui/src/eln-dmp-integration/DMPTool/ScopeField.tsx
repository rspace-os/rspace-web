import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import RadioField, { type RadioOption } from "../../components/Inputs/RadioField";

export type Scope = "MINE" | "PUBLIC" | "BOTH";

type ScopeFieldArgs = {
  getDMPs: (scope: Scope) => void;
};

export default function ScopeField({ getDMPs }: ScopeFieldArgs): React.ReactNode {
  const { t } = useTranslation("apps");
  const [currentScope, setCurrentScope] = useState("MINE");
  const scopeOptions: Array<RadioOption<"MINE" | "PUBLIC" | "BOTH">> = [
    { value: "MINE", label: t("dmpIntegrations.scope.mine") },
    { value: "PUBLIC", label: t("dmpIntegrations.scope.public") },
    { value: "BOTH", label: t("dmpIntegrations.scope.both") },
  ];

  const onScopeSwitch = (newScope: Scope | null) => {
    if (newScope) {
      setCurrentScope(newScope);
      getDMPs(newScope);
    }
  };

  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: "flex-start" }}>
      <RadioField
        value={currentScope}
        name={t("dmpIntegrations.scope.name")}
        onChange={(e) => onScopeSwitch(e.target.value as Scope | null)}
        options={scopeOptions}
        disabled={false}
        labelPlacement="top"
        row
        smallText={true}
      />

      <Typography variant="body2">
        {t("dmpIntegrations.scope.instructions.selectScope")}
        <br />
        {t("dmpIntegrations.scope.instructions.selectPlan")}
      </Typography>
    </Stack>
  );
}
