//@flow

import NoValue from "../../components/NoValue";
import TemplateModel from "../../stores/models/TemplateModel";
import GlobalId from "../../components/GlobalId";
import { library } from "@fortawesome/fontawesome-svg-core";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
library.add(faSpinner);
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemSecondaryAction from "@mui/material/ListItemSecondaryAction";
import ListItemText from "@mui/material/ListItemText";
import { observer } from "mobx-react-lite";
import React, { type Node, type ComponentType } from "react";
import Box from "@mui/material/Box";

type OneItemListArgs = {|
  avatar: Node,
  text: string,
  action?: Node,
|};

const OneItemList = ({ avatar, text, action }: OneItemListArgs): Node => (
  <List dense disablePadding>
    <ListItem>
      <ListItemAvatar>{avatar}</ListItemAvatar>
      <ListItemText
        primary={text}
        style={{ overflowWrap: "anywhere", maxWidth: "60%" }}
      />
      <ListItemSecondaryAction>{action}</ListItemSecondaryAction>
    </ListItem>
  </List>
);

type SummaryInfoArgs = {|
  template: ?TemplateModel,
  loading?: boolean,
  paddingless?: boolean,
|};

/*
 * Shows basic information about a template, for placing inline within a form.
 */
function SummaryInfo({
  template,
  loading,
  paddingless,
}: SummaryInfoArgs): Node {
  if (template) {
    return (
      <OneItemList
        avatar={<img src={template.icon} width={32} height={32} />}
        text={template.name}
        action={<GlobalId record={template} />}
      />
    );
  }
  if (loading) {
    return (
      <OneItemList
        avatar={<FontAwesomeIcon icon="spinner" spin size="lg" />}
        text="Loading"
      />
    );
  }
  if (paddingless) {
    return <NoValue label="No Template" />;
  }
  return (
    <Box my={1.5}>
      <NoValue label="No Template" />
    </Box>
  );
}

export default (observer(SummaryInfo): ComponentType<SummaryInfoArgs>);
