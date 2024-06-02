"use strict";
import React, { useEffect } from "react";
import Button from "@mui/material/Button";
import ChemCard from "./chemCard";
import { makeStyles } from "tss-react/mui";
import styled from "@emotion/styled";
import { createRoot } from "react-dom/client";
import materialTheme from "../theme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";

const useStyles = makeStyles()((theme) => ({
  text: {
    fontSize: "14px !important",
    padding: "2px",
    color: "#1465b7 !important",
  },
  paper: {
    padding: theme.spacing(0),
  },
  cardContent: {
    padding: "0",
    paddingBottom: "0px !important",
  },
  closeAll: {
    padding: "10px",
  },
  closeAllWrapper: {
    textAlign: "right",
  },
}));

const SidebarWrapper = styled.div`
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;

  .MuiPaper-root {
    overflow: visible;
  }
`;

export default function SidebarInfo(props) {
  const [items, setItems] = React.useState([]);
  const [open, setOpen] = React.useState(false);
  const { classes } = useStyles();

  // add click listener on chem elements
  useEffect(() => {
    $(props.iframe.contentDocument).on("click", ".chem", function (e) {
      addItem(e.target);
    });

    // detect removed elements
    watchEditor(props.iframe.contentWindow.document, ".chem", (element) => {
      removeItem(element.id);
    });

    // detect new chem inserted
    document.addEventListener("tinymce-chem-inserted", function (e) {
      var innerDoc =
        props.iframe.contentDocument || props.iframe.contentWindow.document;
      addItem($(innerDoc).find(`img#${e.detail}`)[0]);
    });

    // detect chem updated
    document.addEventListener("tinymce-chem-updated", function (e) {
      closeAll();
    });

    // detect sidebar state open/close
    document.addEventListener("tinymce-chem-sidebar", function (e) {
      setOpen(e.detail);
    });
  }, []);

  // logic for closing and opening the sidebar
  useEffect(() => {
    if (items.length == 1 && !open) {
      openSidebar();
    } else if (items.length == 0 && open) {
      closeSidebar();
    }
  }, [items]);

  const addItem = (element) => {
    let item = {
      id: element.id,
      imageSrc: element.getAttribute("src"),
    };

    setItems((oldItems) => {
      return [item, ...oldItems.filter((i) => i.id != item.id)];
    });
  };

  const removeItem = (id) => {
    setItems((oldItems) => {
      return oldItems.filter((i) => i.id != id);
    });
  };

  const closeAll = (e) => {
    if (e) {
      e.preventDefault();
    }
    setItems([]);
  };

  const openSidebar = () => {
    if (!open) {
      tinymce.activeEditor.execCommand("togglesidebar", false, "cheminfo");
      setOpen(true);
    }
  };

  const closeSidebar = () => {
    if (open) {
      tinymce.activeEditor.execCommand("togglesidebar", false, "cheminfo");
      setOpen(false);
    }
  };

  return items.length ? (
    <>
      <SidebarWrapper>
        {items.length > 1 && (
          <div className={classes.closeAllWrapper}>
            <Button className={classes.closeAll} onClick={closeAll}>
              Close All
            </Button>
          </div>
        )}
        {items.map((item, idx) => (
          <ChemCard key={item.id} onClose={removeItem} item={item} idx={idx} />
        ))}
      </SidebarWrapper>
    </>
  ) : (
    ""
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
function WrappedSidebarInfo(props) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <SidebarInfo {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

// detect iframe load and render elements
document.addEventListener("tinymce-iframe-loaded", function (e) {
  const iframe = $(e.detail)[0];
  let container = $(".tox-sidebar__pane-container")[0];
  const root = createRoot(container);
  root.render(<WrappedSidebarInfo iframe={iframe} />);
});

function watchEditor(containerSelector, elementSelector, callbackRemoved) {
  var onMutationsObserved = function (mutations) {
    mutations.forEach(function (mutation) {
      // if new chem is added
      if (mutation.removedNodes.length) {
        // if a chem is removed
        var element = $(mutation.removedNodes).find(elementSelector);
        if (
          element.prevObject[0].classList &&
          element.prevObject[0].classList.contains("chem")
        ) {
          callbackRemoved(element.prevObject[0]);
        }
      }
    });
  };

  var MutationObserver =
    window.MutationObserver || window.WebKitMutationObserver;
  var observer = new MutationObserver(onMutationsObserved);
  observer.observe($(containerSelector)[0], { childList: true, subtree: true });
}
