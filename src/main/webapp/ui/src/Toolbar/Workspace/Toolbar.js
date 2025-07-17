"use strict";
import React from "react";
import { createRoot } from "react-dom/client";
import update from "immutability-helper";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../theme";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import MenuItem from "@mui/material/MenuItem";
import Menu from "@mui/material/Menu";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCalendarAlt,
  faList,
  faFolder,
  faStar,
  faShareAlt,
  faUsers,
  faFile,
  faBook,
  faFolderOpen,
  faThList,
  faStream,
} from "@fortawesome/free-solid-svg-icons";
library.add(
  faCalendarAlt,
  faList,
  faFolder,
  faStar,
  faShareAlt,
  faUsers,
  faFile,
  faBook,
  faFolderOpen,
  faThList,
  faStream,
);
import TagDialog from "./TagDialog";
import CompareDialog from "./CompareDialog";
import RenameDialog from "./RenameDialog";

import BaseToolbar from "../../components/BaseToolbar";
import TreeSort from "../../components/TreeSort";
import CreateMenu from "../ToolbarCreateMenu";
import SocialActions from "../ToolbarSocial";
import AdvancedSearch from "./AdvancedSearch/AdvancedSearch";
import SimpleSearch from "./SimpleSearch/SimpleSearch";
import Analytics from "../../components/Analytics";

