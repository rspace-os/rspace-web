"use strict";
import React from "react";
import axios from "@/common/axios";
import update from "immutability-helper";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import styled from "@emotion/styled";
import Paper from "@mui/material/Paper";
import InputBase from "@mui/material/InputBase";
import Divider from "@mui/material/Divider";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Chip from "@mui/material/Chip";
import DateField from "../../../components/Inputs/DateField";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faFilter, faSearch, faBars } from "@fortawesome/free-solid-svg-icons";
import { truncateIsoTimestamp } from "../../../util/conversions";
library.add(faFilter, faSearch, faBars);

import UserSelect from "../AdvancedSearch/UserSelect/UserSelect";
import TagSelect from "../AdvancedSearch/TagSelect/TagSelect";
import ScopeDialog from "./SimpleSearchScopeDialog";

function formatDateForTooltip(date /*: ?Date*/) /*: string*/ {
  if (date === null) return "";
  return truncateIsoTimestamp(date, "date").orElse("Invalid date");
}

const SearchBar = styled.div`
  padding: 2px 2px;
  display: flex;
  alignitems: center;

  .MuiInputBase-root {
    flex-grow: 1;
    padding-left: 5px;
    input:focus,
    input:hover {
      background-color: transparent !important;
    }
  }

  .MuiDivider-root {
    width: 1px;
    height: 35px;
    margin: 4px;
  }

  .MuiTextField-root {
    .MuiInputLabel-formControl {
      transform: translate(0, 16px) scale(1);
    }
    .MuiInputLabel-formControl.Mui-focused,
    .MuiInputLabel-formControl.MuiFormLabel-filled {
      transform: translate(5px, 0) scale(0.75);
      transform-origin: top left;
    }
    label + .MuiInput-formControl {
      margin-top: 5px;
    }
    .MuiInput-underline:before,
    .MuiInput-underline:after {
      border-bottom: 0px;
    }
  }

  button.MuiIconButton-root {
    width: 44px;
    height: 44px;
    font-size: 16px !important;
  }

  .grow {
    flex-grow: 1;
  }

  .MuiChip-root {
    margin: 6px;
  }
`;

let FILTERS = {
  global: "All",
  fullText: "Text",
  tag: "Tag(s)",
  name: "Name",
  form: "Form",
  template: "Template",
  created: "Creation date",
  lastModified: "Last modified",
  owner: "Owner(s)",
  attachment: "Attachment",
};

const DEFAULT_STATE = {
  recordsDialog: false,
  selectedRecords: [],
  open: false,
  anchorEl: null,
  filter: "global",
  term: "",
  from: null,
  to: null,
};

class SimpleSearch extends React.Component {
  constructor(props) {
    super(props);
    this.state = DEFAULT_STATE;
  }

  resetState = () => {
    this.setState(DEFAULT_STATE);
  };

  // with the current configuration, we assume that only one query will be received
  setQueries = (queries) => {
    if (!queries.length) return;

    let query = queries[0];
    this.setState({
      filter: query.filter,
      term: query.term,
      from: query.from,
      to: query.to,
    });
  };

  componentDidMount = () => {
    // don't make backend call while there is no open-source
    // chemistry module.

    // axios
    //   .get("/integration/integrationInfo", {
    //     params: {
    //       name: "CHEMISTRY",
    //     },
    //   })
    //   .then((response) => {
    //     var integration = response.data.data;
    //     if (integration && integration.available && integration.enabled) {
    //       FILTERS.chemical = "Chemical";
    //     }
    //   });

    let toolbar = this;
    // Bad practise. Change when the reset button is in React
    $(document).on("click", "#resetSearch", function (e) {
      toolbar.resetState();
    });
  };

  toggleAdvanced = () => {
    this.props.toggleAdvanced(
      this.state.filter,
      this.state.term,
      this.state.from,
      this.state.to
    );
    this.resetState();
    this.props.hideIcons(false);
  };

  handleOpen = (event) => {
    this.setState({ open: true, anchorEl: event.currentTarget });
  };

  handleClose = () => {
    this.setState({ open: false });
  };

