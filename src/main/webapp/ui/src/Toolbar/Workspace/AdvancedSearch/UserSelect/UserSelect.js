import React, { useEffect } from "react";
import axios from "axios";
import { emphasize, useTheme } from "@mui/material/styles";
import { makeStyles } from "tss-react/mui";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import Paper from "@mui/material/Paper";
import MenuItem from "@mui/material/MenuItem";
import PropTypes from "prop-types";
import Autocomplete from "@mui/material/Autocomplete";

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
    marginTop: theme.spacing(1),
    left: 0,
    right: 0,
  },
  autocomplete: {
    flexGrow: 1,
    marginTop: "9px",
  },
  popupIndicator: {
    height: "unset !important",
    width: "unset !important",
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
      data-test-id={`search-owner-option-${props.children.split("- ").pop()}`} // only use the username
      ref={props.innerRef}
      selected={props.isFocused}
      component="div"
      className="dropdown-item"
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

export default function UserSelect(props) {
  const { classes } = useStyles();
  const theme = useTheme();
  const [multi, setMulti] = React.useState([]);
  const [suggestions, setSuggestions] = React.useState([]);

  function handleChangeMulti(value) {
    setMulti(value);
    props.updateSelected(value, "username");
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
        "/workspace/ajax/getViewablePublicUserInfoList"
      );

      setSuggestions(result.data.data);
      if (props.selected) {
        let selected = props.selected.split("<<>>");
        let local_selected = [];
        selected.map((s) => {
          let idx = result.data.data.findIndex((r) => r.username == s);
          local_selected.push(result.data.data[idx]);
        });
        handleChangeMulti(local_selected);
      }
    };

    fetchData();
  }, []);

  return (
    <Autocomplete
      options={suggestions}
      getOptionLabel={(option) =>
        `${typeof option.firstName !== "undefined" ? option.firstName : ""} ${
          typeof option.lastName !== "undefined" ? option.lastName : ""
        } - ${option.username}`
      }
      onChange={(_, selection) => handleChangeMulti(selection)}
      renderInput={(props) => (
        <TextField
          {...props}
          variant="standard"
          placeholder={props.error ?? "Select owner(s)"}
        />
      )}
      classes={{
        root: classes.autocomplete,
        popupIndicator: classes.popupIndicator,
      }}
      size="small"
      multiple
      value={multi}
      dataTestId={props.testId}
    />
  );
}
