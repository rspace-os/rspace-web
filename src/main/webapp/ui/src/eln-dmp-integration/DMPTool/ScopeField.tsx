import React, { useState } from "react";
import Grid from "@mui/material/Grid";
import RadioField, {
  type RadioOption,
} from "../../components/Inputs/RadioField";
import Typography from "@mui/material/Typography";

export type Scope = "MINE" | "PUBLIC" | "BOTH";

const scopeOptions: Array<RadioOption<"MINE" | "PUBLIC" | "BOTH">> = [
  { value: "MINE", label: "Mine" },
  { value: "PUBLIC", label: "Public" },
  { value: "BOTH", label: "Both" },
];

type ScopeFieldArgs = {
  getDMPs: (scope: Scope) => void;
};

export default function ScopeField({
  getDMPs,
}: ScopeFieldArgs): React.ReactNode {
  const [currentScope, setCurrentScope] = useState("MINE");

  const onScopeSwitch = (newScope: Scope | null) => {
    if (newScope) {
      setCurrentScope(newScope);
      getDMPs(newScope);
    }
  };

  return (
    <Grid container direction="row" spacing={2}>
      <Grid item>
        <RadioField
          value={currentScope}
          name="DMP Scope Options"
          onChange={(e) => onScopeSwitch(e.target.value as Scope | null)}
          options={scopeOptions}
          disabled={false}
          labelPlacement="top"
          row
          smallText={true}
        />
      </Grid>

      <Grid item>
        <Typography variant="body2">
          Select a scope to get the latest plans.
          <br /> Select a plan and click &quot;Import&quot; to add it to the
          Gallery.
        </Typography>
      </Grid>
    </Grid>
  );
}
