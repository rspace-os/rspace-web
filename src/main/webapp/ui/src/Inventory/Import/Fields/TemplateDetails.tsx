import useStores from "../../../stores/use-stores";
import * as Parsers from "../../../util/parsers";
import SummaryInfo from "../../Template/SummaryInfo";
import HelpTextAlert from "../../../components/HelpTextAlert";
import TemplateName from "./TemplateName";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { observer } from "mobx-react-lite";
import React, { type ReactNode } from "react";
import TemplatePicker from "../../components/Picker/TemplatePicker";

function TemplateDetails(): ReactNode {
  const { importStore } = useStores();

  return (
    <RadioGroup
      name="newOrExisting"
      value={importStore.importData?.createNewTemplate.toString()}
      onChange={(event, value) => {
        if (event.target.name === "newOrExisting") {
          importStore.importData?.setCreateNewTemplate(
            Parsers.parseBoolean(value as "true" | "false").elseThrow()
          );
          if (Parsers.parseBoolean(value as "true" | "false").elseThrow()) {
            importStore.importData?.setTemplate(null);
            importStore.importData?.setDefaultUnitId();
          }
        }
      }}
    >
      <FormControlLabel
        value="true"
        control={<Radio color="primary" />}
        label="Create new template."
      />
      <Box ml={4} mb={4} mt={1}>
        <FormControl component="fieldset" fullWidth>
          <FormGroup style={{ maxWidth: 660 }}>
            <TemplateName
              disabled={importStore.importData?.createNewTemplate === false}
            />
          </FormGroup>
        </FormControl>
      </Box>
      <FormControlLabel
        value="false"
        control={<Radio color="primary" />}
        label="Choose existing template."
      />
      <Box ml={4}>
        <SummaryInfo template={importStore.importData?.template ?? null} />
        <Box mb={1}>
          <Divider />
        </Box>
        <TemplatePicker
          setTemplate={(t) => {
            importStore.importData?.setTemplate(t);
            importStore.importData?.setCreateNewTemplate(false);
            importStore.importData?.setDefaultUnitId(t.defaultUnitId);
          }}
        />
        <HelpTextAlert
          text="Select a template from which these imported samples will be created."
          condition={
            !importStore.importData?.createNewTemplate &&
            !importStore.importData?.template
          }
          severity="info"
        />
      </Box>
    </RadioGroup>
  );
}

export default observer(TemplateDetails);
