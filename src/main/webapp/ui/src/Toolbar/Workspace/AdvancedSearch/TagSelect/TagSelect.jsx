import React, { useEffect } from "react";
import axios from "@/common/axios";
import Select from "react-select";
import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import Paper from "@mui/material/Paper";
import ListItemButton from "@mui/material/ListItemButton";
import { inputBaseClasses } from "@mui/material/InputBase";
import { buttonBaseClasses } from "@mui/material/ButtonBase";
import { chipClasses } from "@mui/material/Chip";
import PropTypes from "prop-types";

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

const inputComponent = React.forwardRef(({ inputRef, ...props }, ref) => (
  <div ref={inputRef || ref} {...props} />
));

inputComponent.displayName = "TagSelectInput";

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
    <ListItemButton
      data-test-id={`a-search-tag-option-${props.children}`}
      className="dropdown-item"
      ref={props.innerRef}
      selected={props.isFocused}
      component="div"
      sx={{ fontWeight: props.isSelected ? 500 : 400 }}
      {...props.innerProps}
    >
      {props.children}
    </ListItemButton>
  );
}

Option.propTypes = {
  children: PropTypes.node,
  innerProps: PropTypes.object,
  innerRef: PropTypes.oneOfType([PropTypes.func, PropTypes.object]),
  isFocused: PropTypes.bool,
  isSelected: PropTypes.bool,
};

function ValueContainer(props) {
  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        flex: 1,
        alignItems: "center",
        overflow: "hidden",
      }}
    >
      {props.children}
    </Box>
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

const RSPACE_ONTOLOGY_URL_DELIMITER = "__RSP_EXTONT_URL_DELIM__";

const replaceCommaDelimiterInTag = (tag) =>
  tag.replaceAll("__rspactags_comma__", ",");

const replaceForwardSlashInTag = (tag) =>
  tag.replaceAll("/", "__rspactags_forsl__");

const parseDelimitedTags = (tag) =>
  tag.includes(RSPACE_ONTOLOGY_URL_DELIMITER)
    ? replaceCommaDelimiterInTag(
        tag.split(RSPACE_ONTOLOGY_URL_DELIMITER)[0].trim(),
      )
    : tag;

export default function TagSelect(props) {
  const theme = useTheme();
  const [multi, setMulti] = React.useState(null);
  const [suggestions, setSuggestions] = React.useState([]);

  function handleChangeMulti(value) {
    setMulti(value);
    props.updateSelected(value, "value");
  }

  const selectStyles = {
    container: (base) => ({
      ...base,
      width: "100%",
    }),
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

      const tags = result.data.data.map((suggestion) => {
        const label = parseDelimitedTags(suggestion);
        return { value: replaceForwardSlashInTag(label), label };
      });
      setSuggestions(tags);

      if (props.selected) {
        const localSelected = props.selected
          .split("<<>>")
          .map((s) => tags.find((r) => r.label === s));
        handleChangeMulti(localSelected);
      }
    };
    fetchData();
  }, []);

  return (
    <Box
      sx={{
        display: "flex",
        flexGrow: 1,
        "& .myReactSelect .Select-arrow-zone": {
          display: "none",
        },
        "& .advanced-search": {
          [`& .search-input .${inputBaseClasses.input}`]: {
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
        [`& .${buttonBaseClasses.root}:not(.${chipClasses.root})`]: {
          width: "fit-content !important",
          fontSize: 15,
          marginLeft: "10px",
        },
      }}
    >
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
