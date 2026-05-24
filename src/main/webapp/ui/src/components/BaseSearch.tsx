import React, { useEffect } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Paper from "@mui/material/Paper";
import InputBase from "@mui/material/InputBase";
import IconButton from "@mui/material/IconButton";
import SearchIcon from "@mui/icons-material/Search";
import { createRoot } from "react-dom/client";

function BaseSearch(props: {
  onSubmit?: string | (() => void);
  variant?: "elevation" | "outlined";
  elId: string;
  placeholder: string;
}) {
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
      sx={{ boxShadow: "none" }}
      variant={props.variant}
      data-test-id="base-search-content"
    >
      <form
        onSubmit={submitSearch}
        style={{
          display: "flex",
          width: "100%",
          alignItems: "center",
          justifyContent: "space-between",
        }}
        data-test-id="base-search-form"
      >
        <InputBase
          id={props.elId}
          data-test-id="base-search-input"
          placeholder={props.placeholder || "Search..."}
          slotProps={{
            input: { "aria-label": props.placeholder || "Search" },
          }}
          value={search}
          onChange={handleChange}
          sx={{ pl: 1, flexGrow: 1 }}
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
    <StyledEngineProvider injectFirst enableCssLayer>
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
        const htmlContainer = container as HTMLElement;
        const { dataset } = htmlContainer;
        const variant =
          dataset.variant === "outlined" || dataset.variant === "elevation"
            ? dataset.variant
            : undefined;
        const root = createRoot(container);
        root.render(
          <WrappedBaseSearch
            placeholder={dataset.placeholder ?? "Search..."}
            onSubmit={dataset.onsubmit}
            elId={dataset.elid ?? "base-search"}
            variant={variant}
          />
        );
      });
    }
  });
}

renderElements();
