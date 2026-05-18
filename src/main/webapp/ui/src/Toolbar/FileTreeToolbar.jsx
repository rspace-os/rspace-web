"use strict";
import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import styled from "@emotion/styled";
import { createRoot } from "react-dom/client";

import BaseToolbar from "../components/BaseToolbar";
import TreeSort from "../components/TreeSort";

const ToolbarWrapper = styled.div`
  display: flex;
  width: 100%;
  .sortingSettings {
    justify-content: flex-start;
    .MuiSelect-selectMenu {
      padding-left: 5px !important;
    }
  }
  ul {
    position: absolute;
    right: 0;
    height: 100%;
    top: 0;
    margin: 0;
    display: flex;
    align-items: center;
    color: white;
    list-style: none;
    padding: 0px 5px;
    a {
      color: white;
      font-size: 18px;
    }
  }
`;

export default function FileTreeToolbar() {
  function content() {
    return (
      <ToolbarWrapper>
        <TreeSort />
        <ul>
          <li>
            <a
              id="hideFileTreeSmall"
              href="#"
              className="rs-actionbar__item rs-actionbar__item--icon"
              title="Hide tree browser"
              data-test-id="hide-tree"
              aria-label="Hide tree browser"
            >
              <span className="glyphicon glyphicon-menu-right"></span>
            </a>
          </li>
        </ul>
      </ToolbarWrapper>
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
