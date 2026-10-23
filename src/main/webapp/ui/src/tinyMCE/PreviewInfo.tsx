import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import CssBaseline from "@mui/material/CssBaseline";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type React from "react";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { ACCENT_COLOR } from "@/assets/branding/chemistry";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import ErrorBoundary from "@/components/ErrorBoundary";
import createAccentedTheme from "../accentedTheme";
import { useIntegrationIsAllowedAndEnabled } from "../hooks/api/integrationHelpers";
import * as FetchingData from "../util/fetchingData";
import ChemCard from "./ChemCard";
import StoichiometryTable from "./stoichiometry/StoichiometryTable";

export type PreviewInfoItem = Record<string, string | undefined>;

export const STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE = "data-stoichiometry-table-only";
const PREVIEW_INFO_BORDER_COLOR = `hsl(${ACCENT_COLOR.background.hue} ${ACCENT_COLOR.background.saturation}% ${ACCENT_COLOR.background.lightness}%)`;
const queryClient = new QueryClient();

export function getStoichiometryReference(raw: string | undefined) {
  if (typeof raw !== "string") {
    return null;
  }

  try {
    const { id, revision } = JSON.parse(raw) as {
      id?: number | string;
      revision?: number | string;
    };
    const parsedId = typeof id === "number" ? id : Number(id);
    const parsedRevision = typeof revision === "number" ? revision : Number(revision);

    if (Number.isNaN(parsedId)) {
      return null;
    }

    return {
      id: parsedId,
      revision: Number.isNaN(parsedRevision) ? undefined : parsedRevision,
    };
  } catch {
    return null;
  }
}

export function StoichiometryPreviewSection({
  stoichiometryId,
  stoichiometryRevision,
}: {
  stoichiometryId: number;
  stoichiometryRevision?: number;
}) {
  const { t } = useTranslation("apps");
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
                <Box sx={{ p: 2 }}>
                  <Alert severity="info">{t("previewInfo.chemistryStatus.loading")}</Alert>
                </Box>
              ),
              error: (error) => (
                <Box sx={{ p: 2 }}>
                  <Alert severity="error">{t("previewInfo.chemistryStatus.error", { error: String(error) })}</Alert>
                </Box>
              ),
              success: (isEnabled) =>
                isEnabled ? (
                  <StoichiometryTable stoichiometryId={stoichiometryId} stoichiometryRevision={stoichiometryRevision} />
                ) : (
                  <Box sx={{ p: 2 }}>
                    <Alert severity="warning">{t("previewInfo.chemistryStatus.disabled")}</Alert>
                  </Box>
                ),
            })}
          </Box>
        </Alerts>
      </ErrorBoundary>
    </Analytics>
  );
}

function PreviewInfoFrame({ children, isTableOnly = false }: { children: React.ReactNode; isTableOnly?: boolean }) {
  const theme = createAccentedTheme(ACCENT_COLOR);

  return (
    <QueryClientProvider client={queryClient}>
      <StyledEngineProvider injectFirst enableCssLayer>
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

/** I18nRoot fallback: reuses PreviewInfoFrame's chrome sized to the source img's height, so document text doesn't shift. */
export function PreviewInfoFallback({ isTableOnly, height }: { isTableOnly?: boolean; height: number }) {
  return (
    <PreviewInfoFrame isTableOnly={isTableOnly}>
      <Box sx={{ p: 1 }}>
        <Skeleton variant="rectangular" height={height} />
      </Box>
    </PreviewInfoFrame>
  );
}

export default function PreviewInfo({ item }: { item: PreviewInfoItem }) {
  const stoichiometryReference = getStoichiometryReference(item["data-stoichiometry-table"]);
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
      <Box
        sx={{
          display: "flex",
          minHeight: "200px",
          maxHeight: "334px",
        }}
      >
        <Box sx={{ alignSelf: "center", mx: "auto" }}>
          {/** biome-ignore lint/a11y/useAltText: initial biome migration */}
          <img
            id={item.id}
            className={item.class}
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
      </Box>
      {stoichiometryReference && (
        <StoichiometryPreviewSection
          stoichiometryId={stoichiometryReference.id}
          stoichiometryRevision={stoichiometryReference.revision}
        />
      )}
    </PreviewInfoFrame>
  );
}
