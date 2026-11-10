import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import Alerts from "@/components/Alerts/Alerts";
import i18n from "../../../modules/common/i18n";
import materialTheme from "../../../theme";
import GroupAutoshareManager from "./Autoshare/GroupAutoshareManager";
import GroupBioOntologiesManager from "./GroupBioOntologiesManager";
import GroupOntologiesManager from "./GroupOntologiesManager";
import GroupPublishManager from "./GroupPublishManager";
import GroupSeoManager from "./GroupSeoManager";
import MyLabGroupsDialog from "./MyLabGroupsDialog";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class MyLabGroups extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  membersDialog: React.RefObject<any>;

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = {
      groupId: props.domContainer.dataset.groupid,
      groupDisplayName: props.domContainer.dataset.displayname,
      role: props.domContainer.dataset.role,
      groupUniqueId: props.domContainer.dataset.uniquename,
      groupType: props.domContainer.dataset.grouptype,
      canEdit: props.domContainer.dataset.canedit === "true",
      canManageAutoshare: props.domContainer.dataset.canmanageautoshare === "true",
      canManageOntologies: props.domContainer.dataset.canmanageontologies === "true",
      canManagePublish: props.domContainer.dataset.canmanagepublish === "true",
      isGroupAutoshareAllowed: props.domContainer.dataset.isgroupautoshareallowed === "true",
      isGroupPublicationAllowed: props.domContainer.dataset.isgrouppublicationallowed === "true",
      isGroupSEOAllowed: props.domContainer.dataset.isgroupseoallowed === "true",
      isCloud: props.domContainer.dataset.iscloud === "true",
      isLabGroup: props.domContainer.dataset.islabgroup === "true",
    };

    this.membersDialog = React.createRef();
  }

  openDialog = () => {
    this.membersDialog.current.openDialog();
  };

  render() {
    return (
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <Alerts>
            <Box sx={{ display: "flex", width: "100%", alignItems: "center" }}>
              <h3>{i18n.t("profile.groups.members")}</h3>
              {this.state.isCloud && (
                <Button
                  id="inviteNewMembersGrpLink"
                  variant="outlined"
                  size="small"
                  sx={{ margin: "0 0 0.5em 15px" }}
                  data-test-id="button-invite-members"
                >
                  {i18n.t("actions.invite")}
                </Button>
              )}
              {!this.state.isCloud && this.state.canEdit && this.state.role !== "admin" && (
                <Button
                  onClick={this.openDialog}
                  variant="outlined"
                  size="small"
                  sx={{ margin: "0 0 0.5em 15px" }}
                  data-test-id="button-add-members"
                >
                  {i18n.t("actions.invite")}
                </Button>
              )}
              {this.state.canEdit && this.state.role === "admin" && (
                <Button
                  onClick={this.openDialog}
                  variant="outlined"
                  size="small"
                  sx={{ margin: "0 0 0.5em 15px" }}
                  data-test-id="button-add-members"
                >
                  {i18n.t("actions.add")}
                </Button>
              )}
              {this.state.canManageAutoshare && (
                <GroupAutoshareManager
                  groupId={parseInt(this.state.groupId, 10)}
                  groupDisplayName={this.state.groupDisplayName}
                  isCloud={this.state.isCloud}
                  isLabGroup={this.state.isLabGroup}
                  isGroupAutoshareAllowed={this.state.isGroupAutoshareAllowed}
                />
              )}
              {
                <GroupPublishManager
                  groupId={parseInt(this.state.groupId, 10)}
                  groupDisplayName={this.state.groupDisplayName}
                  isCloud={this.state.isCloud}
                  isLabGroup={this.state.isLabGroup}
                  isGroupPublicationAllowed={this.state.isGroupPublicationAllowed}
                  canManagePublish={this.state.canManagePublish}
                />
              }
              {
                <GroupSeoManager
                  groupId={parseInt(this.state.groupId, 10)}
                  groupDisplayName={this.state.groupDisplayName}
                  isCloud={this.state.isCloud}
                  isLabGroup={this.state.isLabGroup}
                  isGroupSeoAllowed={this.state.isGroupSEOAllowed}
                  canManagePublish={this.state.canManagePublish}
                />
              }
              {
                <GroupOntologiesManager
                  groupId={parseInt(this.state.groupId, 10)}
                  isCloud={this.state.isCloud}
                  canManageOntologies={this.state.canManageOntologies}
                />
              }
              {
                <GroupBioOntologiesManager
                  groupId={parseInt(this.state.groupId, 10)}
                  isCloud={this.state.isCloud}
                  canManageOntologies={this.state.canManageOntologies}
                />
              }
            </Box>
            <MyLabGroupsDialog
              ref={this.membersDialog}
              role={this.state.role}
              groupId={this.state.groupId}
              groupUniqueId={this.state.groupUniqueId}
              groupType={this.state.groupType}
            />
          </Alerts>
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

export default MyLabGroups;
