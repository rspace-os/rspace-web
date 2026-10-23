import Box from "@mui/material/Box";
import FormControlLabel from "@mui/material/FormControlLabel";
import Switch from "@mui/material/Switch";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import { useTranslation } from "react-i18next";
import { Heading } from "../../components/DynamicHeadingLevel";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import { useDeploymentProperty } from "../../hooks/api/useDeploymentProperty";
import { helpDocsArticleUrl } from "../../modules/common/i18n/TransRichText";
import type SampleModel from "../../stores/models/SampleModel";
import * as FetchingData from "../../util/fetchingData";
import * as Parser from "../../util/parsers";

const RequestableSwitch = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const sampleRequestsAvailable = FetchingData.getSuccessValue(
    useDeploymentProperty("inventory.sampleRequests.available"),
  )
    .flatMap(Parser.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);

  // canSetRequestable is computed server-side from the exact same check the backend itself
  // enforces before allowing requestable to change (SampleApiManagerImpl.
  // assertRequestableChangeAllowed -> canUserTransferInventoryRecord: the owner, or a PI/Lab Admin
  // of the owner's group). Neither of the generic getters further up the hierarchy is right here:
  // canEdit also covers any other group member with mere edit access, and canTransfer (CHANGE_OWNER)
  // also covers a sysadmin's admin-override ability - both broader than who may actually toggle this.
  if (!sampleRequestsAvailable || !activeResult.canSetRequestable) return null;

  return (
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
      <FormControlLabel
        sx={{ m: 0 }}
        control={
          <Switch
            checked={activeResult.requestable}
            onChange={({ target: { checked } }) => activeResult.setAttributesDirty({ requestable: checked })}
            color="primary"
            disabled={!activeResult.isFieldEditable("requestable")}
          />
        }
        label={t("sample.requestsSection.switchLabel")}
      />
    </Box>
  );
});

export default RequestableSwitch;
