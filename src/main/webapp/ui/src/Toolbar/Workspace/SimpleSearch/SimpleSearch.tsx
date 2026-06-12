import { faBars } from "@fortawesome/free-solid-svg-icons/faBars";
import { faFilter } from "@fortawesome/free-solid-svg-icons/faFilter";
import { faSearch } from "@fortawesome/free-solid-svg-icons/faSearch";
import { faTimes } from "@fortawesome/free-solid-svg-icons/faTimes";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Chip, { chipClasses } from "@mui/material/Chip";
import { dividerClasses } from "@mui/material/Divider";
import { formLabelClasses } from "@mui/material/FormLabel";
import IconButton, { iconButtonClasses } from "@mui/material/IconButton";
import { inputClasses } from "@mui/material/Input";
import InputBase, { inputBaseClasses } from "@mui/material/InputBase";
import { inputLabelClasses } from "@mui/material/InputLabel";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import { textFieldClasses } from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import React from "react";
import axios from "@/common/axios";
import DateField from "../../../components/Inputs/DateField";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { truncateIsoTimestamp } from "../../../stores/definitions/Units";
import TagSelect from "../AdvancedSearch/TagSelect/TagSelect";
import UserSelect from "../AdvancedSearch/UserSelect/UserSelect";
import ChemicalSearcher from "./ChemicalSearcher";
import ScopeDialog from "./SimpleSearchScopeDialog";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const workspaceSettings: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function doWorkspaceSearch(url: string, settings: any): void;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function getSelectedGlobalIds(): any[];

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function formatDateForTooltip(date: any) /*: string*/ {
  if (date === null) return "";
  return truncateIsoTimestamp(date, "date").orElse("Invalid date");
}

