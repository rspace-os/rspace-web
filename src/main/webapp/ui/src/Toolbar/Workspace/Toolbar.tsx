import { faFolder } from "@fortawesome/free-solid-svg-icons/faFolder";
import { faFolderOpen } from "@fortawesome/free-solid-svg-icons/faFolderOpen";
import { faList } from "@fortawesome/free-solid-svg-icons/faList";
import { faShareAlt } from "@fortawesome/free-solid-svg-icons/faShareAlt";
import { faStar } from "@fortawesome/free-solid-svg-icons/faStar";
import { faStream } from "@fortawesome/free-solid-svg-icons/faStream";
import { faThList } from "@fortawesome/free-solid-svg-icons/faThList";
import { faUsers } from "@fortawesome/free-solid-svg-icons/faUsers";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { produce } from "immer";
import React from "react";
import { createRoot } from "react-dom/client";
import Analytics from "../../components/Analytics";
import BaseToolbar from "../../components/BaseToolbar";
import ShareDialog from "../../components/ShareDialog";
import TreeSort from "../../components/TreeSort";
import AnalyticsContext from "../../stores/contexts/Analytics";
import materialTheme from "../../theme";
import CreateMenu from "../ToolbarCreateMenu";
import SocialActions from "../ToolbarSocial";
import AdvancedSearch from "./AdvancedSearch/AdvancedSearch";
import CompareDialog from "./CompareDialog";
import RenameDialog from "./RenameDialog";
import SimpleSearch from "./SimpleSearch/SimpleSearch";
import TagDialog from "./TagDialog";

