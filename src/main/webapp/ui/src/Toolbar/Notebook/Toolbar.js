/* eslint-disable react/prop-types */
"use strict";
import React from "react";
import { createRoot } from "react-dom/client";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../theme";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import { library } from "@fortawesome/fontawesome-svg-core";
import PrintButton from "../components/PrintButton";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faEdit,
  faTrashAlt,
  faCloudDownloadAlt,
  faFileSignature,
  faFolder,
  faShareAlt,
  faEye,
  faTimes,
} from "@fortawesome/free-solid-svg-icons";
library.add(
  faEdit,
  faTrashAlt,
  faCloudDownloadAlt,
  faFileSignature,
  faFolder,
  faShareAlt,
  faEye,
  faTimes
);

import BaseToolbar from "../../components/BaseToolbar";
import CreateMenu from "../ToolbarCreateMenu";
import SocialActions from "../ToolbarSocial";

class NotebookToolbar extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      workspaceFolderId: props.domContainer.getAttribute(
        "data-workspace-folder-id"
      ),
      settingsKey: props.domContainer.getAttribute("data-settings-key"),
      canCreateRecord:
        props.domContainer.getAttribute("data-can-create-record") === "true",
      pioEnabled:
        props.domContainer.getAttribute("data-pio-enabled") === "true",
      evernoteEnabled:
        props.domContainer.getAttribute("data-evernote-enabled") === "true",
      asposeEnabled:
        props.domContainer.getAttribute("data-aspose-enabled") === "true",
      canDelete: props.domContainer.getAttribute("data-can-delete") === "true",
      canShare: props.domContainer.getAttribute("data-can-share") === "true",
    };
  }

  content = () => {
    return (
      <span style={{ display: "flex", width: "100%" }}>
        <Tooltip title="Back" enterDelay={300}>
          <IconButton
            id="close"
            data-test-id="structured-document-back"
            href={`/workspace/${this.state.workspaceFolderId}?settingsKey=${this.state.settingsKey}`}
          >
            <FontAwesomeIcon icon="times" />
          </IconButton>
        </Tooltip>
        <span
          style={{
            borderRight: "1px solid transparent",
            margin: "0px 10px",
            height: "100%",
          }}
        ></span>
        {this.state.canCreateRecord && (
          <CreateMenu
            pioEnabled={this.state.pioEnabled}
            evernoteEnabled={this.state.evernoteEnabled}
            asposeEnabled={this.state.asposeEnabled}
          />
        )}
        <Tooltip title="Edit" enterDelay={300}>
          <IconButton
            data-test-id="notebooktoolbar-edit"
            color="inherit"
            id="editEntry"
          >
            <FontAwesomeIcon icon="edit" />
          </IconButton>
        </Tooltip>
        {this.state.canDelete && (
          <Tooltip title="Delete" enterDelay={300}>
            <IconButton
              data-test-id="notebooktoolbar-delete"
              color="inherit"
              id="deleteEntry"
            >
              <FontAwesomeIcon icon="trash-alt" />
            </IconButton>
          </Tooltip>
        )}
        <Tooltip title="Sign" enterDelay={300}>
          <IconButton
            data-test-id="notebooktoolbar-sign"
            color="inherit"
            id="signDocument"
          >
            <FontAwesomeIcon icon="file-signature" />
          </IconButton>
        </Tooltip>
        <Tooltip title="Witness" enterDelay={300}>
          <IconButton
            data-test-id="notebooktoolbar-witness"
            color="inherit"
            id="witnessDocument"
          >
            <FontAwesomeIcon icon="eye" />
          </IconButton>
        </Tooltip>
        {this.state.canShare && (
          <Tooltip title="Share" enterDelay={300}>
            <IconButton
              data-test-id="notebooktoolbar-share"
              color="inherit"
              id="shareRecord"
            >
              <FontAwesomeIcon icon="share-alt" />
            </IconButton>
          </Tooltip>
        )}
        <span
          style={{
            borderRight: "1px solid transparent",
            margin: "0px 10px",
            height: "100%",
          }}
        ></span>
        {this.props.conditionalRender.export && (
          <Tooltip title="Export" enterDelay={300}>
            <IconButton
              data-test-id="notebooktoolbar-export"
              color="inherit"
              id="exportDocument"
              onClick={this.props.eventHandlers.onExportDocument}
            >
              <FontAwesomeIcon icon="cloud-download-alt" />
            </IconButton>
          </Tooltip>
        )}
        {this.props.conditionalRender.print && (
          <PrintButton dataTestId="notebooktoolbar-print" />
        )}
        <SocialActions
          showExternal={true}
          style={{ flexGrow: "1", justifyContent: "flex-end" }}
          onCreateRequest={this.props.eventHandlers.onCreateRequest}
        />
      </span>
    );
  };

  componentDidMount() {
    /*
     * This event tells the jQuery parts of the code that they can assume that
     * the dom nodes managed by react have now been added to the dom.
     */
    setTimeout(() => {
      window.dispatchEvent(new CustomEvent("ReactToolbarMounted"));
    }, 0);
  }

  render() {
    return (
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <BaseToolbar content={this.content()} />
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

let rootNode;
let domContainer;

/*
 * We remember the last props so that renderToolbar only needs to be passed the
 * props that have changed
 */
let prevProps = {
  conditionalRender: {
    print: true,
    export: true,
  },
  eventHandlers: {
    onCreateRequest: () => {},
    onExportDocument: () => {},
  },
};

window.renderToolbar = (newProps) => {
  if (!rootNode) {
    domContainer = document.getElementById("toolbar2");
    rootNode = createRoot(domContainer);
  }
  prevProps = {
    conditionalRender: {
      ...prevProps.conditionalRender,
      ...(newProps?.conditionalRender ?? {}),
    },
    eventHandlers: {
      ...prevProps.eventHandlers,
      ...(newProps?.eventHandlers ?? {}),
    },
  };
  rootNode.render(
    <NotebookToolbar domContainer={domContainer} {...prevProps} />
  );
};

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched above.
 */
window.addEventListener("load", function () {
  window.renderToolbar();
});
