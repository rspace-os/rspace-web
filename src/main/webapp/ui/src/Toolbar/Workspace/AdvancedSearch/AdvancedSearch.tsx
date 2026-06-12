import { faInfoCircle } from "@fortawesome/free-solid-svg-icons/faInfoCircle";
import { faPlus } from "@fortawesome/free-solid-svg-icons/faPlus";
import { faTrashAlt } from "@fortawesome/free-solid-svg-icons/faTrashAlt";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { buttonBaseClasses } from "@mui/material/ButtonBase";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import { chipClasses } from "@mui/material/Chip";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import { inputBaseClasses } from "@mui/material/InputBase";
import MenuItem from "@mui/material/MenuItem";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Select from "@mui/material/Select";
import Tooltip from "@mui/material/Tooltip";
import { produce } from "immer";
import React from "react";
import DateField from "../../../components/Inputs/DateField";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { CardWrapper } from "../../../styles/CommonStyles";
import FilePicker from "./RecordSelect/FilePicker";
import Searchbox from "./Searchbox";
import TagSelect from "./TagSelect/TagSelect";
import UserSelect from "./UserSelect/UserSelect";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const workspaceSettings: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function doWorkspaceSearch(url: string, settings: any): void;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare function getSelectedGlobalIds(): any[];

