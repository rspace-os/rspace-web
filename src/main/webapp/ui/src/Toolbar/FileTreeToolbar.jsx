"use strict";
import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import { createRoot } from "react-dom/client";

import BaseToolbar from "../components/BaseToolbar";
import TreeSort from "../components/TreeSort";

/**
 * Toolbar shown above the file tree browser.
 */
export default function FileTreeToolbar() {
  /**
   * Content rendered inside the toolbar chrome.
   */
  function content() {
    return (
      <div style={{ display: "flex", width: "100%" }}>
        <TreeSort justifyContent="flex-start" selectPaddingLeft={5} />
        <ul
          style={{
            position: "absolute",
            right: 0,
            height: "100%",
            top: 0,
            margin: 0,
            display: "flex",
            alignItems: "center",
            color: "white",
            listStyle: "none",
            padding: "0px 5px",
          }}
        >
          <li>
            <button
              type="button"
              id="hideFileTreeSmall"
              className="rs-actionbar__item rs-actionbar__item--icon"
              title="Hide tree browser"
              data-test-id="hide-tree"
              aria-label="Hide tree browser"
              style={{
                color: "white",
                fontSize: "18px",
                background: "transparent",
                border: 0,
                padding: 0,
                cursor: "pointer",
              }}
            >
              <span className="glyphicon glyphicon-menu-right"></span>
            </button>
          </li>
        </ul>
      </div>
    );
  }

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <BaseToolbar content={content()} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("fileTreeToolbar");
const root = createRoot(domContainer);
root.render(<FileTreeToolbar />);
