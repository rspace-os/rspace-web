/* eslint-disable react/prop-types */
import { faCloudDownloadAlt } from "@fortawesome/free-solid-svg-icons/faCloudDownloadAlt";
import { faEdit } from "@fortawesome/free-solid-svg-icons/faEdit";
import { faEye } from "@fortawesome/free-solid-svg-icons/faEye";
import { faFileSignature } from "@fortawesome/free-solid-svg-icons/faFileSignature";
import { faShareAlt } from "@fortawesome/free-solid-svg-icons/faShareAlt";
import { faTimes } from "@fortawesome/free-solid-svg-icons/faTimes";
import { faTrashAlt } from "@fortawesome/free-solid-svg-icons/faTrashAlt";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Skeleton from "@mui/material/Skeleton";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import BaseToolbar from "../../components/BaseToolbar";
import ShareDialog from "../../components/ShareDialog";
import i18n from "../../modules/common/i18n";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import materialTheme from "../../theme";
import PrintButton from "../components/PrintButton";
import CreateMenu from "../ToolbarCreateMenu";
import SocialActions from "../ToolbarSocial";

declare global {
  interface Window {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    renderToolbar: (newProps?: any) => void;
  }
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class NotebookToolbar extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = {
      workspaceFolderId: props.domContainer.getAttribute("data-workspace-folder-id"),
      settingsKey: props.domContainer.getAttribute("data-settings-key"),
      canCreateRecord: props.domContainer.getAttribute("data-can-create-record") === "true",
      pioEnabled: props.domContainer.getAttribute("data-pio-enabled") === "true",
      evernoteEnabled: props.domContainer.getAttribute("data-evernote-enabled") === "true",
      asposeEnabled: props.domContainer.getAttribute("data-aspose-enabled") === "true",
      canDelete: props.domContainer.getAttribute("data-can-delete") === "true",
      canShare: props.domContainer.getAttribute("data-can-share") === "true",
    };
  }

  content = () => {
    return (
      <>
        <Box
          component="span"
          sx={{
            display: "flex",
            width: "100%",
            // Query container so the Create button's font-size (clamp with cqi)
            // tracks the toolbar's available width.
            containerType: "inline-size",
          }}
        >
          <Tooltip title={i18n.t("common:actions.back")} enterDelay={300}>
            <IconButton
              id="close"
              data-test-id="structured-document-back"
              href={`/workspace/${this.state.workspaceFolderId}?settingsKey=${this.state.settingsKey}`}
            >
              <FontAwesomeIcon icon={faTimes} />
            </IconButton>
          </Tooltip>
          <Box
            component="span"
            sx={{
              borderRight: "1px solid transparent",
              margin: "0px 10px",
              height: "100%",
            }}
          ></Box>
          {this.state.canCreateRecord && (
            <CreateMenu
              pioEnabled={this.state.pioEnabled}
              evernoteEnabled={this.state.evernoteEnabled}
              asposeEnabled={this.state.asposeEnabled}
            />
          )}
          <Tooltip title={i18n.t("common:actions.edit")} enterDelay={300}>
            <IconButton data-test-id="notebooktoolbar-edit" color="inherit" id="editEntry">
              <FontAwesomeIcon icon={faEdit} />
            </IconButton>
          </Tooltip>
          {this.state.canDelete && (
            <Tooltip title={i18n.t("common:actions.delete")} enterDelay={300}>
              <IconButton data-test-id="notebooktoolbar-delete" color="inherit" id="deleteEntry">
                <FontAwesomeIcon icon={faTrashAlt} />
              </IconButton>
            </Tooltip>
          )}
          <Tooltip title={i18n.t("common:actions.sign")} enterDelay={300}>
            <IconButton data-test-id="notebooktoolbar-sign" color="inherit" id="signDocument">
              <FontAwesomeIcon icon={faFileSignature} />
            </IconButton>
          </Tooltip>
          <Tooltip title={i18n.t("common:actions.witness")} enterDelay={300}>
            <IconButton data-test-id="notebooktoolbar-witness" color="inherit" id="witnessDocument">
              <FontAwesomeIcon icon={faEye} />
            </IconButton>
          </Tooltip>
          {this.state.canShare && (
            <Tooltip title={i18n.t("common:actions.share")} enterDelay={300}>
              <IconButton data-test-id="notebooktoolbar-share" color="inherit" id="shareRecord">
                <FontAwesomeIcon icon={faShareAlt} />
              </IconButton>
            </Tooltip>
          )}
          <Box
            component="span"
            sx={{
              borderRight: "1px solid transparent",
              margin: "0px 10px",
              height: "100%",
            }}
          ></Box>
          {this.props.conditionalRender.export && (
            <Tooltip title={i18n.t("common:actions.export")} enterDelay={300}>
              <IconButton
                data-test-id="notebooktoolbar-export"
                color="inherit"
                id="exportDocument"
                onClick={this.props.eventHandlers.onExportDocument}
              >
                <FontAwesomeIcon icon={faCloudDownloadAlt} />
              </IconButton>
            </Tooltip>
          )}
          {this.props.conditionalRender.print && <PrintButton data-test-id="notebooktoolbar-print" />}
          <SocialActions
            showExternal={true}
            sx={{ flexGrow: "1", justifyContent: "flex-end" }}
            onCreateRequest={this.props.eventHandlers.onCreateRequest}
          />
        </Box>
        <ShareDialog />
      </>
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
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <BaseToolbar content={this.content()} />
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let rootNode: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let domContainer: any;

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
  };
  rootNode.render(
    // `content()` above resolves its text via `i18n.t()` directly rather than
    // `useTranslation`, so it never re-renders once the namespace arrives —
    // `I18nRoot` must gate NotebookToolbar's whole first render, not just its
    // presentational output, or the toolbar freezes with raw i18n keys.
    <I18nRoot namespaces={["common"]} fallback={<Skeleton variant="rectangular" height={64} />}>
      <NotebookToolbar domContainer={domContainer} {...prevProps} />
    </I18nRoot>,
  );
};

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched above.
 */
window.addEventListener("load", () => {
  window.renderToolbar();
});
