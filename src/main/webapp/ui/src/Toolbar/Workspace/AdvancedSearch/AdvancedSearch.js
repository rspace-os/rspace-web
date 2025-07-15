"use strict";
import React from "react";
import update from "immutability-helper";
import styled from "@emotion/styled";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import FormControlLabel from "@mui/material/FormControlLabel";
import Input from "@mui/material/Input";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Tooltip from "@mui/material/Tooltip";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import { CardWrapper } from "../../../styles/CommonStyles.js";
import DateField from "../../../components/Inputs/DateField";
import Grid from "@mui/material/Grid";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faPlus,
  faTrashAlt,
  faSave,
  faTimes,
  faInfoCircle,
} from "@fortawesome/free-solid-svg-icons";
library.add(faPlus, faTrashAlt, faSave, faTimes, faInfoCircle);

import FilePicker from "./RecordSelect/FilePicker";
import UserSelect from "./UserSelect/UserSelect";
import TagSelect from "./TagSelect/TagSelect";
import Searchbox from "./Searchbox";

const QueryRow = styled.tr`
  td {
    padding: 0px 0px 10px 0px;
  }
  .search-type {
    width: 150px;
  }
  .search-term {
    padding-left: 10px;
    padding-right: 10px;
  }
  .actions {
    width: 100px;
  }
  .MuiInputBase-root {
    width: 100%;
  }
  .MuiInputBase-root {
    input:hover,
    input:active {
      background-color: transparent !important;
    }
  }
  .MuiButtonBase-root:not(.MuiChip-root) {
    width: 39px;
    font-size: 15px;
    margin-left: 10px;
  }
`;

const SEARCH_TYPES = {
  fullText: "Text",
  tag: "Tag(s)",
  name: "Name",
  form: "Form",
  template: "Template",
  created: "Creation date",
  lastModified: "Last modified",
  owner: "Owner(s)",
  attachment: "Attachment",
  records: "Within records",
};

const DEFAULT_STATE = {
  loading: false,
  queries: [{ filter: "fullText", term: "", from: null, to: null }],
  fulfillAll: workspaceSettings.operator == "AND" ? "true" : "false",
};

class AdvancedSearch extends React.Component {
  constructor() {
    super();
    this.state = DEFAULT_STATE;
  }

  componentDidMount = () => {
    let toolbar = this;
    $(document).on("click", "#resetSearch", function (e) {
      toolbar.reset();
    });
  };

  reset = () => {
    this.setState(DEFAULT_STATE);
  };

  setQueries = (queries) => {
    this.setState({
      queries: queries,
    });
  };

  handleClose = () => {
    this.reset();
  };

  handleCheck = (event) => {
    this.setState({ fulfillAll: event.target.value });
  };

  handleChange = (idx, input) => (event) => {
    let value = event.target.value;
    let queries = this.state.queries;

    if (value == "records") {
      let selected = getSelectedGlobalIds();

      this.setState({
        queries: update(queries, {
          [idx]: { $set: { filter: value, term: selected } },
        }),
      });
      return;
    } else if (input == "filter") {
      queries = update(queries, {
        [idx]: { term: { $set: "" } },
      });
    }

    this.setState({
      queries: update(queries, {
        [idx]: { [input]: { $set: value } },
      }),
    });
  };

  updateSelected = (idx, selected) => {
    this.setState({
      queries: update(this.state.queries, {
        [idx]: { term: { $set: selected } },
      }),
    });
  };

  handleDateChange = (idx, input, value) => {
    this.setState({
      queries: update(this.state.queries, {
        [idx]: { [input]: { $set: value } },
      }),
    });
  };

  handleSelectAutocomplete = (selects, label, idx) => {
    this.setState({
      queries: update(this.state.queries, {
        [idx]: { term: { $set: selects.map((s) => s[label]).join("<<>>") } },
      }),
    });
  };

  addNewQuery = () => {
    this.setState({
      queries: update(this.state.queries, {
        $push: [{ filter: "fullText", term: "", from: null, to: null }],
      }),
    });
  };

  deleteQuery = (idx) => {
    this.setState({
      queries: update(this.state.queries, {
        $splice: [[idx, 1]],
      }),
    });
  };

