import React, { useEffect } from "react";
import ChemCard from "./ChemCard";
import { createRoot } from "react-dom/client";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import CssBaseline from "@mui/material/CssBaseline";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import createAccentedTheme from "../accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/chemistry";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useIntegrationIsAllowedAndEnabled } from "../hooks/api/integrationHelpers";
import * as FetchingData from "../util/fetchingData";
import StoichiometryTable from "./stoichiometry/StoichiometryTable";

type PreviewInfoItem = Record<string, string | undefined>;

const STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE = "data-stoichiometry-table-only";
const PREVIEW_INFO_BORDER_COLOR = `hsl(${ACCENT_COLOR.background.hue} ${ACCENT_COLOR.background.saturation}% ${ACCENT_COLOR.background.lightness}%)`;
const queryClient = new QueryClient();

function getStoichiometryReference(raw: string | undefined) {
  if (typeof raw !== "string") {
    return null;
  }

  try {
    const { id, revision } = JSON.parse(raw) as {
      id?: number | string;
      revision?: number | string;
    };
    const parsedId = typeof id === "number" ? id : Number(id);
    const parsedRevision =
      typeof revision === "number" ? revision : Number(revision);

    if (Number.isNaN(parsedId) || Number.isNaN(parsedRevision)) {
      return null;
    }

    return {
      id: parsedId,
      revision: parsedRevision,
    };
  } catch {
    return null;
  }
}

function getAttributes(domNode: Element | null): PreviewInfoItem {
  const attributes: PreviewInfoItem = {};
  if (domNode) {
    Array.from(domNode.attributes).forEach((attr) => {
      attributes[attr.name] = attr.value;
    });
  }
  return attributes;
}

function getPreviewNodes(detail?: string | number): Array<HTMLElement> {
  const selector = `img.chem, div[${STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE}="true"]`;
  const container =
    typeof detail === "string" || typeof detail === "number"
      ? document.getElementById(`div_${detail}`)
      : document;

  if (!container) {
    return [];
  }

  return Array.from(container.querySelectorAll<HTMLElement>(selector));
}

export function StoichiometryPreviewSection({
  stoichiometryId,
  stoichiometryRevision,
}: {
  stoichiometryId: number;
  stoichiometryRevision: number;
}) {
  const chemistryStatus = useIntegrationIsAllowedAndEnabled("CHEMISTRY");

  return (
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
              success: (isEnabled) =>
                isEnabled ? (
                  <StoichiometryTable
                    stoichiometryId={stoichiometryId}
                    stoichiometryRevision={stoichiometryRevision}
                  />
                ) : (
                  <Box p={2}>
                    <Alert severity="warning">
                      Chemistry integration is not enabled. Please contact your
                      administrator to enable it.
                    </Alert>
                  </Box>
                ),
            })}
          </Box>
        </Alerts>
      </ErrorBoundary>
    </Analytics>
  );
}

function PreviewInfoFrame({
  children,
  isTableOnly = false,
}: {
  children: React.ReactNode;
  isTableOnly?: boolean;
}) {
  const theme = createAccentedTheme(ACCENT_COLOR);

  return (
    <QueryClientProvider client={queryClient}>
      <StyledEngineProvider injectFirst>
        <CssBaseline />
        <ThemeProvider theme={theme}>
          <Box
            component="span"
            data-stoichiometry-preview={isTableOnly ? "true" : undefined}
            sx={{ margin: "10px 0px", display: "block" }}
          >
            <Stack
              sx={{
                border: `2px solid ${PREVIEW_INFO_BORDER_COLOR}`,
                borderRadius: 1,
                backgroundColor: "#fafafa",
              }}
            >
              {children}
            </Stack>
          </Box>
        </ThemeProvider>
      </StyledEngineProvider>
    </QueryClientProvider>
  );
}

export default function PreviewInfo({
  item,
}: {
  item: PreviewInfoItem;
}) {
  const stoichiometryReference = getStoichiometryReference(
    item["data-stoichiometry-table"],
  );
  const isTableOnly = item[STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE] === "true";

  useEffect(() => {
    if (!isTableOnly) {
      document.dispatchEvent(new Event("images-replaced"));
    }
  }, [isTableOnly]);

  if (isTableOnly && stoichiometryReference) {
    return (
      <PreviewInfoFrame isTableOnly>
        <StoichiometryPreviewSection
          stoichiometryId={stoichiometryReference.id}
          stoichiometryRevision={stoichiometryReference.revision}
        />
      </PreviewInfoFrame>
    );
  }

  return (
    <PreviewInfoFrame>
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
      {stoichiometryReference && (
        <StoichiometryPreviewSection
          stoichiometryId={stoichiometryReference.id}
          stoichiometryRevision={stoichiometryReference.revision}
        />
      )}
    </PreviewInfoFrame>
  );
}

function render(attributes: PreviewInfoItem, element: Element) {
  const root = createRoot(element as HTMLElement);
  root.render(<PreviewInfo item={{ ...attributes }} />);
}

function renderChemPreview(domContainer: HTMLImageElement) {
  const parent = domContainer.parentElement;
  if (!parent) {
    return;
  }

  const attributes = getAttributes(domContainer);
  parent.querySelectorAll("img.chem").forEach((image) => {
    image.remove();
  });
  const contents = parent.innerHTML;

  const root = createRoot(parent);
  root.render(<PreviewInfo item={{ ...attributes }} />);
  parent.insertAdjacentHTML("beforeend", contents);
}

function handleDocumentPlaced(event: Event & { detail?: string | number }) {
  getPreviewNodes(event.detail).forEach((domContainer) => {
    if (domContainer instanceof HTMLImageElement) {
      renderChemPreview(domContainer);
      return;
    }

    const attributes = getAttributes(domContainer);
    if (getStoichiometryReference(attributes["data-stoichiometry-table"])) {
      render(attributes, domContainer);
    }
  });

  // Tell React that a new document was placed into the dom
  document.dispatchEvent(new Event("images-replaced"));
}

function handleChemUpdated(event: Event & { detail?: string | number }) {
  const selector = event.detail ? `#div_${event.detail} img.chem` : "img.chem";

  document.querySelectorAll<HTMLImageElement>(selector).forEach((domContainer) => {
    const span = domContainer.closest("span");
    if (!span) {
      return;
    }

    render(getAttributes(domContainer), span);
  });
}

document.addEventListener("document-placed", handleDocumentPlaced);

document.addEventListener("chem-updated", handleChemUpdated);