class WorkspaceToolbar extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      open: [false, false],
      anchorEl: [null, null],
      advancedOpen: false,
      hideIcons: false,
      treeView: workspaceSettings.currentViewMode == "TREE_VIEW",
      sharedFilter: workspaceSettings.sharedFilter,
      favoritesFilter: workspaceSettings.favoritesFilter,
      templatesFilter: workspaceSettings.templatesFilter,
      viewableItemsFilter: workspaceSettings.viewableItemsFilter,
      pioEnabled:
        props.domContainer.getAttribute("data-pio-enabled") === "true",
      ontologiesFilter: workspaceSettings.ontologiesFilter,
      evernoteEnabled:
        props.domContainer.getAttribute("data-evernote-enabled") === "true",
      asposeEnabled:
        props.domContainer.getAttribute("data-aspose-enabled") === "true",
      labgroupsFolderId: props.domContainer.getAttribute(
        "data-labgroups-folder-id",
      ),
    };

    this.simpleSearch = React.createRef();
    this.advancedSearch = React.createRef();
  }

  componentDidMount() {
    this.checkSavedSettings();

    // Bad practise. Change when the reset button is in React
    let toolbar = this;
    $(document).on("click", "#resetSearch", function () {
      abandonSearch();
      toolbar.setState({
        sharedFilter: workspaceSettings.sharedFilter,
        favoritesFilter: workspaceSettings.favoritesFilter,
        templatesFilter: workspaceSettings.templatesFilter,
        advancedOpen: false,
        hideIcons: false,
      });
    });

    // Sets up callback function so that regular JS listeners can reset the toolbar after navigating to a folder for e.g.
    resetToolbar = function () {
      workspaceSettings.sharedFilter = false;
      workspaceSettings.favoritesFilter = false;
      workspaceSettings.templatesFilter = false;
      resetSearch();
      toolbar.setState({
        sharedFilter: workspaceSettings.sharedFilter,
        favoritesFilter: workspaceSettings.favoritesFilter,
        templatesFilter: workspaceSettings.templatesFilter,
        advancedOpen: false,
        hideIcons: false,
      });
    };

    /*
     * This event tells the jQuery parts of the code that they can assume that
     * the dom nodes managed by react have now been added to the dom.
     */
    setTimeout(() => {
      window.dispatchEvent(new CustomEvent("ReactToolbarMounted"));
    }, 0);
  }

  // When you search => open a document => go back
  // This function repopulates the searches with saved queries
  checkSavedSettings = () => {
    let localQueries = [],
      term,
      filter,
      from,
      to;

    workspaceSettings.options.map((_, idx) => {
      filter = workspaceSettings.options[idx];
      term = workspaceSettings.terms[idx];

      if (["created", "lastModified"].includes(filter)) {
        [from, to] = term.split("; ");
      } else if (["records"].includes(filter)) {
        term = term.split("; ");
      }

      localQueries.push({
        filter:
          filter === "global" && workspaceSettings.options.length > 1
            ? "fullText"
            : filter,
        term: term,
        from: from === "null" ? null : from,
        to: to === "null" ? null : to,
      });
    });

    if (localQueries.length > 1) {
      this.setState({ advancedOpen: true }, () =>
        this.advancedSearch.current.setQueries(localQueries),
      );
    } else if (localQueries.length === 1) {
      this.simpleSearch.current.setQueries(localQueries);

      if (localQueries[0].filter !== "global") {
        this.handleHideIcons(true);
      }
    }
  };

  handleOpen = (idx, event) => {
    this.setState({
      open: update(this.state.open, {
        [idx]: { $set: true },
      }),
      anchorEl: update(this.state.anchorEl, {
        [idx]: { $set: event.currentTarget },
      }),
    });
  };

  handleClose = (idx) => {
    this.setState({
      open: update(this.state.open, {
        [idx]: { $set: false },
      }),
    });
  };

  toggleAdvanced = (filter, term, from, to) => {
    if (this.state.advancedOpen) {
      this.setState({ advancedOpen: false });
    } else {
      let queries = [];

      if (filter == "global") {
        queries.push({ filter: "fullText", term: term });
      } else {
        queries.push({ filter: filter, term: term, from: from, to: to });
      }

      this.setState({ advancedOpen: true }, () =>
        this.advancedSearch.current.setQueries(queries),
      );
    }
  };

  handleHideIcons = (state) => {
    this.setState({ hideIcons: state });
  };

  openTreeView = () => {
    tree_view();
    this.setState({ treeView: true });
    this.handleClose(0);
    RS.trackEvent("user:view:tree:workspace");
  };

  openListView = () => {
    list_view();
    this.setState({ treeView: false });
    this.handleClose(0);
    RS.trackEvent("user:view:list:workspace");
  };

  openFolderView = () => {
    this.setState({ viewableItemsFilter: false }, this.displayWorkspace);
    this.handleClose(1);
    RS.trackEvent("user:view:folder:workspace");
  };

  openViewAll = () => {
    this.setState({ viewableItemsFilter: true }, this.displayWorkspace);
    this.handleClose(1);
    RS.trackEvent("user:view:all:workspace");
  };

  toggleFilter = (filter) => {
    this.setState(
      {
        [filter]: !this.state[filter],
      },
      this.displayWorkspace,
    );
  };

  displayLabgroup = () => {
    const callback = () => {
      this.setWorkspaceSettings();
      getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
      RS.trackEvent("user:view:labgroup:workspace");
    };

    this.setWorkspaceSettingsUrl();

    if (this.state.viewableItemsFilter) {
      this.setState(
        {
          templatesFilter: false,
          favoritesFilter: false,
          sharedFilter: false,
          viewableItemsFilter: false,
          treeView: false,
        },
        callback,
      );
    } else {
      workspaceSettings.searchMode = false;
      this.setState(
        {
          templatesFilter: false,
          favoritesFilter: false,
          sharedFilter: false,
        },
        callback,
      );
    }
  };

  displayWorkspace = () => {
    this.setWorkspaceSettings();
    let url = "/workspace/ajax/view/" + workspaceSettings.parentFolderId;

    if (workspaceSettings.searchMode) {
      doWorkspaceSearch(workspaceSettings.url, workspaceSettings);
    } else {
      getAndDisplayWorkspaceResults(url, workspaceSettings);
    }
  };

  setWorkspaceSettingsUrl = () => {
    workspaceSettings.parentFolderId = this.state.labgroupsFolderId;
    workspaceSettings.grandparentFolderId = null;
    workspaceSettings.url =
      "/workspace/ajax/view/" + this.state.labgroupsFolderId;
  };

  setWorkspaceSettings = () => {
    workspaceSettings.currentViewMode = this.state.treeView
      ? "TREE_VIEW"
      : "LIST_VIEW";
    workspaceSettings.sharedFilter = this.state.sharedFilter;
    workspaceSettings.favoritesFilter = this.state.favoritesFilter;
    workspaceSettings.templatesFilter = this.state.templatesFilter;
    workspaceSettings.viewableItemsFilter = this.state.viewableItemsFilter;
    workspaceSettings.ontologiesFilter = this.state.ontologiesFilter;
  };

  content = () => {
    return (
      <span style={{ display: "flex", width: "100%" }}>
        <CreateMenu
          pioEnabled={this.state.pioEnabled}
          evernoteEnabled={this.state.evernoteEnabled}
          asposeEnabled={this.state.asposeEnabled}
        />
        {!this.state.hideIcons && (
          <span style={{ display: "flex" }}>
            <SocialActions
              onCreateRequest={this.props.eventHandlers.onCreateRequest}
            />
            <Tooltip title="Create a calendar entry" enterDelay={300}>
              <IconButton
                id="createCalendarEntryDlgLink"
                color="inherit"
                data-test-id="toolbar-calendar"
                aria-label="Create a calendar entry"
              >
                <FontAwesomeIcon icon="calendar-alt" />
              </IconButton>
            </Tooltip>
          </span>
        )}
        <span
          style={{
            borderRight: "1px solid transparent",
            margin: "0px 10px",
            height: "100%",
          }}
        ></span>
        <Tooltip title="View mode" enterDelay={300}>
          <IconButton
            data-test-id="toolbar-views"
            aria-controls="simple-menu2"
            aria-haspopup="true"
            onClick={(e) => this.handleOpen(0, e)}
            color="inherit"
            aria-label="View mode"
          >
            <FontAwesomeIcon icon={this.state.treeView ? "stream" : "list"} />
          </IconButton>
        </Tooltip>
        <Menu
          id="simple-menu2"
          anchorOrigin={{
            vertical: "bottom",
            horizontal: "left",
          }}
          anchorEl={this.state.anchorEl[0]}
          keepMounted
          open={this.state.open[0]}
          onClose={() => this.handleClose(0)}
        >
          <MenuItem
            onClick={this.openTreeView}
            data-test-id="toolbar-view-tree"
            aria-label="Tree view"
          >
            <FontAwesomeIcon icon="stream" style={{ paddingRight: "10px" }} />
            Tree view
          </MenuItem>
          <MenuItem
            onClick={this.openListView}
            id="list_view_1"
            data-test-id="toolbar-view-list"
            aria-label="List view"
          >
            <FontAwesomeIcon icon="list" style={{ paddingRight: "10px" }} />
            List view
          </MenuItem>
        </Menu>
        {this.state.treeView && <TreeSort />}
        {!this.state.treeView && (
          <span style={{ display: "flex", flexGrow: "1" }}>
            <Tooltip title="View mode" enterDelay={300}>
              <IconButton
                data-test-id="toolbar-views-2"
                aria-controls="simple-menu3"
                aria-haspopup="true"
                onClick={(e) => this.handleOpen(1, e)}
                color="inherit"
                aria-label="View mode"
              >
                <FontAwesomeIcon
                  icon={
                    this.state.viewableItemsFilter ? "th-list" : "folder-open"
                  }
                />
              </IconButton>
            </Tooltip>
            <Menu
              id="simple-menu3"
              anchorOrigin={{
                vertical: "bottom",
                horizontal: "left",
              }}
              anchorEl={this.state.anchorEl[1]}
              keepMounted
              open={this.state.open[1]}
              onClose={() => this.handleClose(1)}
            >
              <MenuItem
                onClick={this.openFolderView}
                id="folderView_1"
                data-test-id="toolbar-view-folders"
              >
                <FontAwesomeIcon
                  icon="folder-open"
                  style={{ paddingRight: "10px" }}
                />
                Folder view
              </MenuItem>
              <MenuItem
                onClick={this.openViewAll}
                id="viewableItemsView_1"
                data-test-id="toolbar-view-all"
              >
                <FontAwesomeIcon
                  icon="th-list"
                  style={{ paddingRight: "10px" }}
                />
                View all
              </MenuItem>
            </Menu>
            <Tooltip title="Labgroup records" enterDelay={300}>
              <IconButton
                onClick={this.displayLabgroup}
                id="labgroupFilter_1"
                color="inherit"
                data-test-id="toolbar-filter-labgroup"
                aria-label="Labgroup records"
              >
                <FontAwesomeIcon icon="users" />
              </IconButton>
            </Tooltip>
            <span
              style={{
                borderRight: "1px solid transparent",
                margin: "0px 10px",
                height: "100%",
              }}
            ></span>
            <Tooltip title="Favorites" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("favoritesFilter");
                  RS.trackEvent("user:filter:favorites:workspace");
                }}
                id="favoritesFilter_1"
                color={this.state.favoritesFilter ? "default" : "inherit"}
                className={this.state.favoritesFilter ? "active" : ""}
                data-test-id="toolbar-filter-favorites"
                aria-label="Favorites"
              >
                <FontAwesomeIcon icon="star" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Shared with me" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("sharedFilter");
                  RS.trackEvent("user:filter:shared_with_me:workspace");
                }}
                id="sharedFilter_1"
                color={this.state.sharedFilter ? "default" : "inherit"}
                className={this.state.sharedFilter ? "active" : ""}
                data-test-id="toolbar-filter-shared"
                aria-label="Shared with me"
              >
                <FontAwesomeIcon icon="share-alt" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Templates" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("templatesFilter");
                  RS.trackEvent("user:filter:templates:workspace");
                }}
                id="templatesFilter_1"
                color={this.state.templatesFilter ? "default" : "inherit"}
                className={this.state.templatesFilter ? "active" : ""}
                data-test-id="toolbar-filter-templates"
                aria-label="Templates"
              >
                <FontAwesomeIcon icon="folder" />
                <span
                  style={{
                    position: "absolute",
                    color: "#00adef",
                    fontSize: "13px",
                    fontWeight: "bold",
                    marginTop: "1px",
                  }}
                >
                  T
                </span>
              </IconButton>
            </Tooltip>
            <Tooltip title="Ontology files" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("ontologiesFilter");
                  RS.trackEvent("user:filter:ontologies:workspace");
                }}
                id="ontologiesFilter_1"
                color={this.state.ontologiesFilter ? "default" : "inherit"}
                className={this.state.ontologiesFilter ? "active" : ""}
                data-test-id="toolbar-filter-ontology"
                aria-label="Ontology files"
              >
                <FontAwesomeIcon icon="folder" />
                <span
                  style={{
                    position: "absolute",
                    color: "#00adef",
                    fontSize: "13px",
                    fontWeight: "bold",
                    marginTop: "1px",
                  }}
                >
                  O
                </span>
              </IconButton>
            </Tooltip>
            <span
              style={{
                borderRight: "1px solid transparent",
                margin: "0px 10px",
                height: "100%",
              }}
            ></span>
            <SimpleSearch
              ref={this.simpleSearch}
              toggleAdvanced={this.toggleAdvanced}
              advancedOpen={this.state.advancedOpen}
              hideIcons={this.handleHideIcons}
            />
          </span>
        )}
      </span>
    );
  };

  render() {
    return (
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <Analytics>
            <BaseToolbar content={this.content()} />
            {this.state.advancedOpen && (
              <AdvancedSearch ref={this.advancedSearch} />
            )}
            <TagDialog />
            <CompareDialog />
            <RenameDialog />
          </Analytics>
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
    <WorkspaceToolbar domContainer={domContainer} {...prevProps} />,
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
