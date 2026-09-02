import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { observer } from "mobx-react-lite";
import { type ReactNode, useCallback } from "react";
import { useTranslation } from "react-i18next";
import type InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import useStores from "../../../stores/use-stores";
import InstrumentTemplatePicker from "../../components/Picker/InstrumentTemplatePicker";
import InstrumentTemplateSummaryInfo from "../../InstrumentTemplate/SummaryInfo";
import TemplateName from "./TemplateName";

function InstrumentTemplateDetails(): ReactNode {
  const { t } = useTranslation("inventory");
  const { importStore } = useStores();
  const importData = importStore.importData;

  const createNewTemplate = importData?.instrumentCreateNewTemplate ?? true;
  const templateName = importData?.instrumentTemplateName ?? "";
  const selectedTemplate = importData?.instrumentTemplate ?? null;

  const handleSetTemplate = useCallback(
    (tmpl: InstrumentTemplateModel) => {
      importData?.setInstrumentTemplate(tmpl);
      importData?.setInstrumentCreateNewTemplate(false);
    },
    [importData],
  );

  return (
    <RadioGroup
      name="newOrExisting"
      value={createNewTemplate.toString()}
      onChange={(event, value) => {
        if (event.target.name === "newOrExisting") {
          const isNew = value === "true";
          importData?.setInstrumentCreateNewTemplate(isNew);
          if (isNew) importData?.setInstrumentTemplate(null);
        }
      }}
    >
      <FormControlLabel
        value="true"
        control={<Radio color="primary" />}
        label={t("import.templateDetails.createNewTemplate")}
      />
      <Box sx={{ ml: 4, mb: 4, mt: 1 }}>
        <FormControl component="fieldset" fullWidth>
          <FormGroup sx={{ maxWidth: 660 }}>
            <TemplateName
              disabled={!createNewTemplate}
              value={templateName}
              onChange={(v) => importData?.setInstrumentTemplateName(v)}
              error={createNewTemplate && !(importData?.validInstrumentTemplateName ?? true)}
            />
          </FormGroup>
        </FormControl>
      </Box>
      <FormControlLabel
        value="false"
        control={<Radio color="primary" />}
        label={t("import.templateDetails.chooseExistingTemplate")}
      />
      <Box sx={{ ml: 4 }}>
        <InstrumentTemplateSummaryInfo template={selectedTemplate} />
        <Box sx={{ mb: 1 }}>
          <Divider />
        </Box>
        <InstrumentTemplatePicker setTemplate={handleSetTemplate} selectedTemplate={selectedTemplate} />
        {!createNewTemplate && !selectedTemplate ? (
          <Alert severity="info">{t("import.templateDetails.selectInstrumentTemplate")}</Alert>
        ) : null}
      </Box>
    </RadioGroup>
  );
}

export default observer(InstrumentTemplateDetails);
