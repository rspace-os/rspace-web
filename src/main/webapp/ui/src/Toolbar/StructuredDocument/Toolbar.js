/* eslint-disable react/prop-types */
"use strict";
import React from "react";
import { createRoot } from "react-dom/client";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../theme";
import IconButton from "@mui/material/IconButton";
import Button from "@mui/material/Button";
import Tooltip from "@mui/material/Tooltip";
import PrintButton from "../components/PrintButton";
import { library } from "@fortawesome/fontawesome-svg-core";
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
import SaveMenu from "./ToolbarSaveMenu";
import SocialActions from "../ToolbarSocial";

class StructuredDocumentToolbar extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      closeHref: props.domContainer.getAttribute("data-close-href"),
      canCopy: props.domContainer.getAttribute("data-can-copy") === "true",
      canShare: props.domContainer.getAttribute("data-can-share") === "true",
      canWitness:
        props.domContainer.getAttribute("data-can-witness") === "true",
      canSign: props.canSign,
      canDelete: props.domContainer.getAttribute("data-can-delete") === "true",
      isCloud: props.domContainer.getAttribute("data-cloud") === "true",
      isTemplate:
        props.domContainer.getAttribute("data-is-template") === "true",
      emptyDocrevision:
        props.domContainer.getAttribute("data-docrevision-empty") === "true",
      version: props.domContainer.getAttribute("data-version"),
    };
  }

  content = () => {
    return (
      <span style={{ display: "flex", width: "100%" }}>
        <Tooltip title="Close" enterDelay={300}>
          <IconButton
            data-test-id="structured-document-back"
            href={this.state.closeHref}
            id="close"
          >
            <FontAwesomeIcon icon="times" />
          </IconButton>
        </Tooltip>
        <span
          className="editMode"
          style={{
            borderRight: "1px solid transparent",
            margin: "0px 10px",
            height: "100%",
          }}
        ></span>
        <SaveMenu canCopy={this.state.canCopy} />
        <Button
          id="cancel"
          className="editMode"
          data-test-id="notebooktoolbar-cancel"
          style={{ color: "white" }}
        >
          Cancel
        </Button>
        {!this.state.emptyDocrevision && (
          <span>
            <span
              className="templateActionDiv"
              style={{
                borderRight: "1px solid transparent",
                margin: "0px 10px",
                height: "100%",
              }}
            ></span>
            {this.state.isTemplate && (
              <Button
                id="createDocFromTemplate"
                className="templateActionDiv"
                data-test-id="notebooktoolbar-createDocFromTemplate"
                style={{ color: "white" }}
              >
                CREATE DOCUMENT
              </Button>
            )}
            {!this.state.isTemplate && (
              <Button
                id="saveAsTemplateBtn"
                className="templateActionDiv"
                data-test-id="notebooktoolbar-saveAsTemplateBtn"
                style={{ color: "white" }}
              >
                SAVE AS TEMPLATE
              </Button>
            )}
            <span
              style={{
                borderRight: "1px solid transparent",
                margin: "0px 10px",
                height: "100%",
              }}
            ></span>
            {this.state.canDelete && (
              <Tooltip title="Delete" enterDelay={300}>
                <IconButton
                  data-test-id="structured-delete"
                  color="inherit"
                  id="delete"
                >
                  <FontAwesomeIcon icon="trash-alt" />
                </IconButton>
              </Tooltip>
            )}
            {this.props.canSign && (
              <Tooltip title="Sign" enterDelay={300}>
                <IconButton
                  data-test-id="structured-sign"
                  color="inherit"
                  id="signDocument"
                  onClick={this.props.eventHandlers.onCanSign}
                >
                  <FontAwesomeIcon icon="file-signature" />
                </IconButton>
              </Tooltip>
            )}
            {this.state.canWitness && (
              <Tooltip title="Witness" enterDelay={300}>
                <IconButton
                  data-test-id="structured-witness"
                  color="inherit"
                  id="witnessDocument"
                >
                  <FontAwesomeIcon icon="eye" />
                </IconButton>
              </Tooltip>
            )}
            {this.state.canShare && (
              <Tooltip title="Share" enterDelay={300}>
                <IconButton
                  data-cloud={this.state.isCloud}
                  data-test-id="notebooktoolbar-share"
                  color="inherit"
                  id="shareRecord"
                >
                  <FontAwesomeIcon icon="share-alt" />
                </IconButton>
              </Tooltip>
            )}
            {(this.state.canDelete ||
              this.state.canWitness ||
              this.state.canSign) && (
              <span
                style={{
                  borderRight: "1px solid transparent",
                  margin: "0px 10px",
                  height: "100%",
                }}
              ></span>
            )}
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
            <PrintButton dataTestId="notebooktoolbar-print" />
            <span
              style={{
                borderRight: "1px solid transparent",
                margin: "0px 10px",
                height: "100%",
              }}
            ></span>
          </span>
        )}
        {this.state.emptyDocrevision && (
          <span
            data-test-id="notebooktoolbar-docversion"
            style={{
              fontSize: "15px",
              alignItems: "center",
              display: "flex",
              justifyContent: "center",
              marginLeft: "10px",
            }}
          >
            Displaying version {this.state.version} of the document - this is
            locked for editing.
          </span>
        )}
        <SocialActions
          onCreateRequest={this.props.eventHandlers.onCreateRequest}
          showExternal={true}
          style={{ flexGrow: "1", justifyContent: "flex-end" }}
        />
      </span>
    );
  };

  componentDidMount() {
    /*
     * This event tells the jQuery parts of the code that they can assume that
     * the DOM nodes managed by React have now been added to the DOM. The
     * setTimout of 0ms gives the save menu, a child componet of this toolbar,
     * the opportunity to render its invisible menu into the DOM as there is
     * some code listening for this ReactToolbarMounted that accesses the DOM
     * nodes within the save menu. Ultimately we should refactor the code so
     * that these event handles are passed as props rather than having outside
     * code attach event handlers to DOM nodes managed by React.
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
  conditionalRender: {},
  eventHandlers: {
    onCreateRequest: () => {},
    onExportDocument: () => {},
    onCanSign: () => {},
  },
  canSign: false,
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
    canSign: newProps?.canSign ?? prevProps.canSign,
  };
  rootNode.render(
    <StructuredDocumentToolbar domContainer={domContainer} {...prevProps} canSign={prevProps.canSign} />
  );
};

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched above.
 */
window.addEventListener("load", function () {
  const domContainer = document.getElementById("toolbar2");
  window.renderToolbar({
    canSign: domContainer.getAttribute("data-can-sign") === "true",
  });
});