const FILTERS: Record<string, string> = {
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
  filterDropdownIsOpen: false,
  anchorEl: null,
  filter: "global",
  term: "",
  from: null,
  to: null,
  chemicalSearchDialogOpen: false,
  chemistryProvider: "",
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class SimpleSearch extends React.Component<any, any> {
  declare context: React.ContextType<typeof AnalyticsContext>;

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = DEFAULT_STATE;
  }

  resetState = () => {
    this.setState(DEFAULT_STATE);
  };

  // with the current configuration, we assume that only one query will be received
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  setQueries = (queries: any) => {
    if (!queries.length) return;

    const query = queries[0];
    this.setState({
      filter: query.filter,
      term: query.term,
      from: query.from,
      to: query.to,
    });
  };

  componentDidMount = () => {
    axios
      .get("/integration/integrationInfo", {
        params: {
          name: "CHEMISTRY",
        },
      })
      .then((response) => {
        const integration = response.data.data;
        if (integration?.available && integration.enabled) {
          FILTERS.chemical = "Chemical";
        }
      });

    axios
      .get("/deploymentproperties/ajax/property", {
        params: {
          name: "chemistry.provider",
        },
      })
      .then((response) => {
        this.setState({ chemistryProvider: response.data });
      });

    // Bad practise. Change when the reset button is in React
    $(document).on("click", "#resetSearch", () => this.resetState());
  };

  toggleAdvanced = () => {
    this.props.toggleAdvanced(this.state.filter, this.state.term, this.state.from, this.state.to);
    this.resetState();
    this.props.hideIcons(false);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleOpen = (event: any) => {
    this.setState({
      filterDropdownIsOpen: true,
      anchorEl: event.currentTarget,
    });
  };

  handleClose = () => {
    this.setState({ filterDropdownIsOpen: false });
  };

  handleCloseModal = () => {
    this.setState({ recordsDialog: false });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleSelect = (key: any) => {
    if (this.props.advancedOpen) {
      return;
    }
    if (key === "global") {
      this.props.hideIcons(false);
    } else if (key === "chemical") {
      if (this.state.chemistryProvider === "indigo") {
        this.setState({ chemicalSearchDialogOpen: true }, () => {
          this.handleClose();
        });
      }
    } else {
      this.props.hideIcons(true);
    }

    this.setState(
      {
        filter: key,
        term: "",
      },
      this.handleClose,
    );
  };

  isValid = () => {
    if (!["lastModified", "created", "owner", "tag"].includes(this.state.filter)) {
      return this.state.term.length >= 2;
    }
    if (["owner", "tag"].includes(this.state.filter)) {
      return this.state.term.length >= 1;
    }
    return true;
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  submitSearch = (e: any) => {
    e.preventDefault();
    const selectedRecords = getSelectedGlobalIds();

    if (selectedRecords.length) {
      this.setState({ selectedRecords, recordsDialog: true });
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

    let scopedSearch = false;
    if (this.state.selectedRecords.length) {
      scopedSearch = true;
      workspaceSettings.options.push("records");
      workspaceSettings.terms.push(this.state.selectedRecords.join("; "));
      this.setState({ selectedRecords: [] });
    }

    doWorkspaceSearch(workspaceSettings.url, workspaceSettings);

    if (scopedSearch) {
      this.context.trackEvent("user:search:scoped:workspace");
    } else {
      this.context.trackEvent("user:search:simple:workspace");
    }
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  formatTerm = (options: any) => {
    if (["created", "lastModified"].includes(this.state.filter)) {
      // Setting beginning of the day for 'from' and end of the day for 'to'
      return `${this.toISO(this.state.from, 0, 0, 0)}; ${this.toISO(this.state.to, 23, 59, 59)}`;
    }
    if (options[0] === "tag" && this.state.term && this.state.term.indexOf(",") !== -1) {
      return this.state.term.replaceAll(",", "__rspactags_comma__");
    }
    return this.state.term;
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  toISO = (date: any, hours: any, minutes: any, seconds: any) => {
    if (typeof date === "string") {
      return date;
    }
    if (date) {
      date.setHours(hours);
      date.setMinutes(minutes);
      date.setSeconds(seconds);

      return date.toISOString();
    }
    return null;
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleSelectAutocomplete = (selects: any, label: any) => {
    this.setState({
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      term: selects.map((s: any) => s[label]).join("<<>>"),
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleChange = (event: any) => {
    this.setState({ term: event.target.value });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleDateChange = (input: any, value: any) => {
    this.setState({ [input]: value });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  removeRecord = (record_id: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      selectedRecords: prevState.selectedRecords.filter((r: any) => r !== record_id),
    }));
  };

  render() {
    return (
      <>
        <Paper sx={{ flexGrow: "1", display: "flex" }} elevation={0}>
          <Box component="form" onSubmit={this.submitSearch} sx={{ width: "100%" }}>
            <Box
              sx={{
                padding: "2px 2px",
                display: "flex",
                alignItems: "center",
                [`& .${inputBaseClasses.root}`]: {
                  flexGrow: 1,
                  "& input:focus, & input:hover": {
                    backgroundColor: "transparent !important",
                  },
                },
                [`& .${dividerClasses.root}`]: {
                  width: "1px",
                  height: "35px",
                  margin: "4px",
                },
                [`& .${textFieldClasses.root}`]: {
                  [`& .${inputLabelClasses.formControl}`]: {
                    transform: "translate(0, 16px) scale(1)",
                  },
                  [`& .${inputLabelClasses.formControl}.${inputLabelClasses.focused}, & .${inputLabelClasses.formControl}.${formLabelClasses.filled}`]:
                    {
                      transform: "translate(5px, 0) scale(0.75)",
                      transformOrigin: "top left",
                    },
                  [`& .${inputClasses.underline}:before, & .${inputClasses.underline}:after`]: {
                    borderBottom: 0,
                  },
                },
                [`& button.${iconButtonClasses.root}`]: {
                  width: 44,
                  height: 44,
                  fontSize: "16px !important",
                },
                "& .grow": {
                  flexGrow: 1,
                },
                [`& .${chipClasses.root}`]: {
                  margin: "6px",
                },
              }}
            >
              {(this.state.filter === "global" || this.state.filter === "chemical") && (
                <Tooltip title="Filters" enterDelay={300}>
                  <IconButton
                    data-test-id="s-search-filter"
                    color="default"
                    aria-haspopup="true"
                    onClick={this.handleOpen}
                    disabled={this.props.advancedOpen}
                    aria-label="Filters"
                  >
                    <FontAwesomeIcon icon={faFilter} />
                  </IconButton>
                </Tooltip>
              )}
              {this.state.filter !== "global" && this.state.filter !== "chemical" && (
                <Chip
                  data-test-id="s-search-filtered"
                  label={FILTERS[this.state.filter]}
                  clickable={!this.props.advancedOpen}
                  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                  variant={(this.props.advancedOpen ? "default" : "outlined") as any}
                  color={this.props.advancedOpen ? "default" : "primary"}
                  aria-haspopup="true"
                  onClick={this.props.advancedOpen ? () => {} : this.handleOpen}
                  onDelete={() => this.handleSelect("global")}
                  deleteIcon={
                    <FontAwesomeIcon icon={faTimes} style={{ padding: "10px" }} data-test-id="s-search-rm-filter" />
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
                open={this.state.filterDropdownIsOpen}
                onClose={this.handleClose}
              >
                {Object.keys(FILTERS).map((key) => (
                  <MenuItem
                    data-test-id={`s-search-filter-${key}`}
                    onClick={() => this.handleSelect(key)}
                    key={key}
                    sx={{ minHeight: "25px" }}
                    selected={this.state.filter === key}
                  >
                    {FILTERS[key]}
                  </MenuItem>
                ))}
              </Menu>
              {!["lastModified", "created", "owner", "tag"].includes(this.state.filter) && (
                <InputBase
                  data-test-id="s-search-input-normal"
                  disabled={this.props.advancedOpen}
                  placeholder="Search"
                  value={this.state.term}
                  onChange={this.handleChange}
                  slotProps={{ input: { "aria-label": "Search" } }}
                />
              )}
              {this.state.filter === "owner" && (
                <UserSelect
                  updateSelected={this.handleSelectAutocomplete}
                  selected={this.state.filter === "owner" ? this.state.term : null}
                  testId="s-search-input-user"
                />
              )}
              {this.state.filter === "tag" && (
                <TagSelect
                  updateSelected={this.handleSelectAutocomplete}
                  selected={this.state.filter === "tag" ? this.state.term : null}
                  testId="s-search-input-tag"
                />
              )}
              {["lastModified", "created"].includes(this.state.filter) && (
                <>
                  <Tooltip title={formatDateForTooltip(this.state.from)}>
                    <div>
                      <DateField
                        value={this.state.from}
                        onChange={({ target: { value } }) => this.handleDateChange("from", value)}
                        maxDate={this.state.to || new Date()}
                        disableFuture
                        placeholder="the beginning"
                        label="From"
                        data-test-id="s-search-input-from"
                      />
                    </div>
                  </Tooltip>
                  <Tooltip title={formatDateForTooltip(this.state.to)}>
                    <div>
                      <DateField
                        value={this.state.to}
                        onChange={({ target: { value } }) => this.handleDateChange("to", value)}
                        minDate={this.state.from || new Date(1990, 1, 1)}
                        disableFuture
                        placeholder="now"
                        label="To"
                        data-test-id="s-search-input-to"
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
                <FontAwesomeIcon icon={faSearch} />
              </IconButton>
              <Tooltip title="Advanced search" enterDelay={300} data-test-id="toggle-advanced">
                <IconButton onClick={this.toggleAdvanced} aria-label="Advanced search">
                  <FontAwesomeIcon icon={faBars} />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>
          <ScopeDialog
            open={this.state.recordsDialog}
            selectedRecords={this.state.selectedRecords}
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            removeRecord={(r: any) => this.removeRecord(r)}
            submit={this.submit}
            searchEverywhere={this.searchEverywhere}
          />
        </Paper>
        <ChemicalSearcher
          isOpen={this.state.chemicalSearchDialogOpen}
          onClose={() => this.setState({ chemicalSearchDialogOpen: false })}
        />
      </>
    );
  }
}

SimpleSearch.contextType = AnalyticsContext;

export default SimpleSearch;
