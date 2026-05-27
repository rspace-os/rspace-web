"use strict";
import React from "react";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../../theme";
import MyLabGroupsDialog from "./MyLabGroupsDialog";
import GroupAutoshareManager from "./Autoshare/GroupAutoshareManager";
import { createRoot } from "react-dom/client";
import GroupPublishManager from "./GroupPublishManager";
import GroupSeoManager from "./GroupSeoManager";
import GroupOntologiesManager from "./GroupOntologiesManager";
import GroupBioOntologiesManager from "./GroupBioOntologiesManager";

const styles = {
  iconButton: {
    width: "20px",
    height: "20px",
    padding: "0px",
    margin: "0px 0px 0px 15px",
    fontSize: "18px",
  },
  heading: {
    alignSelf: "center",
  },
  actions: {
    display: "flex",
    width: "100%",
    alignItems: "center",
  },
};

class MyLabGroups extends React.Component {
  constructor() {
    super();
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
      isGroupAutoshareAllowed:
        domContainer.dataset.isgroupautoshareallowed === "true",
      isGroupPublicationAllowed:
        domContainer.dataset.isgrouppublicationallowed === "true",
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
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <div style={styles.actions}>
            <h3>Members</h3>
            {this.state.isCloud && (
              <Button
                id="inviteNewMembersGrpLink"
                variant="outlined"
                size="small"
                style={{ margin: "0 0 0.5em 15px" }}
                data-test-id="button-invite-members"
              >
                Invite
              </Button>
            )}
            {!this.state.isCloud &&
              this.state.canEdit &&
              this.state.role !== "admin" && (
                <Button
                  onClick={this.openDialog}
                  variant="outlined"
                  size="small"
                  style={{ margin: "0 0 0.5em 15px" }}
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
                style={{ margin: "0 0 0.5em 15px" }}
                data-test-id="button-add-members"
              >
                Add
              </Button>
            )}
            {this.state.canManageAutoshare && (
              <GroupAutoshareManager
                groupId={parseInt(this.state.groupId)}
                groupDisplayName={this.state.groupDisplayName}
                isCloud={this.state.isCloud}
                isLabGroup={this.state.isLabGroup}
                isGroupAutoshareAllowed={this.state.isGroupAutoshareAllowed}
              />
            )}
            {
              <GroupPublishManager
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
                groupId={parseInt(this.state.groupId)}
                isCloud={this.state.isCloud}
                canManageOntologies={this.state.canManageOntologies}
              />
            }
            {
              <GroupBioOntologiesManager
                groupId={parseInt(this.state.groupId)}
                isCloud={this.state.isCloud}
                canManageOntologies={this.state.canManageOntologies}
              />
            }
          </div>
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

const domContainer = document.getElementById("myLabGroups");
const root = createRoot(domContainer);
root.render(<MyLabGroups />);
