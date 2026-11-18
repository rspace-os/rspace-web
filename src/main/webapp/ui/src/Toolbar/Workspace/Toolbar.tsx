// @ts-nocheck — legacy class component restored by revert; global JS vars not typed
import { faCalendarAlt } from "@fortawesome/free-regular-svg-icons/faCalendarAlt";
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
import Analytics from "../../components/Analytics";
import BaseToolbar from "../../components/BaseToolbar";
import ShareDialog from "../../components/ShareDialog";
import TreeSort from "../../components/TreeSort";
import i18n from "../../modules/common/i18n";
import AnalyticsContext from "../../stores/contexts/Analytics";
import materialTheme from "../../theme";
import CreateMenu from "../ToolbarCreateMenu";
import SocialActions from "../ToolbarSocial";
import type { ToolbarConditionalRender, ToolbarEventHandlers } from "../types";
import AdvancedSearch from "./AdvancedSearch/AdvancedSearch";
import CompareDialog from "./CompareDialog";
import { installAddressBarSync } from "./DefaultSearch/addressBarSync";
import DefaultSearch from "./DefaultSearch/DefaultSearch";
import { searchLinkFailureReason } from "./DefaultSearch/searchLinkFailure";
import { RECORD_LIST_SHOWN, VIEW_MODE_BUTTON_ID } from "./DefaultSearch/workspaceGlobals";
import { parseSearchQuery } from "./DefaultSearch/workspaceSearchUrl";
import RenameDialog from "./RenameDialog";
import SimpleSearch from "./SimpleSearch/SimpleSearch";
import TagDialog from "./TagDialog";

export type WorkspaceToolbarProps = {
  domContainer: HTMLElement;
  conditionalRender: Partial<ToolbarConditionalRender>;
  eventHandlers: Pick<ToolbarEventHandlers, "onCreateRequest">;
};

class WorkspaceToolbar extends React.Component<WorkspaceToolbarProps> {
  constructor(props: WorkspaceToolbarProps) {
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
      asposeEnabled: props.domContainer.getAttribute("data-aspose-enabled") === "true",
      labgroupsFolderId: props.domContainer.getAttribute("data-labgroups-folder-id"),
    };