  handleCloseModal = () => {
    this.setState({ recordsDialog: false });
  };

  handleSelect = (key) => {
    if (this.props.advancedOpen) {
      return;
    } else if (key == "global") {
      this.props.hideIcons(false);
    } else if (key == "chemical") {
      loadChemSearcher();
      this.resetState();
      this.handleClose();
      return;
    } else {
      this.props.hideIcons(true);
    }

    this.setState(
      {
        filter: key,
        term: "",
      },
      this.handleClose
    );
  };

  isValid = () => {
    if (
      ["lastModified", "created", "owner", "tag"].findIndex(
        (i) => i == this.state.filter
      ) == -1
    ) {
      return this.state.term.length >= 2;
    } else if (
      ["owner", "tag"].findIndex((i) => i == this.state.filter) != -1
    ) {
      return this.state.term.length >= 1;
    } else {
      return true;
    }
  };

  submitSearch = (e) => {
    e.preventDefault();
    let selectedRecords = getSelectedGlobalIds();

    if (!this.isValid) {
    } else if (selectedRecords.length) {
      this.setState({ selectedRecords: selectedRecords });
      this.setState({ recordsDialog: true });
    } else {
      this.submit();
    }
  };

  searchEverywhere = () => {
    this.setState({ selectedRecords: [] }, this.submit);
  };

  submit = () => {
    this.setState({ loading: true, recordsDialog: false });
    workspaceSettings.url = "/workspace/ajax/search";
    workspaceSettings.options = [this.state.filter];
    workspaceSettings.terms = [this.formatTerm(workspaceSettings.options)];
    workspaceSettings.advancedSearch = false;
    workspaceSettings.searchMode = true;
    workspaceSettings.pageNumber = 0;

    if (this.state.selectedRecords.length) {
      workspaceSettings.options.push("records");
      workspaceSettings.terms.push(this.state.selectedRecords.join("; "));
      this.setState({ selectedRecords: [] });
    }

    doWorkspaceSearch(workspaceSettings.url, workspaceSettings);
  };

  formatTerm = (options) => {
    if (["created", "lastModified"].includes(this.state.filter)) {
      // Setting beginning of the day for 'from' and end of the day for 'to'
      return `${this.toISO(this.state.from, 0, 0, 0)}; ${this.toISO(
        this.state.to,
        23,
        59,
        59
      )}`;
    } else {
      if (options[0] === 'tag' && this.state.term && this.state.term.indexOf(",") !== -1) {
        return this.state.term.replaceAll(",", "__rspactags_comma__");
      }
      return this.state.term;
    }
  };

  toISO = (date, hours, minutes, seconds) => {
    if (typeof date === "string") {
      return date;
    } else if (date) {
      date.setHours(hours);
      date.setMinutes(minutes);
      date.setSeconds(seconds);

      return date.toISOString();
    } else {
      return null;
    }
  };

  handleSelectAutocomplete = (selects, label) => {
    this.setState({
      term: selects.map((s) => s[label]).join("<<>>"),
    });
  };

  handleChange = (event) => {
    this.setState({ term: event.target.value });
  };

  handleDateChange = (input, value) => {
    this.setState({ [input]: value });
  };

  removeRecord = (record_id) => {
    let idx = this.state.selectedRecords.findIndex((r) => r == record_id);
    this.setState({
      selectedRecords: update(this.state.selectedRecords, {
        $splice: [[idx, 1]],
      }),
    });
  };

