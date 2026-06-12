import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import materialTheme from "../../../theme";
import GroupAutoshareManager from "./Autoshare/GroupAutoshareManager";
import GroupBioOntologiesManager from "./GroupBioOntologiesManager";
import GroupOntologiesManager from "./GroupOntologiesManager";
import GroupPublishManager from "./GroupPublishManager";
import GroupSeoManager from "./GroupSeoManager";
import MyLabGroupsDialog from "./MyLabGroupsDialog";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
class MyLabGroups extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  membersDialog: React.RefObject<any>;

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  constructor(props: any) {
    super(props);
    this.state = {
      groupId: domContainer.dataset.groupid,
      groupDisplayName: domContainer.dataset.displayname,
      role: domContainer.dataset.role,
      groupUniqueId: domContainer.dataset.uniquename,
      groupType: domContainer.dataset.grouptype,
      canEdit: domContainer.dataset.canedit === "true",
      canManageAutoshare: domContainer.dataset.canmanageautoshare === "true",
      canManageOntologies: domContainer.dataset.canmanageontologies === "true",
      canManagePublish: domContainer.dataset.canmanagepublish === "true",
      isGroupAutoshareAllowed: domContainer.dataset.isgroupautoshareallowed === "true",
      isGroupPublicationAllowed: domContainer.dataset.isgrouppublicationallowed === "true",
      isGroupSEOAllowed: domContainer.dataset.isgroupseoallowed === "true",
      isCloud: domContainer.dataset.iscloud === "true",
      isLabGroup: domContainer.dataset.islabgroup === "true",
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
          <Box sx={{ display: "flex", width: "100%", alignItems: "center" }}>
            <h3>Members</h3>
            {this.state.isCloud && (
              <Button
                id="inviteNewMembersGrpLink"
                variant="outlined"
                size="small"
                sx={{ margin: "0 0 0.5em 15px" }}
                data-test-id="button-invite-members"
              >
                Invite
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
                Invite
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
                Add
              </Button>
            )}
            {this.state.canManageAutoshare && (
              <GroupAutoshareManager
                // biome-ignore lint/correctness/useParseIntRadix: initial biome migration
                groupId={parseInt(this.state.groupId)}
                groupDisplayName={this.state.groupDisplayName}
                isCloud={this.state.isCloud}
                isLabGroup={this.state.isLabGroup}
                isGroupAutoshareAllowed={this.state.isGroupAutoshareAllowed}
              />
            )}
            {
              <GroupPublishManager
                // biome-ignore lint/correctness/useParseIntRadix: initial biome migration
                groupId={parseInt(this.state.groupId)}
                groupDisplayName={this.state.groupDisplayName}
                isCloud={this.state.isCloud}
                isLabGroup={this.state.isLabGroup}
                isGroupPublicationAllowed={this.state.isGroupPublicationAllowed}
                canManagePublish={this.state.canManagePublish}
              />
            }
            {
              <GroupSeoManager
                // biome-ignore lint/correctness/useParseIntRadix: initial biome migration
                groupId={parseInt(this.state.groupId)}
                groupDisplayName={this.state.groupDisplayName}
                isCloud={this.state.isCloud}
                isLabGroup={this.state.isLabGroup}
                isGroupSeoAllowed={this.state.isGroupSEOAllowed}
                canManagePublish={this.state.canManagePublish}
              />
            }
            {
              <GroupOntologiesManager
                // biome-ignore lint/correctness/useParseIntRadix: initial biome migration
                groupId={parseInt(this.state.groupId)}
                isCloud={this.state.isCloud}
                canManageOntologies={this.state.canManageOntologies}
              />
            }
            {
              <GroupBioOntologiesManager
                // biome-ignore lint/correctness/useParseIntRadix: initial biome migration
                groupId={parseInt(this.state.groupId)}
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
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

const domContainer = document.getElementById("myLabGroups") as HTMLElement;
const root = createRoot(domContainer);
root.render(<MyLabGroups />);
