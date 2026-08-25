import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemSecondaryAction from "@mui/material/ListItemSecondaryAction";
import ListItemText from "@mui/material/ListItemText";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "../../components/GlobalId";
import NoValue from "../../components/NoValue";
import RecordTypeIcon from "../../components/RecordTypeIcon";
import type InstrumentTemplateModel from "../../stores/models/InstrumentTemplateModel";

type OneItemListArgs = {
  avatar: React.ReactNode;
  text: string;
  action?: React.ReactNode;
};

const OneItemList = ({ avatar, text, action }: OneItemListArgs): React.ReactNode => (
  <List dense disablePadding>
    <ListItem>
      <ListItemAvatar>{avatar}</ListItemAvatar>
      <ListItemText primary={text} sx={{ overflowWrap: "anywhere", maxWidth: "60%" }} />
      <ListItemSecondaryAction>{action}</ListItemSecondaryAction>
    </ListItem>
  </List>
);

type SummaryInfoArgs = {
  template: InstrumentTemplateModel | null;
  loading?: boolean;
  paddingless?: boolean;
};

function SummaryInfo({ template, loading, paddingless }: SummaryInfoArgs): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);

  if (template) {
    const avatar = template.thumbnail ? (
      <img src={template.thumbnail ?? undefined} width={32} height={32} alt="" />
    ) : (
      <RecordTypeIcon
        record={{ recordTypeLabel: "", iconName: "instrumentTemplate" }}
        color=""
        style={{ width: "18px", height: "18px", padding: "7px" }}
        aria-hidden
      />
    );
    return <OneItemList avatar={avatar} text={template.name} action={<GlobalId record={template} />} />;
  }
  if (loading) {
    return <OneItemList avatar={<FontAwesomeIcon icon={faSpinner} spin size="lg" />} text={t("common:loading")} />;
  }
  if (paddingless) {
    return <NoValue label={t("template.summary.noTemplate")} />;
  }
  return (
    <Box sx={{ my: 1.5 }}>
      <NoValue label={t("template.summary.noTemplate")} />
    </Box>
  );
}

export default observer(SummaryInfo);