  render() {
    return (
      <Paper style={{ flexGrow: "1", display: "flex" }} elevation={0}>
        <form onSubmit={this.submitSearch} style={{ width: "100%" }}>
          <SearchBar>
            {this.state.filter == "global" && (
              <Tooltip title="Filters" enterDelay={300}>
                <IconButton
                  data-test-id="s-search-filter"
                  color="default"
                  aria-haspopup="true"
                  onClick={this.handleOpen}
                  disabled={this.props.advancedOpen}
                  aria-label="Filters"
                >
                  <FontAwesomeIcon icon="filter" />
                </IconButton>
              </Tooltip>
            )}
            {this.state.filter != "global" && (
              <Chip
                data-test-id="s-search-filtered"
                label={FILTERS[this.state.filter]}
                clickable={!this.props.advancedOpen}
                variant={this.props.advancedOpen ? "default" : "outlined"}
                color={this.props.advancedOpen ? "default" : "primary"}
                aria-haspopup="true"
                onClick={this.props.advancedOpen ? () => {} : this.handleOpen}
                onDelete={() => this.handleSelect("global")}
                deleteIcon={
                  <FontAwesomeIcon
                    icon="times"
                    style={{ padding: "10px" }}
                    data-test-id="s-search-rm-filter"
                  />
                }
              />
            )}
            <Menu
              anchorOrigin={{
                vertical: "bottom",
                horizontal: "left",
              }}
              anchorEl={this.state.anchorEl}
              keepMounted
              open={this.state.open}
              onClose={this.handleClose}
            >
              {Object.keys(FILTERS).map((key) => (
                <MenuItem
                  data-test-id={`s-search-filter-${key}`}
                  onClick={() => this.handleSelect(key)}
                  key={key}
                  style={{ minHeight: "25px" }}
                  selected={this.state.filter == key}
                >
                  {FILTERS[key]}
                </MenuItem>
              ))}
            </Menu>
            {!["lastModified", "created", "owner", "tag"].includes(
              this.state.filter
            ) && (
              <InputBase
                data-test-id="s-search-input-normal"
                disabled={this.props.advancedOpen}
                placeholder="Search"
                value={this.state.term}
                onChange={this.handleChange}
                inputProps={{ "aria-label": "Search" }}
              />
            )}
            {this.state.filter == "owner" && (
              <UserSelect
                updateSelected={this.handleSelectAutocomplete}
                selected={this.state.filter == "owner" ? this.state.term : null}
                testId="s-search-input-user"
              />
            )}
            {this.state.filter == "tag" && (
              <TagSelect
                updateSelected={this.handleSelectAutocomplete}
                selected={this.state.filter == "tag" ? this.state.term : null}
                testId="s-search-input-tag"
              />
            )}
            {["lastModified", "created"].includes(this.state.filter) && (
              <>
                <Tooltip title={formatDateForTooltip(this.state.from)}>
                  <div>
                    <DateField
                      value={this.state.from}
                      onChange={({ target: { value } }) =>
                        this.handleDateChange("from", value)
                      }
                      maxDate={this.state.to || new Date()}
                      disableFuture
                      placeholder="the beginning"
                      label="From"
                      datatestid="s-search-input-from"
                    />
                  </div>
                </Tooltip>
                <Tooltip title={formatDateForTooltip(this.state.to)}>
                  <div>
                    <DateField
                      value={this.state.to}
                      onChange={({ target: { value } }) =>
                        this.handleDateChange("to", value)
                      }
                      minDate={this.state.from || new Date(1990, 1, 1)}
                      disableFuture
                      placeholder="now"
                      label="To"
                      datatestid="s-search-input-to"
                    />
                  </div>
                </Tooltip>
              </>
            )}
            <IconButton
              aria-label="Search"
              type="submit"
              onClick={this.submitSearch}
              disabled={this.props.advancedOpen || !this.isValid()}
              data-test-id="s-search-submit"
            >
              <FontAwesomeIcon icon="search" />
            </IconButton>
            <Divider />
            <Tooltip
              title="Advanced search"
              enterDelay={300}
              data-test-id="toggle-advanced"
            >
              <IconButton
                onClick={this.toggleAdvanced}
                aria-label="Advanced search"
              >
                <FontAwesomeIcon icon="bars" />
              </IconButton>
            </Tooltip>
          </SearchBar>
        </form>
        <ScopeDialog
          open={this.state.recordsDialog}
          selectedRecords={this.state.selectedRecords}
          removeRecord={(r) => this.removeRecord(r)}
          submit={this.submit}
          searchEverywhere={this.searchEverywhere}
        />
      </Paper>
    );
  }
}

export default SimpleSearch;