  submitSearch = () => {
    this.setState({ loading: true });
    workspaceSettings.url = "/workspace/ajax/search";
    workspaceSettings.options = this.state.queries.map((q) => q.filter);
    workspaceSettings.terms = this.state.queries.map((q) => this.formatTerm(q));
    workspaceSettings.advancedSearch = true;
    workspaceSettings.searchMode = true;
    workspaceSettings.operator = this.state.fulfillAll == "true" ? "AND" : "OR";
    workspaceSettings.pageNumber = 0;

    doWorkspaceSearch(workspaceSettings.url, workspaceSettings);
    RS.trackEvent("user:search:advanced:workspace", {
      options: workspaceSettings.options,
      operator: workspaceSettings.operator,
    });
  };

  formatTerm = (query) => {
    if (["created", "lastModified"].includes(query.filter)) {
      // Setting beginning of the day for 'from' and end of the day for 'to'
      return `${this.toISO(query.from, 0, 0, 0)}; ${this.toISO(
        query.to,
        23,
        59,
        59,
      )}`;
    } else if (query.filter == "records") {
      return query.term.join("; ");
    } else {
      return query.term;
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

  validateQueries = () => {
    let valid = true;
    let oneValid = false;
    let queries = this.state.queries;

    this.state.queries.map((query, idx) => {
      let isSimple = [
        "fullText",
        "name",
        "form",
        "template",
        "attachment",
      ].includes(query.filter);
      let isSelectable = ["owner", "tag"].includes(query.filter);
      let isScopeRecords = query.filter == "records";

      if (isSimple) {
        if (query.term.length >= 2) {
          valid &= true;
          oneValid = true;

          queries = update(queries, {
            [idx]: { error: { $set: null } },
          });
        } else {
          queries = update(queries, {
            [idx]: { error: { $set: "The term should be at least 2 symbols" } },
          });
          if (query.term.length || queries.length == 1) valid = false;
        }
      } else if (isSelectable || isScopeRecords) {
        if (query.term.length > 0) {
          valid &= true;
          oneValid = true;

          queries = update(queries, {
            [idx]: { error: { $set: null } },
          });
        } else {
          queries = update(queries, {
            [idx]: { error: { $set: "Include at least one " + query.filter } },
          });
          if (queries.length == 1) valid = false;
        }
      } else {
        valid &= true;
        oneValid = true;
      }
    });
    valid = valid && oneValid;

    if (valid) {
      queries = queries.filter((q) => q.error == null);
    }

    this.setState(
      {
        queries: queries,
      },
      () => {
        if (valid) this.submitSearch();
      },
    );
  };

  // lists all the queries inputs in the advanced search
  searchQueries = () => {
    return this.state.queries.map((query, idx) => (
      <QueryRow key={idx}>
        <td className="search-type">
          <Select
            variant="standard"
            data-test-id={`a-search-type-${idx}`}
            value={query.filter}
            onChange={this.handleChange(idx, "filter")}
            style={{
              paddingTop: ["created", "lastModified"].includes(query.filter)
                ? "16px"
                : "0px",
            }}
          >
            {Object.keys(SEARCH_TYPES).map((key) => (
              <MenuItem
                key={key}
                value={key}
                data-test-id={`a-search-option-${key}`}
              >
                {SEARCH_TYPES[key]}
              </MenuItem>
            ))}
          </Select>
        </td>
        <td className="search-term">
          {["fullText", "name", "form", "template", "attachment"].includes(
            query.filter,
          ) && (
            <Searchbox
              idx={idx}
              query={query}
              onChange={this.handleChange(idx, "term")}
              onSubmit={this.validateQueries}
            />
          )}
          {["owner"].includes(query.filter) && (
            <UserSelect
              error={query.error}
              selected={query.term}
              advanced={true}
              updateSelected={(s, l) =>
                this.handleSelectAutocomplete(s, l, idx)
              }
              testId={`a-search-input-${idx}`}
            />
          )}
          {["tag"].includes(query.filter) && (
            <TagSelect
              error={query.error}
              selected={query.term}
              advanced={true}
              updateSelected={(s, l) =>
                this.handleSelectAutocomplete(s, l, idx)
              }
              testId={`a-search-input-${idx}`}
            />
          )}
          {["created", "lastModified"].includes(query.filter) && (
            <Grid container spacing={1}>
              <Grid item>
                <DateField
                  datatestid={`a-search-input-${idx}-from`}
                  label="From"
                  value={query.from}
                  disableFuture
                  placeholder="the beginning"
                  onChange={({ target: { value } }) =>
                    this.handleDateChange(idx, "from", value)
                  }
                  maxDate={query.to}
                  outputFormat="date"
                />
              </Grid>
              <Grid item>
                <DateField
                  datatestid={`a-search-input-${idx}-to`}
                  label="To"
                  value={query.to}
                  disableFuture
                  placeholder="now"
                  onChange={({ target: { value } }) =>
                    this.handleDateChange(idx, "to", value)
                  }
                  minDate={query.from}
                  outputFormat="date"
                />
              </Grid>
            </Grid>
          )}
          {query.filter == "records" && (
            <FilePicker
              error={query.error}
              term={query.term}
              updateSelected={(selected) => this.updateSelected(idx, selected)}
            />
          )}
        </td>
        <td className="actions">
          {this.state.queries.length > 1 && (
            <Tooltip title="Remove condition">
              <IconButton
                onClick={() => this.deleteQuery(idx)}
                data-test-id={`a-search-rmv-${idx}`}
              >
                <FontAwesomeIcon icon="trash-alt" />
              </IconButton>
            </Tooltip>
          )}
          {this.state.queries.length == idx + 1 && (
            <Tooltip title="Add new condition">
              <IconButton
                onClick={this.addNewQuery}
                data-test-id={`a-search-query-add`}
              >
                <FontAwesomeIcon icon="plus" />
              </IconButton>
            </Tooltip>
          )}
        </td>
      </QueryRow>
    ));
  };

  render() {
    return (
      <CardWrapper>
        <Card style={{ overflow: "visible" }}>
          <CardHeader subheader="Advanced search" />
          <CardContent>
            <table style={{ width: "80%", marginBottom: "0px" }}>
              <tbody>{this.searchQueries()}</tbody>
            </table>
            {this.state.queries.length > 1 && (
              <RadioGroup
                value={this.state.fulfillAll}
                onChange={this.handleCheck}
                margin="dense"
              >
                <FormControlLabel
                  value="true"
                  control={
                    <Radio
                      color="primary"
                      inputProps={{ "aria-label": "Satisfy all conditions" }}
                    />
                  }
                  label={
                    <div>
                      Satisfy all conditions
                      {this.state.queries.findIndex(
                        (q) => q.filter == "records",
                      ) != -1 && (
                        <Tooltip title="At least one of the 'Within records' conditions will always be satisfied.">
                          <IconButton
                            data-test-id={`show-condition-info`}
                            style={{
                              width: "40px",
                              height: "40px",
                              marginLeft: "10px",
                              padding: "0px",
                            }}
                          >
                            <FontAwesomeIcon icon="info-circle" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </div>
                  }
                  data-test-id="fullfill-all"
                />
                <FormControlLabel
                  value="false"
                  control={
                    <Radio
                      color="primary"
                      inputProps={{
                        "aria-label": "Satisfy at least one condition",
                      }}
                    />
                  }
                  label={
                    <div>
                      Satisfy at least one condition
                      {this.state.queries.findIndex(
                        (q) => q.filter == "records",
                      ) != -1 && (
                        <Tooltip title="At least one of the 'Within records' conditions will always be satisfied.">
                          <IconButton
                            data-test-id={`show-condition-info`}
                            style={{
                              width: "40px",
                              height: "40px",
                              marginLeft: "10px",
                              padding: "0px",
                            }}
                          >
                            <FontAwesomeIcon icon="info-circle" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </div>
                  }
                  data-test-id="fullfill-one"
                />
              </RadioGroup>
            )}
          </CardContent>
          <CardActions style={{ backgroundColor: "#eceff1" }}>
            <span></span>
            <span className="group-right">
              <Button onClick={this.reset} data-test-id="a-search-reset">
                Reset
              </Button>
              <Button
                onClick={this.validateQueries}
                variant="contained"
                color="primary"
                data-test-id="a-search-submit"
              >
                Search
              </Button>
            </span>
          </CardActions>
        </Card>
      </CardWrapper>
    );
  }
}

export default AdvancedSearch;
