/* eslint-disable react/prop-types */
import { faCloudDownloadAlt } from "@fortawesome/free-solid-svg-icons/faCloudDownloadAlt";
import { faEye } from "@fortawesome/free-solid-svg-icons/faEye";
import { faFileSignature } from "@fortawesome/free-solid-svg-icons/faFileSignature";
import { faShareAlt } from "@fortawesome/free-solid-svg-icons/faShareAlt";
import { faTimes } from "@fortawesome/free-solid-svg-icons/faTimes";
import { faTrashAlt } from "@fortawesome/free-solid-svg-icons/faTrashAlt";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import Skeleton from "@mui/material/Skeleton";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import Analytics from "../../components/Analytics";
import BaseToolbar from "../../components/BaseToolbar";
import ShareDialog from "../../components/ShareDialog";
import i18n from "../../modules/common/i18n";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import AnalyticsContext from "../../stores/contexts/Analytics";
import materialTheme from "../../theme";
import PrintButton from "../components/PrintButton";
import SocialActions from "../ToolbarSocial";
import SaveMenu from "./ToolbarSaveMenu";

declare global {
  interface Window {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    renderToolbar: (newProps?: any) => void;
  }
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class StructuredDocumentToolbar extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  declare context: any;
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = {
      closeHref: props.domContainer.getAttribute("data-close-href"),
      canCopy: props.domContainer.getAttribute("data-can-copy") === "true",
      canShare: props.domContainer.getAttribute("data-can-share") === "true",
      canWitness: props.domContainer.getAttribute("data-can-witness") === "true",
      canSign: props.canSign,
      canDelete: props.domContainer.getAttribute("data-can-delete") === "true",
      isCloud: props.domContainer.getAttribute("data-cloud") === "true",
      isTemplate: props.domContainer.getAttribute("data-is-template") === "true",
      emptyDocrevision: props.domContainer.getAttribute("data-docrevision-empty") === "true",
      version: props.domContainer.getAttribute("data-version"),
    };
  }

  content = () => {
    return (
      <>
        <Box component="span" sx={{ display: "flex", width: "100%" }}>
          <Tooltip title={i18n.t("common:actions.close")} enterDelay={300}>
            <IconButton data-test-id="structured-document-back" href={this.state.closeHref} id="close">
              <FontAwesomeIcon icon={faTimes} />
            </IconButton>
          </Tooltip>
          <Box
            component="span"
            className="editMode"
            sx={{
              borderRight: "1px solid transparent",
              margin: "0px 10px",
              height: "100%",
            }}
          ></Box>
          <SaveMenu canCopy={this.state.canCopy} />
          <Button id="cancel" className="editMode" data-test-id="notebooktoolbar-cancel" sx={{ color: "white" }}>
            {i18n.t("common:actions.cancel")}
          </Button>
          {!this.state.emptyDocrevision && (
            <span>
              <Box
                component="span"
                className="templateActionDiv"
                sx={{
                  borderRight: "1px solid transparent",
                  margin: "0px 10px",
                  height: "100%",
                }}
              ></Box>
              {this.state.isTemplate && (
                <Button
                  id="createDocFromTemplate"
                  className="templateActionDiv"
                  data-test-id="notebooktoolbar-createDocFromTemplate"
                  sx={{ color: "white" }}
                >
                  {i18n.t("common:toolbar.createDocument")}
                </Button>
              )}
              {!this.state.isTemplate && (
                <Button
                  id="saveAsTemplateBtn"
                  className="templateActionDiv"
                  data-test-id="notebooktoolbar-saveAsTemplateBtn"
                  sx={{ color: "white" }}
                >
                  {i18n.t("common:toolbar.saveAsTemplate")}
                </Button>
              )}
              <Box
                component="span"
                sx={{
                  borderRight: "1px solid transparent",
                  margin: "0px 10px",
                  height: "100%",
                }}
              ></Box>
              {this.state.canDelete && (
                <Tooltip title={i18n.t("common:actions.delete")} enterDelay={300}>
                  <IconButton
                    data-test-id="structured-delete"
                    color="inherit"
                    id="delete"
                    onClick={() => {
                      this.context.trackEvent("user:delete:document:document_editor");
                    }}
                  >
                    <FontAwesomeIcon icon={faTrashAlt} />
                  </IconButton>
                </Tooltip>
              )}
              {this.props.canSign && (
                <Tooltip title={i18n.t("common:actions.sign")} enterDelay={300}>
                  <IconButton
                    data-test-id="structured-sign"
                    color="inherit"
                    id="signDocument"
                    onClick={() => {
                      this.props.eventHandlers.onCanSign();
                      this.context.trackEvent("user:sign:document:document_editor");
                    }}
                  >
                    <FontAwesomeIcon icon={faFileSignature} />
                  </IconButton>
                </Tooltip>
              )}
              {this.state.canWitness && (
                <Tooltip title={i18n.t("common:actions.witness")} enterDelay={300}>
                  <IconButton
                    data-test-id="structured-witness"
                    color="inherit"
                    id="witnessDocument"
                    onClick={() => {
                      this.context.trackEvent("user:witness:document:document_editor");
                    }}
                  >
                    <FontAwesomeIcon icon={faEye} />
                  </IconButton>
                </Tooltip>
              )}
              {this.state.canShare && (
                <Tooltip title={i18n.t("common:actions.share")} enterDelay={300}>
                  <IconButton
                    data-cloud={this.state.isCloud}
                    data-test-id="notebooktoolbar-share"
                    color="inherit"
                    id="shareRecord"
                    onClick={() => {
                      this.context.trackEvent("user:share:document:document_editor");
                    }}
                  >
                    <FontAwesomeIcon icon={faShareAlt} />
                  </IconButton>
                </Tooltip>
              )}
              {(this.state.canDelete || this.state.canWitness || this.state.canSign) && (
                <Box
                  component="span"
                  sx={{
                    borderRight: "1px solid transparent",
                    margin: "0px 10px",
                    height: "100%",
                  }}
                ></Box>
              )}
              <Tooltip title={i18n.t("common:actions.export")} enterDelay={300}>
                <IconButton
                  data-test-id="notebooktoolbar-export"
                  color="inherit"
                  id="exportDocument"
                  onClick={() => {
                    this.props.eventHandlers.onExportDocument();
                    this.context.trackEvent("user:export:document:document_editor");
                  }}
                >
                  <FontAwesomeIcon icon={faCloudDownloadAlt} />
                </IconButton>
              </Tooltip>
              <PrintButton
                onClick={() => {
                  this.context.trackEvent("user:print:document:document_editor");
                }}
                data-test-id="notebooktoolbar-print"
              />
              <Box
                component="span"
                sx={{
                  borderRight: "1px solid transparent",
                  margin: "0px 10px",
                  height: "100%",
                }}
              ></Box>
            </span>
          )}
          {this.state.emptyDocrevision && (
            <Box
              component="span"
              data-test-id="notebooktoolbar-docversion"
              sx={{
                fontSize: "15px",
                alignItems: "center",
                display: "flex",
                justifyContent: "center",
                marginLeft: "10px",
              }}
            >
              {i18n.t("common:toolbar.displayingLockedVersion", { version: this.state.version })}
            </Box>
          )}
          <SocialActions
            onCreateRequest={this.props.eventHandlers.onCreateRequest}
            showExternal={true}
            sx={{ flexGrow: "1", justifyContent: "flex-end" }}
          />
        </Box>
        <ShareDialog />
      </>
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
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <Analytics>
            <BaseToolbar content={this.content()} />
          </Analytics>
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

StructuredDocumentToolbar.contextType = AnalyticsContext;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let rootNode: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let domContainer: any;

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

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
window.renderToolbar = (newProps: any) => {
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
    // content() uses i18n.t() directly, not the hook, so I18nRoot must gate this whole render or labels freeze as raw keys.
    <I18nRoot namespaces={["common"]} fallback={<Skeleton variant="rectangular" height={64} />}>
      <StructuredDocumentToolbar domContainer={domContainer} {...prevProps} canSign={prevProps.canSign} />
    </I18nRoot>,
  );
};

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched above.
 */
window.addEventListener("load", () => {
  const domContainer = document.getElementById("toolbar2");
  window.renderToolbar({
    canSign: domContainer?.getAttribute("data-can-sign") === "true",
  });
});
