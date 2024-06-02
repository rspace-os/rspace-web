// @flow

import React, { useState, useEffect, type Node } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../theme";
import Button from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import styled from "@emotion/styled";
import { createRoot } from "react-dom/client";

import BaseToolbar from "../../components/BaseToolbar";
import BaseSearch from "../../components/BaseSearch";
import DMPToolMenuItem from "../../eln-dmp-integration/DMPTool/DMPToolMenuItem";
import ArgosMenuItem from "../../eln-dmp-integration/Argos/ArgosMenuItem";
import DMPOnlineMenuItem from "../../eln-dmp-integration/DMPOnline/DMPOnlineMenuItem";
import MoveToIrods from "./MoveToIrods";
import Alerts from "../../components/Alerts/Alerts";

const ButtonWrapper = styled.div`
  button {
    color: white;
    border-color: white;
    margin-right: 15px;
    height: 100%;
  }
`;

const domContainer = document.getElementById("toolbar2");

export default function GalleryToolbar(): Node {
  const [galleryMenu, setGalleryMenu] = useState<?EventTarget>(null);

  function openGalleryMenu({ currentTarget }: Event) {
    setGalleryMenu(currentTarget);
  }

  function closeGalleryMenu() {
    setGalleryMenu(null);
  }

  const [importMenu, setImportMenu] = useState<?EventTarget>(null);

  function openImportMenu({ currentTarget }: Event) {
    setImportMenu(currentTarget);
  }

  function closeImportMenu() {
    setImportMenu(null);
  }

  const Content = (
    <>
      <ButtonWrapper>
        <Button
          data-test-id="choose-gallery-btn"
          onClick={(e) => openGalleryMenu(e)}
          variant="outlined"
        >
          Choose Gallery
        </Button>
      </ButtonWrapper>
      <ButtonWrapper>
        <Button
          data-test-id="imprt-btn"
          onClick={(e) => openImportMenu(e)}
          variant="outlined"
        >
          Import
        </Button>
      </ButtonWrapper>
      <h1>
        {domContainer?.getAttribute("data-net-filestores-enabled") === "true"}
      </h1>
      <Menu
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        anchorEl={galleryMenu}
        keepMounted
        open={Boolean(galleryMenu)}
        onClose={closeGalleryMenu}
      >
        <MenuItem
          className="imageButton"
          data-test-id="gallery-image-btn"
          onClick={closeGalleryMenu}
        >
          Image
        </MenuItem>
        <MenuItem
          className="audioButton"
          data-test-id="gallery-audio-btn"
          onClick={closeGalleryMenu}
        >
          Audio
        </MenuItem>
        <MenuItem
          className="videoButton"
          data-test-id="gallery-video-btn"
          onClick={closeGalleryMenu}
        >
          Video
        </MenuItem>
        <MenuItem
          className="documentButton"
          data-test-id="gallery-docs-btn"
          onClick={closeGalleryMenu}
        >
          Docs
        </MenuItem>
        <MenuItem
          className="chemistryButton"
          data-test-id="gallery-misc-btn"
          onClick={closeGalleryMenu}
        >
          Chemistry
        </MenuItem>

        <MenuItem
          className="snippetButton"
          data-test-id="gallery-snippets-btn"
          onClick={closeGalleryMenu}
        >
          Snippets
        </MenuItem>
        <MenuItem
          className="miscButton"
          data-test-id="gallery-misc-btn"
          onClick={closeGalleryMenu}
        >
          Misc
        </MenuItem>
        <MenuItem
          className="exportButton"
          data-test-id="gallery-exports-btn"
          onClick={closeGalleryMenu}
        >
          Exports
        </MenuItem>
        {domContainer?.getAttribute("data-net-filestores-enabled") ===
          "true" && (
          <MenuItem
            className="netButton"
            data-test-id="gallery-filestores-btn"
            onClick={closeGalleryMenu}
          >
            Filestores
          </MenuItem>
        )}
        {domContainer?.getAttribute("data-dmp-enabled") === "true" && (
          <MenuItem
            className="dmpButton"
            data-test-id="gallery-dmp-btn"
            onClick={closeGalleryMenu}
          >
            DMPs
          </MenuItem>
        )}
      </Menu>
      <Menu
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        anchorEl={importMenu}
        keepMounted
        open={Boolean(importMenu)}
        onClose={closeImportMenu}
      >
        <MenuItem
          id="fromLocalComputer"
          data-test-id="gallery-import-local"
          onClick={closeImportMenu}
        >
          File
        </MenuItem>
        <DMPToolMenuItem onClick={closeImportMenu} />
        <ArgosMenuItem onClick={closeImportMenu} />
        <DMPOnlineMenuItem onClick={closeImportMenu} />
        {/*
          <MenuItem id="fromDropbox" style={{display: "none"}} data-test-id="gallery-import-dropbpx" onClick={closeImportMenu}>From Dropbox</MenuItem>
          <MenuItem id="fromBox" style={{display: "none"}} data-test-id="gallery-import-box" onClick={closeImportMenu}>From Box</MenuItem>
          <MenuItem id="fromOneDrive" style={{display: "none"}} data-test-id="gallery-import-one-drive" onClick={closeImportMenu}>From OneDrive</MenuItem>
          <MenuItem id="fromOwnCloud" style={{display: "none"}} data-test-id="gallery-import-own-cloud" onClick={closeImportMenu}>From ownCloud</MenuItem>
*/}
      </Menu>
      <div style={{ flexGrow: "1" }}></div>
      <BaseSearch
        placeholder="By name or unique ID..."
        onSubmit={"filterGallery"}
        elId={"gallery-search-input"}
      />
    </>
  );

  useEffect(() => {
    /*
     * This event tells the jQuery parts of the code that they can assume that
     * the dom nodes managed by react have now been added to the dom.
     */
    window.dispatchEvent(new CustomEvent("ReactToolbarMounted"));
  }, []);

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <BaseToolbar content={Content} />
        <Alerts>
          <MoveToIrods />
        </Alerts>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

let rootNode;

/*
 * We remember the last props so that renderToolbar only needs to be passed the
 * props that have changed
 */
let prevProps = { conditionalRender: {}, eventHandlers: {} };

if (/^\/gallery/.test(window.location.pathname))
  window.renderToolbar = (newProps) => {
    if (!rootNode && domContainer) {
      rootNode = createRoot(domContainer);
    }
    prevProps = {
      conditionalRender: {
        ...prevProps.conditionalRender,
        ...(newProps?.conditionalRender ?? {}),
      },
      eventHandlers: {
        ...prevProps.eventHandlers,
        ...(newProps?.eventHandlers ?? {}),
      },
    };
    rootNode.render(<GalleryToolbar {...prevProps} />);
  };

/*
 * By waiting on window load event, we give the jQuery parts of the code an
 * opportunity to set up event handlers for the ReactToolbarMounted event
 * dispatched above.
 */
window.addEventListener("load", () => {
  window.renderToolbar();
});
