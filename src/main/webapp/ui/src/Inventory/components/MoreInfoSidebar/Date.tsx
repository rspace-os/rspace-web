import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import { observer } from "mobx-react-lite";
import type React from "react";
import { isoToLocale } from "../../../util/Util";

type DateArgs = {
  date: string;
  label: string;
};

// biome-ignore lint/suspicious/noShadowRestrictedNames: initial biome migration
function Date({ date, label }: DateArgs): React.ReactNode {
  return (
    <FormControl component="fieldset" sx={{ alignItems: "flex-start" }}>
      <FormLabel component="legend">{label}</FormLabel>
      <FormGroup>{isoToLocale(date)}</FormGroup>
    </FormControl>
  );
}

export default observer(Date);
