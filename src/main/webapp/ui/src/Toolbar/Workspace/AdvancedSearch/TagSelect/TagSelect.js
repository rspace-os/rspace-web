import React, { useEffect } from "react";
import axios from "@/common/axios";
import Select from "react-select";
import { emphasize, useTheme } from "@mui/material/styles";
import { makeStyles } from "tss-react/mui";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import Paper from "@mui/material/Paper";
import MenuItem from "@mui/material/MenuItem";
import PropTypes from "prop-types";
import styled from "@emotion/styled";

const Wrapper = styled.div`
  display: flex;
  flex-grow: 1;
  .css-1pcexqc-container {
    flex-grow: 1;
    align-items: center;
    justify-content: center;
  }
  .myReactSelect .Select-arrow-zone {
    display: none;
  }
  .advanced-search {
    .search-input .MuiInputBase-input {
      height: 32px;
    }
    .dropdown-item {
      margin: 0px;
      width: 100%;
    }
  }
  .simple-search {
    .search-input {
      margin-top: 3px;
      min-height: 36px;
    }
  }

  .MuiButtonBase-root:not(.MuiChip-root) {
    width: fit-content !important;
    font-size: 15px;
    margin-left: 10px;
  }
`;

const useStyles = makeStyles()((theme) => ({
  input: {
    display: "flex",
    padding: 0,
    height: "100%",
  },
  valueContainer: {
    display: "flex",
    flexWrap: "wrap",
    flex: 1,
    alignItems: "center",
    overflow: "hidden",
  },
  chip: {
    margin: theme.spacing(0.5, 0.25),
  },
  chipFocused: {
    backgroundColor: emphasize(
      theme.palette.type === "light"
        ? theme.palette.grey[300]
        : theme.palette.grey[700],
      0.08
    ),
  },
  noOptionsMessage: {
    padding: theme.spacing(1, 2),
  },
  placeholder: {
    position: "absolute",
    left: 2,
    bottom: 6,
    fontSize: 16,
  },
  paper: {
    position: "absolute",
    zIndex: 1,
    width: "fit-content !important",
    marginTop: theme.spacing(1),
    left: 0,
    right: 0,
  },
}));

function NoOptionsMessage(props) {
  return (
    <Typography
      color="textSecondary"
      className={props.selectProps.classes.noOptionsMessage}
      {...props.innerProps}
    >
      {props.children}
    </Typography>
  );
}

NoOptionsMessage.propTypes = {
  children: PropTypes.node,
  innerProps: PropTypes.object,
  selectProps: PropTypes.object.isRequired,
};

function inputComponent({ inputRef, ...props }) {
  return <div ref={inputRef} {...props} />;
}

inputComponent.propTypes = {
  inputRef: PropTypes.oneOfType([PropTypes.func, PropTypes.object]),
};

function Control(props) {
  const {
    children,
    innerProps,
    innerRef,
    selectProps: { classes, TextFieldProps },
  } = props;

  return (
    <TextField
      variant="standard"
      data-test-id={props.selectProps.testId}
      className="search-input"
      fullWidth
      InputProps={{
        inputComponent,
        inputProps: {
          className: classes.input,
          ref: innerRef,
          children,
          ...innerProps,
        },
      }}
      {...TextFieldProps}
    />
  );
}

Control.propTypes = {
  children: PropTypes.node,
  innerProps: PropTypes.object,
  innerRef: PropTypes.oneOfType([PropTypes.func, PropTypes.object]),
  selectProps: PropTypes.object.isRequired,
};

function Option(props) {
  return (
    <MenuItem
      data-test-id={`a-search-tag-option-${props.children}`}
      className="dropdown-item"
      ref={props.innerRef}
      selected={props.isFocused}
      component="div"
      style={{ fontWeight: props.isSelected ? 500 : 400 }}
      {...props.innerProps}
    >
      {props.children}
    </MenuItem>
  );
}

