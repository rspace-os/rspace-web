import React, { useEffect } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Paper from "@mui/material/Paper";
import InputBase from "@mui/material/InputBase";
import { makeStyles } from "tss-react/mui";
import IconButton from "@mui/material/IconButton";
import SearchIcon from "@mui/icons-material/Search";
import { createRoot } from "react-dom/client";

const useStyles = makeStyles()((theme) => ({
  paper: {
    boxShadow: "none",
  },
  input: {
    paddingLeft: theme.spacing(1),
    flexGrow: 1,
  },
  form: {
    display: "flex",
    width: "100%",
    alignItems: "center",
    justifyContent: "space-between",
  },
}));

function BaseSearch(props: {
  onSubmit: string | (() => void);
  variant: "elevation" | "outlined";
  elId: string;
  placeholder: string;
}) {
  const { classes } = useStyles();
  const [search, setSearch] = React.useState("");

  const submitSearch = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    if (props.onSubmit) {
      if (typeof props.onSubmit === "function") {
        props.onSubmit();
      } else if (typeof props.onSubmit === "string") {
        // @ts-expect-error yes, this is a bit hacky
        (window.parent[props.onSubmit] as () => void)();
      }
    }
  };

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSearch(event.target.value);
  };

  useEffect(() => {
    document.addEventListener("reset-search-input", function () {
      setSearch("");
    });
  }, []);

  return (
    <Paper
      className={classes.paper}
      variant={props.variant}
      data-test-id="base-search-content"
    >
      <form
        onSubmit={submitSearch}
        className={classes.form}
        data-test-id="base-search-form"
      >
        <InputBase
          id={props.elId}
          data-test-id="base-search-input"
          placeholder={props.placeholder || "Search..."}
          inputProps={{ "aria-label": props.placeholder || "Search" }}
          value={search}
          onChange={handleChange}
          className={classes.input}
        />
        <IconButton
          id={`${props.elId}-submit`}
          type="submit"
          aria-label="Search"
          data-test-id="base-search-submit"
        >
          <SearchIcon />
        </IconButton>
      </form>
    </Paper>
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
export default function WrappedBaseSearch(
  props: React.ComponentProps<typeof BaseSearch>
) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <BaseSearch {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

function renderElements() {
  document.addEventListener("DOMContentLoaded", () => {
    const domContainers = document.getElementsByClassName("base-search");
    if (domContainers.length) {
      [].map.call(domContainers, (container) => {
        const root = createRoot(container);
        root.render(
          <WrappedBaseSearch
            // @ts-expect-error the container will have data attributes
            placeholder={container.dataset.placeholder}
            // @ts-expect-error the container will have data attributes
            onSubmit={container.dataset.onsubmit}
            // @ts-expect-error the container will have data attributes
            elId={container.dataset.elid}
            // @ts-expect-error the container will have data attributes
            variant={container.dataset.variant}
          />
        );
      });
    }
  });
}

renderElements();
