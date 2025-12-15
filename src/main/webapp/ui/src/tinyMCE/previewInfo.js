"use strict";
import React, { useEffect } from "react";
import ChemCard from "./chemCard";
import { createRoot } from "react-dom/client";
import { makeStyles } from "tss-react/mui";
import Stack from "@mui/material/Stack";
import { ThemeProvider, styled } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import createAccentedTheme from "../accentedTheme";
import { ACCENT_COLOR } from "../assets/branding/chemistry";
import StoichiometryTable from "./stoichiometry/table";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import Box from "@mui/material/Box";
import Alert from "@mui/material/Alert";
import { useIntegrationIsAllowedAndEnabled } from "../hooks/api/integrationHelpers";
import * as FetchingData from "../util/fetchingData";
import CssBaseline from "@mui/material/CssBaseline";

const useStyles = makeStyles()(() => ({
  wrapper: {
    margin: "10px 0px",
  },
}));

const Div = styled("div")(() => {});

export default function PreviewInfo(props) {
  const { classes } = useStyles();
  const chemistryStatus = useIntegrationIsAllowedAndEnabled("CHEMISTRY");

  useEffect(() => {
    document.dispatchEvent(new Event("images-replaced"));
  }, []);
  const theme = createAccentedTheme(ACCENT_COLOR);
  return (
    <StyledEngineProvider injectFirst>
      <CssBaseline />
      <ThemeProvider theme={theme}>
        <span className={classes.wrapper}>
          <Stack
            sx={{
              border: `2px solid ${theme.palette.primary.background}`,
              borderRadius: 1,
              backgroundColor: "#fafafa",
            }}
          >
            <div
              style={{
                display: "flex",
                minHeight: "200px",
                maxHeight: "334px",
              }}
            >
              <Div sx={{ alignSelf: "center", mx: "auto" }}>
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
                  data-stoichiometry-table={
                    props.item["data-stoichiometry-table"]
                  }
                />
              </Div>
              <ChemCard item={props.item} inline />
            </div>
            {props.item["data-stoichiometry-table"] && (
              <Analytics>
                <ErrorBoundary>
                  <Alerts>
                    <Box
                      sx={{
                        overflow: "hidden",
                      }}
                    >
                      {FetchingData.match(chemistryStatus, {
                        loading: () => (
                          <Box p={2}>
                            <Alert severity="info">
                              Checking chemistry integration status...
                            </Alert>
                          </Box>
                        ),
                        error: (error) => (
                          <Box p={2}>
                            <Alert severity="error">
                              Error checking chemistry integration: {error}
                            </Alert>
                          </Box>
                        ),
                        success: (isEnabled) => {
                          let stoichiometryId;
                          let stoichiometryVersion;
                          try {
                            const { id, revision } = JSON.parse(
                              props.item["data-stoichiometry-table"],
                            );
                            stoichiometryId = id;
                            stoichiometryVersion = revision;
                          } catch {}
                          return isEnabled ? (
                            <StoichiometryTable
                              chemId={props.item.id}
                              stoichiometryId={stoichiometryId}
                              stoichiometryRevision={stoichiometryVersion}
                            />
                          ) : (
                            <Box p={2}>
                              <Alert severity="warning">
                                Chemistry integration is not enabled. Please
                                contact your administrator to enable it.
                              </Alert>
                            </Box>
                          );
                        },
                      })}
                    </Box>
                  </Alerts>
                </ErrorBoundary>
              </Analytics>
            )}
          </Stack>
        </span>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

function render(attributes, element) {
  const root = createRoot(element);
  root.render(<PreviewInfo item={{ ...attributes }} />);
}

document.addEventListener("document-placed", (e) => {
  $.fn.getAttributes = function () {
    const attributes = {};
    if (this.length) {
      $.each(this[0].attributes, (i, attr) => {
        attributes[attr.name] = attr.value;
      });
    }
    return attributes;
  };
  const domElements = e.detail ? $(`#div_${e.detail} img.chem`) : $("img.chem");

  domElements.each((i) => {
    const domContainer = $(domElements[i]);
    const parent = domContainer.parent();
    const attributes = domContainer.getAttributes();
    parent.find("img.chem").remove();
    const contents = parent.html();

    const root = createRoot(parent[0]);
    root.render(<PreviewInfo item={{ ...attributes }} />);
    parent.append(contents);
  });

  // Tell React that a new document was placed into the dom
  document.dispatchEvent(new Event("images-replaced"));
});

document.addEventListener("chem-updated", (e) => {
  $.fn.getAttributes = function () {
    const attributes = {};
    if (this.length) {
      $.each(this[0].attributes, (i, attr) => {
        attributes[attr.name] = attr.value;
      });
    }
    return attributes;
  };

  const domElements = e.detail ? $(`#div_${e.detail} img.chem`) : $("img.chem");

  domElements.each((i) => {
    const domContainer = $(domElements[i]);
    const span = domContainer.closest("span");
    render(domContainer.getAttributes(), span[0]);
  });
});
