import React, { useEffect } from "react";
import ChemCard from "./chemCard";
import { createRoot } from "react-dom/client";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import createAccentedTheme from "../accentedTheme";
import { ACCENT_COLOR } from "../assets/branding/chemistry";
import StoichiometryTable from "./stoichiometry/StoichiometryTable";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import Box from "@mui/material/Box";
import Alert from "@mui/material/Alert";
import { useIntegrationIsAllowedAndEnabled } from "../hooks/api/integrationHelpers";
import * as FetchingData from "../util/fetchingData";
import CssBaseline from "@mui/material/CssBaseline";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

declare const $: any;

type PreviewInfoItem = Record<string, string | undefined>;

interface PreviewInfoProps {
  item: PreviewInfoItem;
}

const queryClient = new QueryClient();

export default function PreviewInfo({ item }: PreviewInfoProps) {
  const chemistryStatus = useIntegrationIsAllowedAndEnabled("CHEMISTRY");

  useEffect(() => {
    document.dispatchEvent(new Event("images-replaced"));
  }, []);

  const theme = createAccentedTheme(ACCENT_COLOR);

  return (
    <QueryClientProvider client={queryClient}>
      <StyledEngineProvider injectFirst>
        <CssBaseline />
        <ThemeProvider theme={theme}>
          <Box component="span" sx={{ margin: "10px 0px", display: "block" }}>
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
                <Box sx={{ alignSelf: "center", mx: "auto" }}>
                  <img
                    id={item.id}
                    className={item["class"]}
                    src={item.src}
                    width={item.width}
                    height={item.height}
                    data-rsrevision={item["data-rsrevision"]}
                    data-fullwidth={item["data-fullwidth"]}
                    data-fullheight={item["data-fullheight"]}
                    data-chemfileid={item["data-chemfileid"]}
                    data-stoichiometry-table={item["data-stoichiometry-table"]}
                  />
                </Box>
                <ChemCard item={item} inline />
              </div>
              {item["data-stoichiometry-table"] && (
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
                                Error checking chemistry integration: {String(error)}
                              </Alert>
                            </Box>
                          ),
                          success: (isEnabled) => {
                            let stoichiometryId: number | undefined;
                            let stoichiometryVersion: number | undefined;
                            const stoichiometryRaw =
                              item["data-stoichiometry-table"];
                            if (typeof stoichiometryRaw === "string") {
                              try {
                                const { id, revision } = JSON.parse(
                                  stoichiometryRaw,
                                ) as {
                                  id?: number | string;
                                  revision?: number | string;
                                };
                                const parsedId = Number(id);
                                const parsedRevision = Number(revision);
                                if (
                                  Number.isFinite(parsedId) &&
                                  Number.isFinite(parsedRevision)
                                ) {
                                  stoichiometryId = parsedId;
                                  stoichiometryVersion = parsedRevision;
                                }
                              } catch {}
                            }

                            if (!isEnabled) {
                              return (
                                <Box p={2}>
                                  <Alert severity="warning">
                                    Chemistry integration is not enabled. Please
                                    contact your administrator to enable it.
                                  </Alert>
                                </Box>
                              );
                            }

                            if (
                              stoichiometryId === undefined ||
                              stoichiometryVersion === undefined
                            ) {
                              return (
                                <Box p={2}>
                                  <Alert severity="error">
                                    Invalid stoichiometry metadata provided.
                                  </Alert>
                                </Box>
                              );
                            }

                            return (
                              <StoichiometryTable
                                chemId={item.id}
                                stoichiometryId={stoichiometryId}
                                stoichiometryRevision={stoichiometryVersion}
                              />
                            );
                          },
                        })}
                      </Box>
                    </Alerts>
                  </ErrorBoundary>
                </Analytics>
              )}
            </Stack>
          </Box>
        </ThemeProvider>
      </StyledEngineProvider>
    </QueryClientProvider>
  );
}

function render(attributes: PreviewInfoItem, element: Element) {
  const root = createRoot(element as HTMLElement);
  root.render(<PreviewInfo item={{ ...attributes }} />);
}

document.addEventListener(
  "document-placed",
  (event: Event & { detail?: string | number }) => {
    $.fn.getAttributes = function () {
      const attributes: PreviewInfoItem = {};
      if (this.length) {
        $.each(this[0].attributes, (i: number, attr: { name: string; value: string }) => {
          attributes[attr.name] = attr.value;
        });
      }
      return attributes;
    };
    const domElements = event.detail
      ? $(`#div_${event.detail} img.chem`)
      : $("img.chem");

    domElements.each((i: number) => {
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
  },
);

document.addEventListener(
  "chem-updated",
  (event: Event & { detail?: string | number }) => {
    $.fn.getAttributes = function () {
      const attributes: PreviewInfoItem = {};
      if (this.length) {
        $.each(this[0].attributes, (i: number, attr: { name: string; value: string }) => {
          attributes[attr.name] = attr.value;
        });
      }
      return attributes;
    };

    const domElements = event.detail
      ? $(`#div_${event.detail} img.chem`)
      : $("img.chem");

    domElements.each((i: number) => {
      const domContainer = $(domElements[i]);
      const span = domContainer.closest("span");
      render(domContainer.getAttributes(), span[0]);
    });
  },
);