const queryRowSx = {
  "& td": {
    padding: "0px 0px 10px 0px",
  },
  "& .search-type": {
    width: "150px",
  },
  "& .search-term": {
    paddingLeft: "10px",
    paddingRight: "10px",
  },
  "& .actions": {
    width: "100px",
  },
  [`& .${inputBaseClasses.root}`]: {
    width: "100%",
    "& input:hover, & input:active": {
      backgroundColor: "transparent !important",
    },
  },
  [`& .${buttonBaseClasses.root}:not(.${chipClasses.root})`]: {
    width: 39,
    fontSize: 15,
    marginLeft: "10px",
  },
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function QueryRow(props: any) {
  return <Box component="tr" {...props} sx={{ ...queryRowSx, ...props.sx }} />;
}

const SEARCH_TYPES: Record<string, string> = {
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

// Filters grouped by how their term is entered and validated
const SIMPLE_FILTERS = ["fullText", "name", "form", "template", "attachment"];
const DATE_FILTERS = ["created", "lastModified"];
const SELECTABLE_FILTERS = ["owner", "tag"];

const makeQuery = () => ({
  filter: "fullText",
  term: "",
  from: null,
  to: null,
});

const DEFAULT_STATE = {
  loading: false,
  queries: [makeQuery()],
  fulfillAll: workspaceSettings.operator === "AND" ? "true" : "false",
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class AdvancedSearch extends React.Component<any, any> {
  declare context: React.ContextType<typeof AnalyticsContext>;

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = DEFAULT_STATE;
  }

  componentDidMount = () => {
    const toolbar = this;
    $(document).on("click", "#resetSearch", (_e) => {
      toolbar.reset();
    });
  };

  reset = () => {
    this.setState(DEFAULT_STATE);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  setQueries = (queries: any) => {
    this.setState({
      queries,
    });
  };

  handleClose = () => {
    this.reset();
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleCheck = (event: any) => {
    this.setState({ fulfillAll: event.target.value });
  };

  // Apply an Immer recipe to the queries array and store the result
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    updateQueries = (recipe: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      queries: produce(prevState.queries, recipe),
    }));
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleChange = (idx: any, input: any) => (event: any) => {
    const value = event.target.value;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.updateQueries((draft: any) => {
      if (value === "records") {
        draft[idx] = { filter: value, term: getSelectedGlobalIds() };
        return;
      }
      if (input === "filter") {
        draft[idx].term = "";
      }
      draft[idx][input] = value;
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  updateSelected = (idx: any, selected: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.updateQueries((draft: any) => {
      draft[idx].term = selected;
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleDateChange = (idx: any, input: any, value: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.updateQueries((draft: any) => {
      draft[idx][input] = value;
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleSelectAutocomplete = (selects: any, label: any, idx: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.updateQueries((draft: any) => {
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      draft[idx].term = selects.map((s: any) => s[label]).join("<<>>");
    });
  };

  addNewQuery = () => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.updateQueries((draft: any) => {
      draft.push(makeQuery());
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  deleteQuery = (idx: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.updateQueries((draft: any) => {
      draft.splice(idx, 1);
    });
  };

  submitSearch = () => {
    this.setState({ loading: true });
    workspaceSettings.url = "/workspace/ajax/search";
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    workspaceSettings.options = this.state.queries.map((q: any) => q.filter);
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    workspaceSettings.terms = this.state.queries.map((q: any) => this.formatTerm(q));
    workspaceSettings.advancedSearch = true;
    workspaceSettings.searchMode = true;
    workspaceSettings.operator = this.state.fulfillAll === "true" ? "AND" : "OR";
    workspaceSettings.pageNumber = 0;

    doWorkspaceSearch(workspaceSettings.url, workspaceSettings);
    this.context.trackEvent("user:search:advanced:workspace", {
      options: workspaceSettings.options,
      operator: workspaceSettings.operator,
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  formatTerm = (query: any) => {
    if (DATE_FILTERS.includes(query.filter)) {
      // Setting beginning of the day for 'from' and end of the day for 'to'
      return `${this.toISO(query.from, 0, 0, 0)}; ${this.toISO(query.to, 23, 59, 59)}`;
    }
    if (query.filter === "records") {
      return query.term.join("; ");
    }
    return query.term;
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

  validateQueries = () => {
    let valid = true;
    let oneValid = false;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    const queries: any[] = produce(this.state.queries as any[], (draft: any) => {
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      this.state.queries.forEach((query: any, idx: any) => {
        const isSimple = SIMPLE_FILTERS.includes(query.filter);
        const isSelectable = SELECTABLE_FILTERS.includes(query.filter);
        const isScopeRecords = query.filter === "records";

        if (isSimple) {
          if (query.term.length >= 2) {
            oneValid = true;
            draft[idx].error = null;
          } else {
            draft[idx].error = "The term should be at least 2 symbols";
            if (query.term.length || draft.length === 1) valid = false;
          }
        } else if (isSelectable || isScopeRecords) {
          if (query.term.length > 0) {
            oneValid = true;
            draft[idx].error = null;
          } else {
            draft[idx].error = `Include at least one ${query.filter}`;
            if (draft.length === 1) valid = false;
          }
        } else {
          oneValid = true;
        }
      });
    });
    valid = valid && oneValid;

    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    const validatedQueries = valid ? queries.filter((q: any) => q.error == null) : queries;

    this.setState({ queries: validatedQueries }, () => {
      if (valid) this.submitSearch();
    });
  };

  // lists all the queries inputs in the advanced search
  searchQueries = () => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    return this.state.queries.map((query: any, idx: any) => (
      <QueryRow key={idx}>
        <td className="search-type">
          <Select
            variant="standard"
            data-test-id={`a-search-type-${idx}`}
            value={query.filter}
            onChange={this.handleChange(idx, "filter")}
            sx={{
              paddingTop: DATE_FILTERS.includes(query.filter) ? "16px" : "0px",
            }}
          >
            {Object.keys(SEARCH_TYPES).map((key) => (
              <MenuItem key={key} value={key} data-test-id={`a-search-option-${key}`}>
                {SEARCH_TYPES[key]}
              </MenuItem>
            ))}
          </Select>
        </td>
        <td className="search-term">
          {SIMPLE_FILTERS.includes(query.filter) && (
            <Searchbox
              idx={idx}
              query={query}
              onChange={this.handleChange(idx, "term")}
              onSubmit={this.validateQueries}
            />
          )}
          {query.filter === "owner" && (
            <UserSelect
              error={query.error}
              selected={query.term}
              advanced={true}
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              updateSelected={(s: any, l: any) => this.handleSelectAutocomplete(s, l, idx)}
              testId={`a-search-input-${idx}`}
            />
          )}
          {query.filter === "tag" && (
            <TagSelect
              error={query.error}
              selected={query.term}
              advanced={true}
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              updateSelected={(s: any, l: any) => this.handleSelectAutocomplete(s, l, idx)}
              testId={`a-search-input-${idx}`}
            />
          )}
          {DATE_FILTERS.includes(query.filter) && (
            <Grid container spacing={1}>
              <Grid>
                <DateField
                  data-test-id={`a-search-input-${idx}-from`}
                  label="From"
                  value={query.from}
                  disableFuture
                  placeholder="the beginning"
                  onChange={({ target: { value } }) => this.handleDateChange(idx, "from", value)}
                  maxDate={query.to}
                  // @ts-expect-error pragmatic jsx->tsx conversion: outputFormat is not a typed DateField prop
                  outputFormat="date"
                />
              </Grid>
              <Grid>
                <DateField
                  data-test-id={`a-search-input-${idx}-to`}
                  label="To"
                  value={query.to}
                  disableFuture
                  placeholder="now"
                  onChange={({ target: { value } }) => this.handleDateChange(idx, "to", value)}
                  minDate={query.from}
                  // @ts-expect-error pragmatic jsx->tsx conversion: outputFormat is not a typed DateField prop
                  outputFormat="date"
                />
              </Grid>
            </Grid>
          )}
          {query.filter === "records" && (
            <FilePicker
              error={query.error}
              term={query.term}
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              updateSelected={(selected: any) => this.updateSelected(idx, selected)}
            />
          )}
        </td>
        <td className="actions">
          {this.state.queries.length > 1 && (
            <Tooltip title="Remove condition">
              <IconButton onClick={() => this.deleteQuery(idx)} data-test-id={`a-search-rmv-${idx}`}>
                <FontAwesomeIcon icon={faTrashAlt} />
              </IconButton>
            </Tooltip>
          )}
          {this.state.queries.length === idx + 1 && (
            <Tooltip title="Add new condition">
              <IconButton onClick={this.addNewQuery} data-test-id={`a-search-query-add`}>
                <FontAwesomeIcon icon={faPlus} />
              </IconButton>
            </Tooltip>
          )}
        </td>
      </QueryRow>
    ));
  };

  // Renders one "satisfy all / at least one" radio option. Shows an info
  // tooltip whenever a 'Within records' condition is present.
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      renderFulfillOption = (value: any, dataTestId: any, label: any) => (
    <FormControlLabel
      value={value}
      control={<Radio color="primary" slotProps={{ input: { "aria-label": label } }} />}
      label={
        <div>
          {label}
          {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
          {this.state.queries.some((q: any) => q.filter === "records") && (
            <Tooltip title="At least one of the 'Within records' conditions will always be satisfied.">
              <IconButton
                data-test-id="show-condition-info"
                sx={{
                  width: "40px",
                  height: "40px",
                  marginLeft: "10px",
                  padding: "0px",
                }}
              >
                <FontAwesomeIcon icon={faInfoCircle} />
              </IconButton>
            </Tooltip>
          )}
        </div>
      }
      data-test-id={dataTestId}
    />
  );

  render() {
    return (
      <CardWrapper>
        <Card sx={{ overflow: "visible" }}>
          <CardHeader subheader="Advanced search" />
          <CardContent>
            <Box component="table" sx={{ width: "80%", marginBottom: "0px" }}>
              <tbody>{this.searchQueries()}</tbody>
            </Box>
            {this.state.queries.length > 1 && (
              <RadioGroup
                value={this.state.fulfillAll}
                onChange={this.handleCheck}
                // @ts-expect-error pragmatic jsx->tsx conversion
                margin="dense"
              >
                {this.renderFulfillOption("true", "fullfill-all", "Satisfy all conditions")}
                {this.renderFulfillOption("false", "fullfill-one", "Satisfy at least one condition")}
              </RadioGroup>
            )}
          </CardContent>
          <CardActions sx={{ backgroundColor: "#eceff1" }}>
            <span></span>
            <span className="group-right">
              <Button onClick={this.reset} data-test-id="a-search-reset">
                Reset
              </Button>
              <Button onClick={this.validateQueries} variant="contained" color="primary" data-test-id="a-search-submit">
                Search
              </Button>
            </span>
          </CardActions>
        </Card>
      </CardWrapper>
    );
  }
}

AdvancedSearch.contextType = AnalyticsContext;

export default AdvancedSearch;
