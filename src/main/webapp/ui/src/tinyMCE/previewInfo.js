"use strict";
import React, { useState, useEffect } from "react";
import ChemCard from "./chemCard";
import { createRoot } from "react-dom/client";
import { makeStyles } from "tss-react/mui";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import createAccentedTheme from "../accentedTheme";
import { ACCENT_COLOR } from "../assets/branding/chemistry";
import StoichiometryTable from "./stoichiometry/table";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";

const useStyles = makeStyles()((theme) => ({
  wrapper: {
    display: "table",
    border: "1px solid rgb(200,200,200)",
    borderCollapse: "collapse",
    margin: "10px 0px",
  },
}));

export default function PreviewInfo(props) {
  const { classes } = useStyles();
  useEffect(() => {
    document.dispatchEvent(new Event("images-replaced"));
  }, []);
  return (
    <span className={classes.wrapper}>
      <Stack>
        <div
          style={{ display: "flex", minHeight: "200px", maxHeight: "334px" }}
        >
          <div style={{ alignSelf: "center" }}>
            <img
              id={props.item.id}
              className={props.item.class}
              src={props.item.src}
              width={props.item.width}
              height={props.item.height}
              data-rsrevision={props.item["data-rsrevision"]}
              data-fullwidth={props.item["data-fullwidth"]}
              data-fullheight={props.item["data-fullheight"]}
              data-chemfileid={props.item["data-chemfileid"]}
              data-foo={props.item["data-has-stoichiometry-table"]}
            />
          </div>
          <ChemCard item={props.item} inline />
        </div>
        {props.item["data-has-stoichiometry-table"] && (
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <Analytics>
                <ErrorBoundary>
                  <Alerts>
                    <StoichiometryTable chemId={props.item.id} />
                  </Alerts>
                </ErrorBoundary>
              </Analytics>
            </ThemeProvider>
          </StyledEngineProvider>
        )}
      </Stack>
    </span>
  );
}

function render(attributes, element) {
  const root = createRoot(element);
  root.render(<PreviewInfo item={{ ...attributes }} />);
}

document.addEventListener("document-placed", function (e) {
  $.fn.getAttributes = function () {
    var attributes = {};
    if (this.length) {
      $.each(this[0].attributes, function (i, attr) {
        attributes[attr.name] = attr.value;
      });
    }
    return attributes;
  };
  let domElements = e.detail ? $(`#div_${e.detail} img.chem`) : $("img.chem");

  domElements.each((i) => {
    let domContainer = $(domElements[i]);
    let parent = domContainer.parent();
    let attributes = domContainer.getAttributes();
    parent.find("img.chem").remove();
    let contents = parent.html();

    const root = createRoot(parent[0]);
    root.render(<PreviewInfo item={{ ...attributes }} />);
    parent.append(contents);
  });

  // Tell React that a new document was placed into the dom
  document.dispatchEvent(new Event("images-replaced"));
});

document.addEventListener("chem-updated", function (e) {
  $.fn.getAttributes = function () {
    var attributes = {};
    if (this.length) {
      $.each(this[0].attributes, function (i, attr) {
        attributes[attr.name] = attr.value;
      });
    }
    return attributes;
  };

  let domElements = e.detail ? $(`#div_${e.detail} img.chem`) : $("img.chem");

  domElements.each((i) => {
    let domContainer = $(domElements[i]);
    let span = domContainer.closest("span");
    render(domContainer.getAttributes(), span[0]);
  });
});
