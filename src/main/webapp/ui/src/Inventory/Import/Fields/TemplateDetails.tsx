import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { observer } from "mobx-react-lite";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React, { type ReactNode } from "react";
import useStores from "../../../stores/use-stores";
import * as Parsers from "../../../util/parsers";
import TemplatePicker from "../../components/Picker/TemplatePicker";
import SummaryInfo from "../../Template/SummaryInfo";
import TemplateName from "./TemplateName";

function TemplateDetails(): ReactNode {
  const { importStore } = useStores();

  return (
    <RadioGroup
      name="newOrExisting"
      value={importStore.importData?.createNewTemplate.toString()}
      onChange={(event, value) => {
        if (event.target.name === "newOrExisting") {
          importStore.importData?.setCreateNewTemplate(Parsers.parseBoolean(value as "true" | "false").elseThrow());
          if (Parsers.parseBoolean(value as "true" | "false").elseThrow()) {
            importStore.importData?.setTemplate(null);
            importStore.importData?.setDefaultUnitId();
          }
        }
      }}
    >
      <FormControlLabel value="true" control={<Radio color="primary" />} label="Create new template." />
      <Box sx={{ ml: 4, mb: 4, mt: 1 }}>
        <FormControl component="fieldset" fullWidth>
          <FormGroup sx={{ maxWidth: 660 }}>
            <TemplateName disabled={importStore.importData?.createNewTemplate === false} />
          </FormGroup>
        </FormControl>
      </Box>
      <FormControlLabel value="false" control={<Radio color="primary" />} label="Choose existing template." />
      <Box sx={{ ml: 4 }}>
        <SummaryInfo template={importStore.importData?.template ?? null} />
        <Box sx={{ mb: 1 }}>
          <Divider />
        </Box>
        <TemplatePicker
          setTemplate={(t) => {
            importStore.importData?.setTemplate(t);
            importStore.importData?.setCreateNewTemplate(false);
            importStore.importData?.setDefaultUnitId(t.defaultUnitId);
          }}
        />
        {!importStore.importData?.createNewTemplate && !importStore.importData?.template ? (
          <Alert severity="info">{"Select a template from which these imported samples will be created."}</Alert>
        ) : null}
      </Box>
    </RadioGroup>
  );
}

export default observer(TemplateDetails);
