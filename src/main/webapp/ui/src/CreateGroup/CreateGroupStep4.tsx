import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const createGroupStep4 = (props: any) => {
  const { t, i18n } = useTranslation("groups");
  const language = i18n.resolvedLanguage ?? i18n.language;
  const selfService = $("#selfServiceLabGroup").length !== 0;
  const projectGroup = $("#projectGroup").length !== 0;
  return (
    <Box sx={{ padding: "0 25px 10px 25px" }}>
      <h3>{t("createGroup.step4.heading")}</h3>
      <p data-test-id="createGroupSummaryGroupName">
        <strong>{t("createGroup.step4.groupNameLabel")}</strong>{" "}
        {props.summary.groupName === "" ? (
          <Typography variant="inherit" component="span" sx={{ color: "#f44336" }}>
            {t("createGroup.step4.groupNameRequired")}
          </Typography>
        ) : (
          props.summary.groupName
        )}
      </p>
      <p data-test-id="createGroupSummaryPI">
        {projectGroup && <strong>{t("createGroup.step4.groupOwnerLabel")}</strong>}
        {!projectGroup && <strong>{t("createGroup.step4.piLabel")}</strong>}{" "}
        {props.summary.selectPI.selectedUser === "" ? (
          <Typography variant="inherit" component="span" sx={{ color: "#f44336" }}>
            {t("createGroup.step4.piRequired")}
          </Typography>
        ) : (
          props.summary.selectPI.selectedUser
        )}
      </p>
      <p data-test-id="createGroupSummaryInvitedMem">
        <strong>{t("createGroup.step4.invitedMembersLabel")}</strong>{" "}
        {formatList(props.summary.existingUsers, language)}
      </p>
      {!selfService && (
        <p data-test-id="createGroupSummaryInvitedNonMem">
          <strong>{t("createGroup.step4.invitedNonMembersLabel")}</strong>{" "}
          {formatList(props.summary.newUsers, language)}
        </p>
      )}
    </Box>
  );
};

export default createGroupStep4;