Option.propTypes = {
  children: PropTypes.node,
  innerProps: PropTypes.object,
  innerRef: PropTypes.oneOfType([PropTypes.func, PropTypes.object]),
  isFocused: PropTypes.bool,
  isSelected: PropTypes.bool,
};

function Placeholder(props) {
  return (
    <Typography
      color="textSecondary"
      className={props.selectProps.classes.placeholder}
      {...props.innerProps}
    >
      {props.children}
    </Typography>
  );
}

Placeholder.propTypes = {
  children: PropTypes.node,
  innerProps: PropTypes.object,
  selectProps: PropTypes.object.isRequired,
};

function ValueContainer(props) {
  return (
    <div className={props.selectProps.classes.valueContainer}>
      {props.children}
    </div>
  );
}

ValueContainer.propTypes = {
  children: PropTypes.node,
  selectProps: PropTypes.object.isRequired,
};

function MultiValue(props) {
  return <span>{`${props.children}, `}</span>;
}

MultiValue.propTypes = {
  children: PropTypes.node,
  isFocused: PropTypes.bool,
  removeProps: PropTypes.object.isRequired,
  selectProps: PropTypes.object.isRequired,
};

function Menu(props) {
  return (
    <Paper
      square
      className={props.selectProps.classes.paper}
      {...props.innerProps}
    >
      {props.children}
    </Paper>
  );
}

Menu.propTypes = {
  children: PropTypes.node,
  innerProps: PropTypes.object,
  selectProps: PropTypes.object,
};

const components = {
  Control,
  Menu,
  MultiValue,
  NoOptionsMessage,
  Option,
  ValueContainer,
  DropdownIndicator: null,
};

export default function TagSelect(props) {
  const { classes } = useStyles();
  const theme = useTheme();
  const [multi, setMulti] = React.useState(null);
  const [suggestions, setSuggestions] = React.useState([]);

  function handleChangeMulti(value) {
    setMulti(value);
    props.updateSelected(value, "value");
  }

  const selectStyles = {
    input: (base) => ({
      ...base,
      color: theme.palette.text.primary,
    }),
  };

  useEffect(() => {
    const fetchData = async () => {
      const result = await axios(
        "/workspace/editor/structuredDocument/userTags"
      );
      const RSPACE_ONTOLOGY_URL_DELIMITER = "__RSP_EXTONT_URL_DELIM__";
      const parseDelimitedTags = (tag) => {
        if (tag.includes(RSPACE_ONTOLOGY_URL_DELIMITER)) {
          return replaceCommaDelimiterInTag(
            tag.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0].trim()
          );
        }
        return tag;
      };
      const replaceForwardSlashInTag = (tag) => {
        tag = tag.replaceAll("/", "__rspactags_forsl__");
        return tag;
      };
      const replaceCommaDelimiterInTag = (tag) => {
        tag = tag.replaceAll("__rspactags_comma__", ",");
        return tag;
      };

      let tags = result.data.data.map((suggestion) => ({
        value: replaceForwardSlashInTag(parseDelimitedTags(suggestion)),
        label: parseDelimitedTags(suggestion),
      }));

      setSuggestions(tags);

      if (props.selected) {
        let selected = props.selected.split("<<>>");
        let local_selected = [];
        selected.map((s) => {
          let idx = tags.findIndex((r) => r.label == s);
          local_selected.push(tags[idx]);
        });
        handleChangeMulti(local_selected);
      }
    };
    fetchData();
  }, []);

  return (
    <Wrapper>
      <Select
        className={props.advanced ? "advanced-search" : "simple-search"}
        classes={classes}
        styles={selectStyles}
        options={suggestions}
        components={components}
        value={multi}
        onChange={handleChangeMulti}
        isMulti
        placeholder={props.error || "Select tag(s)"}
        testId={props.testId}
      />
    </Wrapper>
  );
}