type WorkspaceToolbarContext = React.ContextType<typeof AnalyticsContext>;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const workspaceSettings: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const $: any;
declare function abandonSearch(): void;
declare function resetSearch(): void;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare let resetToolbar: any;
declare function tree_view(): void;
declare function list_view(): void;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function doWorkspaceSearch(url: string, settings: any): void;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function getAndDisplayWorkspaceResults(url: string, settings: any): void;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class WorkspaceToolbar extends React.Component<any, any> {
  declare context: WorkspaceToolbarContext;
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  simpleSearch: any;
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  advancedSearch: any;

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = {
      open: [false, false],
      anchorEl: [null, null],
      advancedOpen: false,
      hideIcons: false,
      treeView: workspaceSettings.currentViewMode === "TREE_VIEW",
      sharedFilter: workspaceSettings.sharedFilter,
      favoritesFilter: workspaceSettings.favoritesFilter,
      templatesFilter: workspaceSettings.templatesFilter,
      viewableItemsFilter: workspaceSettings.viewableItemsFilter,
      pioEnabled: props.domContainer.getAttribute("data-pio-enabled") === "true",
      ontologiesFilter: workspaceSettings.ontologiesFilter,
      evernoteEnabled: props.domContainer.getAttribute("data-evernote-enabled") === "true",
      asposeEnabled: props.domContainer.getAttribute("data-aspose-enabled") === "true",
      labgroupsFolderId: props.domContainer.getAttribute("data-labgroups-folder-id"),
    };

    this.simpleSearch = React.createRef();
    this.advancedSearch = React.createRef();
  }

  componentDidMount() {
    this.checkSavedSettings();

    $(document).on("click", "#resetSearch", () => {
      abandonSearch();
      this.setState({
        sharedFilter: workspaceSettings.sharedFilter,
        favoritesFilter: workspaceSettings.favoritesFilter,
        templatesFilter: workspaceSettings.templatesFilter,
        advancedOpen: false,
        hideIcons: false,
      });
    });

    // Sets up callback function so that regular JS listeners can reset the toolbar after navigating to a folder for e.g.
    resetToolbar = () => {
      workspaceSettings.sharedFilter = false;
      workspaceSettings.favoritesFilter = false;
      workspaceSettings.templatesFilter = false;
      resetSearch();
      this.setState({
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
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    let localQueries: any[] = [],
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      term: any,
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      filter: any,
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      from: any,
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      to: any;

    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    workspaceSettings.options.map((_: any, idx: any) => {
      filter = workspaceSettings.options[idx];
      term = workspaceSettings.terms[idx];

      if (["created", "lastModified"].includes(filter)) {
        [from, to] = term.split("; ");
      } else if (["records"].includes(filter)) {
        term = term.split("; ");
      }

      localQueries.push({
        filter: filter === "global" && workspaceSettings.options.length > 1 ? "fullText" : filter,
        term,
        from: from === "null" ? null : from,
        to: to === "null" ? null : to,
      });
    });

    if (localQueries.length > 1) {
      this.setState({ advancedOpen: true }, () => this.advancedSearch.current.setQueries(localQueries));
    } else if (localQueries.length === 1) {
      this.simpleSearch.current.setQueries(localQueries);

      if (localQueries[0].filter !== "global") {
        this.handleHideIcons(true);
      }
    }
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleOpen = (idx: any, event: any) => {
    // Capture the anchor element synchronously, because by the time the
    // setState updater runs, React may have nulled out `event.currentTarget`
    // (it is only valid during the synchronous event handler). If we read it
    // inside the updater we can end up with a null anchor, which causes the
    // Menu to render in the top-left corner of the viewport rather than next
    // to the button that opened it.
    const anchor = event.currentTarget;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      open: produce(prevState.open, (draft: any) => {
        draft[idx] = true;
      }),
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      anchorEl: produce(prevState.anchorEl, (draft: any) => {
        draft[idx] = anchor;
      }),
    }));
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleClose = (idx: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      open: produce(prevState.open, (draft: any) => {
        draft[idx] = false;
      }),
    }));
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  toggleAdvanced = (filter: any, term: any, from: any, to: any) => {
    if (this.state.advancedOpen) {
      this.setState({ advancedOpen: false });
    } else {
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      const queries: any[] = [];

      if (filter === "global") {
        queries.push({ filter: "fullText", term });
      } else {
        queries.push({ filter, term, from, to });
      }

      this.setState({ advancedOpen: true }, () => this.advancedSearch.current.setQueries(queries));
    }
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleHideIcons = (state: any) => {
    this.setState({ hideIcons: state });
  };

  openTreeView = () => {
    tree_view();
    this.setState({ treeView: true });
    this.handleClose(0);
    this.context.trackEvent("user:view:tree:workspace");
  };

  openListView = () => {
    list_view();
    this.setState({ treeView: false });
    this.handleClose(0);
    this.context.trackEvent("user:view:list:workspace");
  };

  openFolderView = () => {
    this.setState({ viewableItemsFilter: false }, this.displayWorkspace);
    this.handleClose(1);
    this.context.trackEvent("user:view:folder:workspace");
  };

  openViewAll = () => {
    this.setState({ viewableItemsFilter: true }, this.displayWorkspace);
    this.handleClose(1);
    this.context.trackEvent("user:view:all:workspace");
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  toggleFilter = (filter: any) => {
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
      this.context.trackEvent("user:view:labgroup:workspace");
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
    const url = `/workspace/ajax/view/${workspaceSettings.parentFolderId}`;

    if (workspaceSettings.searchMode) {
      doWorkspaceSearch(workspaceSettings.url, workspaceSettings);
    } else {
      getAndDisplayWorkspaceResults(url, workspaceSettings);
    }
  };

  setWorkspaceSettingsUrl = () => {
    workspaceSettings.parentFolderId = this.state.labgroupsFolderId;
    workspaceSettings.grandparentFolderId = null;
    workspaceSettings.url = `/workspace/ajax/view/${this.state.labgroupsFolderId}`;
  };

  setWorkspaceSettings = () => {
    workspaceSettings.currentViewMode = this.state.treeView ? "TREE_VIEW" : "LIST_VIEW";
    workspaceSettings.sharedFilter = this.state.sharedFilter;
    workspaceSettings.favoritesFilter = this.state.favoritesFilter;
    workspaceSettings.templatesFilter = this.state.templatesFilter;
    workspaceSettings.viewableItemsFilter = this.state.viewableItemsFilter;
    workspaceSettings.ontologiesFilter = this.state.ontologiesFilter;
  };

  content = () => {
    return (
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
        <CreateMenu
          pioEnabled={this.state.pioEnabled}
          evernoteEnabled={this.state.evernoteEnabled}
          asposeEnabled={this.state.asposeEnabled}
        />
        {!this.state.hideIcons && (
          <Box component="span" sx={{ display: "flex" }}>
            <SocialActions onCreateRequest={this.props.eventHandlers.onCreateRequest} />
          </Box>
        )}
        <Box
          component="span"
          sx={{
            borderRight: "1px solid transparent",
            height: "100%",
          }}
        ></Box>
        <Tooltip title="View mode" enterDelay={300}>
          <IconButton
            data-test-id="toolbar-views"
            aria-controls="simple-menu2"
            aria-haspopup="true"
            onClick={(e) => this.handleOpen(0, e)}
            color="inherit"
            aria-label="View mode"
          >
            {this.state.treeView ? <FontAwesomeIcon icon={faStream} /> : <FontAwesomeIcon icon={faList} />}
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
          <MenuItem onClick={this.openTreeView} data-test-id="toolbar-view-tree" aria-label="Tree view">
            <FontAwesomeIcon icon={faStream} style={{ paddingRight: "10px" }} />
            Tree view
          </MenuItem>
          <MenuItem
            onClick={this.openListView}
            id="list_view_1"
            data-test-id="toolbar-view-list"
            aria-label="List view"
          >
            <FontAwesomeIcon icon={faList} style={{ paddingRight: "10px" }} />
            List view
          </MenuItem>
        </Menu>
        {this.state.treeView && <TreeSort />}
        {!this.state.treeView && (
          <Box component="span" sx={{ display: "flex", flexGrow: "1" }}>
            <Tooltip title="View mode" enterDelay={300}>
              <IconButton
                data-test-id="toolbar-views-2"
                aria-controls="simple-menu3"
                aria-haspopup="true"
                onClick={(e) => this.handleOpen(1, e)}
                color="inherit"
                aria-label="View mode"
              >
                {this.state.viewableItemsFilter ? (
                  <FontAwesomeIcon icon={faThList} />
                ) : (
                  <FontAwesomeIcon icon={faFolderOpen} />
                )}
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
              <MenuItem onClick={this.openFolderView} id="folderView_1" data-test-id="toolbar-view-folders">
                <FontAwesomeIcon icon={faFolderOpen} style={{ paddingRight: "10px" }} />
                Folder view
              </MenuItem>
              <MenuItem onClick={this.openViewAll} id="viewableItemsView_1" data-test-id="toolbar-view-all">
                <FontAwesomeIcon icon={faThList} style={{ paddingRight: "10px" }} />
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
                <FontAwesomeIcon icon={faUsers} />
              </IconButton>
            </Tooltip>
            <Box
              component="span"
              sx={{
                borderRight: "1px solid transparent",
                height: "100%",
              }}
            ></Box>
            <Tooltip title="Favorites" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("favoritesFilter");
                  this.context.trackEvent("user:filter:favorites:workspace");
                }}
                id="favoritesFilter_1"
                color={this.state.favoritesFilter ? "default" : "inherit"}
                className={this.state.favoritesFilter ? "active" : ""}
                data-test-id="toolbar-filter-favorites"
                aria-label="Favorites"
              >
                <FontAwesomeIcon icon={faStar} />
              </IconButton>
            </Tooltip>
            <Tooltip title="Shared with me" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("sharedFilter");
                  this.context.trackEvent("user:filter:shared_with_me:workspace");
                }}
                id="sharedFilter_1"
                color={this.state.sharedFilter ? "default" : "inherit"}
                className={this.state.sharedFilter ? "active" : ""}
                data-test-id="toolbar-filter-shared"
                aria-label="Shared with me"
              >
                <FontAwesomeIcon icon={faShareAlt} />
              </IconButton>
            </Tooltip>
            <Tooltip title="Templates" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("templatesFilter");
                  this.context.trackEvent("user:filter:templates:workspace");
                }}
                id="templatesFilter_1"
                color={this.state.templatesFilter ? "default" : "inherit"}
                className={this.state.templatesFilter ? "active" : ""}
                data-test-id="toolbar-filter-templates"
                aria-label="Templates"
              >
                <FontAwesomeIcon icon={faFolder} />
                <Box
                  component="span"
                  sx={{
                    position: "absolute",
                    color: "#00adef",
                    fontSize: "13px",
                    fontWeight: "bold",
                    marginTop: "1px",
                  }}
                >
                  T
                </Box>
              </IconButton>
            </Tooltip>
            <Tooltip title="Ontology files" enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("ontologiesFilter");
                  this.context.trackEvent("user:filter:ontologies:workspace");
                }}
                id="ontologiesFilter_1"
                color={this.state.ontologiesFilter ? "default" : "inherit"}
                className={this.state.ontologiesFilter ? "active" : ""}
                data-test-id="toolbar-filter-ontology"
                aria-label="Ontology files"
              >
                <FontAwesomeIcon icon={faFolder} />
                <Box
                  component="span"
                  sx={{
                    position: "absolute",
                    color: "#00adef",
                    fontSize: "13px",
                    fontWeight: "bold",
                    marginTop: "1px",
                  }}
                >
                  O
                </Box>
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
            <SimpleSearch
              ref={this.simpleSearch}
              toggleAdvanced={this.toggleAdvanced}
              advancedOpen={this.state.advancedOpen}
              hideIcons={this.handleHideIcons}
            />
          </Box>
        )}
      </Box>
    );
  };

  render() {
    return (
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <Analytics>
            <BaseToolbar content={this.content()} />
            {this.state.advancedOpen && <AdvancedSearch ref={this.advancedSearch} />}
            <TagDialog />
            <CompareDialog />
            <RenameDialog />
            <ShareDialog />
          </Analytics>
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

WorkspaceToolbar.contextType = AnalyticsContext;

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
  },
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
(window as any).renderToolbar = (newProps: any) => {
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
  rootNode.render(<WorkspaceToolbar domContainer={domContainer} {...prevProps} />);
};

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched above.
 */
window.addEventListener("load", () => {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  (window as any).renderToolbar();
});