    this.simpleSearch = React.createRef();
    this.advancedSearch = React.createRef();
  }

  componentDidMount() {
    installAddressBarSync();
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

    if (workspaceSettings.options.length > 0) {
      this.resumeRestoredSearch();
    } else {
      this.restoreSearchFromLocation();
    }

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
    let localQueries: Array<object> = [],
      term: string | string[] = "",
      filter: string = "",
      from: string | undefined,
      to: string | undefined;

    workspaceSettings.options.forEach((_, idx) => {
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

  // The server rendered the results of a search kept in the session, after the user closed a document
  resumeRestoredSearch = () => {
    endSearchLinkWait();
    workspaceSettings.searchMode = true;
    workspaceSettings.url = "/workspace/ajax/search";
    updateSearchModePanel($("#noOfRows").val() > 0);
    window.dispatchEvent(new CustomEvent(RECORD_LIST_SHOWN));
  };

  // Runs the search held in the address bar, for a bookmarked or shared search link
  restoreSearchFromLocation = () => {
    const parsed = parseSearchQuery(window.location.search);
    if (parsed.kind === "none") {
      endSearchLinkWait();
      return;
    }
    if (parsed.kind === "invalid") {
      this.showFolderInsteadOfSearch();
      return;
    }

    const { state } = parsed;
    const runSearch = () => {
      Object.assign(workspaceSettings, {
        options: [...state.options],
        terms: [...state.terms],
        advancedSearch: state.advancedSearch,
        operator: state.operator,
        sharedFilter: state.sharedFilter,
        favoritesFilter: state.favoritesFilter,
        viewableItemsFilter: state.viewableItemsFilter,
        templatesFilter: state.templatesFilter,
        ontologiesFilter: state.ontologiesFilter,
        orderBy: state.orderBy,
        sortOrder: state.sortOrder,
        searchMode: true,
        pageNumber: 0,
        url: "/workspace/ajax/search",
      });
      this.setState(
        {
          sharedFilter: state.sharedFilter,
          favoritesFilter: state.favoritesFilter,
          viewableItemsFilter: state.viewableItemsFilter,
          templatesFilter: state.templatesFilter,
          ontologiesFilter: state.ontologiesFilter,
        },
        () => {
          this.checkSavedSettings();
          searchLinkSearchStarted();
          doWorkspaceSearch(workspaceSettings.url, workspaceSettings, this.showFolderInsteadOfSearch, () => {
            // The results are drawn after a short fade, which then sends this event
            window.addEventListener(RECORD_LIST_SHOWN, endSearchLinkWait, { once: true });
            if (this.searchLinkLeftTreeView) {
              this.searchLinkLeftTreeView = false;
              saveWorkplaceSettings();
            }
          });
        },
      );
    };

    // The search box only exists in list view. The list is saved as the
    // user's view only once the search has worked.
    if (this.state.treeView) {
      this.searchLinkLeftTreeView = true;
      // The server keeps the search with this view, for returning from a result
      workspaceSettings.currentViewMode = "LIST_VIEW";
      showListView();
      this.setState({ treeView: false }, runSearch);
    } else {
      runSearch();
    }
  };

  showFolderInsteadOfSearch = (jqxhr) => {
    endSearchLinkWait();
    if (this.searchLinkLeftTreeView) {
      this.searchLinkLeftTreeView = false;
      workspaceSettings.currentViewMode = "TREE_VIEW";
      showTreeView();
      this.setState({ treeView: true });
    }
    // resetToolbar() leaves these two filters as they are
    workspaceSettings.viewableItemsFilter = false;
    workspaceSettings.ontologiesFilter = false;
    this.setState({ viewableItemsFilter: false, ontologiesFilter: false });
    resetToolbar();
    this.simpleSearch.current?.resetState();
    if (jqxhr?.status === 0) {
      // List the folder again on paging and sorting, as abandonSearch() does, without
      // reloading it now. Keep the link in the address bar, so that reloading tries it again
      workspaceSettings.parentFolderId = $("#currFolderId").val();
      workspaceSettings.url = `/workspace/ajax/view/${workspaceSettings.parentFolderId}`;
      workspaceSettings.pageNumber = 0;
      RS.confirm(i18n.t("workspace:toolbar.defaultSearch.linkNetworkError"), "warning", 5000);
      return;
    }
    abandonSearch();
    const reason = jqxhr ? searchLinkFailureReason(jqxhr.responseText) : "";
    RS.confirm(
      reason
        ? i18n.t("workspace:toolbar.defaultSearch.linkFailedReason", {
            reason: RS.escapeHtml(reason),
          })
        : i18n.t("workspace:toolbar.defaultSearch.invalidSearch"),
      "warning",
      5000,
    );
  };

  handleOpen = (idx, event) => {
    // Capture the anchor element synchronously, because by the time the
    // setState updater runs, React may have nulled out `event.currentTarget`
    // (it is only valid during the synchronous event handler). If we read it
    // inside the updater we can end up with a null anchor, which causes the
    // Menu to render in the top-left corner of the viewport rather than next
    // to the button that opened it.
    const anchor = event.currentTarget;
    this.setState((prevState) => ({
      open: produce(prevState.open, (draft) => {
        draft[idx] = true;
      }),
      anchorEl: produce(prevState.anchorEl, (draft) => {
        draft[idx] = anchor;
      }),
    }));
  };

  handleClose = (idx) => {
    this.setState((prevState) => ({
      open: produce(prevState.open, (draft) => {
        draft[idx] = false;
      }),
    }));
  };

  toggleAdvanced = (filter, term, from, to) => {
    if (this.state.advancedOpen) {
      this.setState({ advancedOpen: false });
    } else {
      const queries = [];

      if (filter === "global") {
        queries.push({ filter: "fullText", term });
      } else {
        queries.push({ filter, term, from, to });
      }

      this.setState({ advancedOpen: true }, () => this.advancedSearch.current.setQueries(queries));
    }
  };

  handleHideIcons = (state) => {
    this.setState({ hideIcons: state });
  };

  openTreeView = () => {
    // The tree has no search bar, so leave the search the way Clear search does
    if (workspaceSettings.searchMode) $("#resetSearch").trigger("click");
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
        {/* A long search, such as a date range, shrinks the search box rather than these buttons */}
        <Box component="span" sx={{ display: "flex", flexShrink: 0 }}>
          <CreateMenu pioEnabled={this.state.pioEnabled} asposeEnabled={this.state.asposeEnabled} />
        </Box>
        {!this.state.hideIcons && (
          <Box component="span" sx={{ display: "flex" }}>
            <SocialActions onCreateRequest={this.props.eventHandlers.onCreateRequest} />
            <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.calendarEntry")} enterDelay={300}>
              <IconButton
                id="createCalendarEntryDlgLink"
                color="inherit"
                data-test-id="toolbar-calendar"
                aria-label={i18n.t("workspace:toolbar.workspaceToolbar.calendarEntry")}
              >
                <FontAwesomeIcon icon={faCalendarAlt} />
              </IconButton>
            </Tooltip>
          </Box>
        )}
        <Box
          component="span"
          sx={{
            borderRight: "1px solid transparent",
            height: "100%",
          }}
        ></Box>
        <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.viewMode")} enterDelay={300}>
          <IconButton
            id={VIEW_MODE_BUTTON_ID}
            data-test-id="toolbar-views"
            aria-controls="simple-menu2"
            aria-haspopup="true"
            onClick={(e) => this.handleOpen(0, e)}
            color="inherit"
            aria-label={i18n.t("workspace:toolbar.workspaceToolbar.viewMode")}
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
          <MenuItem
            onClick={this.openTreeView}
            data-test-id="toolbar-view-tree"
            aria-label={i18n.t("workspace:toolbar.workspaceToolbar.treeView")}
          >
            <FontAwesomeIcon icon={faStream} style={{ paddingRight: "10px" }} />
            {i18n.t("workspace:toolbar.workspaceToolbar.treeView")}
          </MenuItem>
          <MenuItem
            onClick={this.openListView}
            id="list_view_1"
            data-test-id="toolbar-view-list"
            aria-label={i18n.t("workspace:toolbar.workspaceToolbar.listView")}
          >
            <FontAwesomeIcon icon={faList} style={{ paddingRight: "10px" }} />
            {i18n.t("workspace:toolbar.workspaceToolbar.listView")}
          </MenuItem>
        </Menu>
        {this.state.treeView && <TreeSort />}
        {!this.state.treeView && (
          <Box component="span" sx={{ display: "flex", flexGrow: "1" }}>
            <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.viewMode")} enterDelay={300}>
              <IconButton
                data-test-id="toolbar-views-2"
                aria-controls="simple-menu3"
                aria-haspopup="true"
                onClick={(e) => this.handleOpen(1, e)}
                color="inherit"
                aria-label={i18n.t("workspace:toolbar.workspaceToolbar.viewMode")}
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
                {i18n.t("workspace:toolbar.workspaceToolbar.folderView")}
              </MenuItem>
              <MenuItem onClick={this.openViewAll} id="viewableItemsView_1" data-test-id="toolbar-view-all">
                <FontAwesomeIcon icon={faThList} style={{ paddingRight: "10px" }} />
                {i18n.t("workspace:toolbar.workspaceToolbar.viewAll")}
              </MenuItem>
            </Menu>
            <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.labgroupRecords")} enterDelay={300}>
              <IconButton
                onClick={this.displayLabgroup}
                id="labgroupFilter_1"
                color="inherit"
                data-test-id="toolbar-filter-labgroup"
                aria-label={i18n.t("workspace:toolbar.workspaceToolbar.labgroupRecords")}
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
            <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.favorites")} enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("favoritesFilter");
                  this.context.trackEvent("user:filter:favorites:workspace");
                }}
                id="favoritesFilter_1"
                color={this.state.favoritesFilter ? "default" : "inherit"}
                className={this.state.favoritesFilter ? "active" : ""}
                data-test-id="toolbar-filter-favorites"
                aria-label={i18n.t("workspace:toolbar.workspaceToolbar.favorites")}
              >
                <FontAwesomeIcon icon={faStar} />
              </IconButton>
            </Tooltip>
            <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.sharedWithMe")} enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("sharedFilter");
                  this.context.trackEvent("user:filter:shared_with_me:workspace");
                }}
                id="sharedFilter_1"
                color={this.state.sharedFilter ? "default" : "inherit"}
                className={this.state.sharedFilter ? "active" : ""}
                data-test-id="toolbar-filter-shared"
                aria-label={i18n.t("workspace:toolbar.workspaceToolbar.sharedWithMe")}
              >
                <FontAwesomeIcon icon={faShareAlt} />
              </IconButton>
            </Tooltip>
            <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.templates")} enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("templatesFilter");
                  this.context.trackEvent("user:filter:templates:workspace");
                }}
                id="templatesFilter_1"
                color={this.state.templatesFilter ? "default" : "inherit"}
                className={this.state.templatesFilter ? "active" : ""}
                data-test-id="toolbar-filter-templates"
                aria-label={i18n.t("workspace:toolbar.workspaceToolbar.templates")}
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
                  {"T"}
                </Box>
              </IconButton>
            </Tooltip>
            <Tooltip title={i18n.t("workspace:toolbar.workspaceToolbar.ontologyFiles")} enterDelay={300}>
              <IconButton
                onClick={() => {
                  this.toggleFilter("ontologiesFilter");
                  this.context.trackEvent("user:filter:ontologies:workspace");
                }}
                id="ontologiesFilter_1"
                color={this.state.ontologiesFilter ? "default" : "inherit"}
                className={this.state.ontologiesFilter ? "active" : ""}
                data-test-id="toolbar-filter-ontology"
                aria-label={i18n.t("workspace:toolbar.workspaceToolbar.ontologyFiles")}
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
                  {"O"}
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
        <Box component="span" sx={{ display: "flex", flexShrink: 0 }}>
          <DefaultSearch />
        </Box>
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

export default WorkspaceToolbar;
