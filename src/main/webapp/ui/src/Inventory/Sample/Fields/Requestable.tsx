import Box from "@mui/material/Box";
import FormControlLabel from "@mui/material/FormControlLabel";
import Switch from "@mui/material/Switch";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import { useTranslation } from "react-i18next";
import { Heading } from "../../../components/DynamicHeadingLevel";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import ChooseToEdit from "../../../components/Inputs/ChooseToEdit";
import { useDeploymentProperty } from "../../../hooks/api/useDeploymentProperty";
import { helpDocsArticleUrl } from "../../../modules/common/i18n/TransRichText";
import type { SampleCollection } from "../../../stores/models/SampleModel";
import * as FetchingData from "../../../util/fetchingData";
import * as Parser from "../../../util/parsers";

/**
 * The batch-editing counterpart of RequestableSwitch.tsx. Shown in the Overview section of the
 * Sample batch-edit form, alongside the generic "Batch edit this field" checkbox (ChooseToEdit)
 * that every other batch-editable field already uses, so the whole selection's requestable value
 * is only sent to the server once the user has opted in.
 */
const Requestable = observer(({ collection }: { collection: SampleCollection }) => {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const sampleRequestsAvailable = FetchingData.getSuccessValue(
    useDeploymentProperty("inventory.sampleRequests.available"),
  )
    .flatMap(Parser.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);

  // Mirrors RequestableSwitch's own guard, extended across the whole selection: hides the whole
  // box unless every selected sample is one the signed-in user may toggle (the owner, or a PI/Lab
  // Admin of the owner's group).
  if (!sampleRequestsAvailable || !collection.canSetRequestable) return null;

  const editable = collection.isFieldEditable("requestable");
  // Non-null only when the selected samples don't all currently agree, in which case there's no
  // single switch position that would honestly represent the selection until the user opts in.
  const noValueLabel = collection.noValueLabel.requestable;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
      {/*
       * ChooseToEdit positions itself absolutely, in the top-right corner of the nearest
       * positioned ancestor. Every other batch field has a label row above its actual input,
       * leaving that corner spare, but this card's title and switch already fill its only row, so
       * ChooseToEdit is given its own reserved-height row above the card instead of being nested
       * inside it, where it would float on top of the switch and block it from being operated.
       */}
      <Box sx={{ position: "relative", minHeight: 40 }}>
        <ChooseToEdit
          checked={editable}
          onChange={(checked) => collection.setFieldEditable("requestable", checked)}
          label={t("sample.requestsSection.batchEditLabel")}
        />
      </Box>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 2,
          p: 1.5,
          borderRadius: 1,
          border: `1px solid ${theme.palette.divider}`,
          backgroundColor: theme.palette.record.sample.lighter,
        }}
      >
        <Box>
          <Heading sx={{ mt: 0, display: "flex", alignItems: "center", gap: 0.5 }}>
            {t("sample.requestsSection.title")}
            <HelpLinkIcon
              link={helpDocsArticleUrl("sampleRequests")}
              title={t("requestsManagement.helpTitle")}
              fontSize="inherit"
            />
          </Heading>
          <Typography variant="body2">{t("sample.requestsSection.description")}</Typography>
        </Box>
        {editable || !noValueLabel ? (
          <FormControlLabel
            sx={{ m: 0 }}
            control={
              <Switch
                checked={collection.fieldValues.requestable}
                onChange={({ target: { checked } }) => collection.setFieldsDirty({ requestable: checked })}
                color="primary"
                disabled={!editable}
              />
            }
            label={t("sample.requestsSection.switchLabel")}
          />
        ) : (
          <Typography variant="body2">{noValueLabel}</Typography>
        )}
      </Box>
    </Box>
  );
});

export default Requestable;
