import React, { useEffect } from "react";
import axios from "@/common/axios";
import Select from "react-select";
import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import Paper from "@mui/material/Paper";
import MenuItem from "@mui/material/MenuItem";
import PropTypes from "prop-types";

const wrapperSx = {
  display: "flex",
  flexGrow: 1,
  "& .css-1pcexqc-container": {
    flexGrow: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  "& .myReactSelect .Select-arrow-zone": {
    display: "none",
  },
  "& .advanced-search": {
    "& .search-input .MuiInputBase-input": {
      height: 32,
    },
    "& .dropdown-item": {
      margin: 0,
      width: "100%",
    },
  },
  "& .simple-search": {
    "& .search-input": {
      marginTop: "3px",
      minHeight: "36px",
    },
  },
  "& .MuiButtonBase-root:not(.MuiChip-root)": {
    width: "fit-content !important",
    fontSize: 15,
    marginLeft: "10px",
  },
};

function NoOptionsMessage(props) {
  return (
    <Typography
      color="textSecondary"
      sx={{ p: "8px 16px" }}
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
    selectProps: { TextFieldProps },
  } = props;

  return (
    <TextField
      variant="standard"
      data-test-id={props.selectProps.testId}
      className="search-input"
      fullWidth
      {...TextFieldProps}
      slotProps={{
        input: {
          inputComponent,
          inputProps: {
            style: { display: "flex", padding: 0, height: "100%" },
            ref: innerRef,
            children,
            ...innerProps,
          },
        },
      }}
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
      sx={{ position: "absolute", left: 2, bottom: 6, fontSize: 16 }}
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
    <div
      style={{
        display: "flex",
        flexWrap: "wrap",
        flex: 1,
        alignItems: "center",
        overflow: "hidden",
      }}
    >
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
      sx={{
        position: "absolute",
        zIndex: 1,
        width: "fit-content !important",
        mt: 1,
        left: 0,
        right: 0,
      }}
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
        "/workspace/editor/structuredDocument/userTags",
      );
      const RSPACE_ONTOLOGY_URL_DELIMITER = "__RSP_EXTONT_URL_DELIM__";
      const parseDelimitedTags = (tag) => {
        if (tag.includes(RSPACE_ONTOLOGY_URL_DELIMITER)) {
          return replaceCommaDelimiterInTag(
            tag.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0].trim(),
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

      const tags = result.data.data.map((suggestion) => ({
        value: replaceForwardSlashInTag(parseDelimitedTags(suggestion)),
        label: parseDelimitedTags(suggestion),
      }));

      setSuggestions(tags);

      if (props.selected) {
        const selected = props.selected.split("<<>>");
        const local_selected = [];
        selected.map((s) => {
          const idx = tags.findIndex((r) => r.label == s);
          local_selected.push(tags[idx]);
        });
        handleChangeMulti(local_selected);
      }
    };
    fetchData();
  }, []);

  return (
    <Box sx={wrapperSx}>
      <Select
        className={props.advanced ? "advanced-search" : "simple-search"}
        styles={selectStyles}
        options={suggestions}
        components={components}
        value={multi}
        onChange={handleChangeMulti}
        isMulti
        placeholder={props.error || "Select tag(s)"}
        testId={props.testId}
      />
    </Box>
  );
}
