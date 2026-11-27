import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useContext, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/other";
import AppBar from "../../components/AppBar";
import { DialogBoundary } from "../../components/DialogBoundary";
import GoogleLoginProvider from "../../components/GoogleLoginProvider";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { getByKey } from "../../util/optional";
import CardListing from "./CardListing";
import CardPlacementContext, { type FrozenModes } from "./CardPlacementContext";
import { type IntegrationModes, useIntegrationModesQuery } from "./queries";

function LoadingSkeleton() {
  return (
    <Grid container spacing={2} sx={{ alignItems: "stretch" }}>
      <Grid
        sx={{ display: "flex" }}
        size={{
          sm: 6,
          xs: 12,
        }}
      >
        <Skeleton variant="rounded" width="100%" height={125} />
      </Grid>
      <Grid
        sx={{ display: "flex" }}
        size={{
          sm: 6,
          xs: 12,
        }}
      >
        <Skeleton variant="rounded" width="100%" height={125} />
      </Grid>
      <Grid
        sx={{ display: "flex" }}
        size={{
          sm: 6,
          xs: 12,
        }}
      >
        <Skeleton variant="rounded" width="100%" height={125} />
      </Grid>
      <Grid
        sx={{ display: "flex" }}
        size={{
          sm: 6,
          xs: 12,
        }}
      >
        <Skeleton variant="rounded" width="100%" height={125} />
      </Grid>
    </Grid>
  );
}

type AppsSectionArgs = {
  id: string;
  title: string;
  description: React.ReactNode;
  mode: React.ComponentProps<typeof CardListing>["mode"];
  modes: IntegrationModes | undefined;
  loadingError: Error | null;
};

function AppsSection({ id, title, description, mode, modes, loadingError }: AppsSectionArgs): React.ReactNode {
  // the error boundary reports a failure to load; a failed refetch keeps the loaded cards
  if (!modes && loadingError) throw loadingError;
  return (
    <Box component="section" aria-labelledby={id}>
      <Box sx={{ mb: 2 }}>
        <Divider>
          <Typography
            variant="h5"
            component="h2"
            sx={{
              p: 1,
              mb: 1,
              ...(id === "third-party-rspace-integrations"
                ? {
                    whiteSpace: {
                      xs: "break-spaces",
                      md: "unset",
                    },
                  }
                : {}),
            }}
            id={id}
          >
            {title}
          </Typography>
        </Divider>
        <Typography variant="body1" sx={{ mb: 2 }}>
          {description}
        </Typography>
      </Box>
      <Box sx={{ mt: 0.5 }}>{modes ? <CardListing mode={mode} modes={modes} /> : <LoadingSkeleton />}</Box>
    </Box>
  );
}

function App(): React.ReactNode {
  const { t } = useTranslation("apps");
  const { data: modes, error: loadingError } = useIntegrationModesQuery();
  const analytics = useContext(AnalyticsContext);
  const [, setLastDialogOpened] = useState<string | null>(null);

  const [frozenModes, setFrozenModes] = useState<FrozenModes | null>(null);
  const cardPlacement = useMemo(
    () => ({
      frozenModes,
      dialogOpened: () => {
        if (modes) setFrozenModes(modes);
      },
      dialogClosed: () => setFrozenModes(null),
    }),
    [frozenModes, modes],
  );

  /*
   * The parent context's fields are read when used, rather than when this
   * component renders, so that they stay current once analytics has loaded.
   */
  const analyticsContext = useMemo(
    () => ({
      get isAvailable() {
        return analytics.isAvailable;
      },
      trackEvent: (event: string, properties?: Record<string, unknown>) => {
        if (event === "Apps page dialog opened") {
          if (typeof properties !== "undefined") {
            getByKey("integrationName", properties).do((integrationName: unknown) => {
              if (typeof integrationName === "string") {
                setLastDialogOpened(integrationName);
              }
            });
          }
        } else {
          analytics.trackEvent(event, properties);
        }
      },
    }),
    [analytics],
  );

  return (
    <>
      <GoogleLoginProvider />
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <DialogBoundary>
          <AnalyticsContext.Provider value={analyticsContext}>
            <AppBar
              variant="page"
              currentPage="Apps"
              ambientI18n
              accessibilityTips={{
                supportsHighContrastMode: true,
                supportsReducedMotion: true,
                supports2xZoom: true,
              }}
            />
            <Box
              component="main"
              sx={{
                width: {
                  xs: "83.333333%",
                  md: "66.666667%",
                },
                mx: "auto",
              }}
            >
              <Box sx={{ my: 4 }}>
                <Typography variant="h1">{t("page.title")}</Typography>
                <Typography variant="body1">
                  <TransRichText i18nKey="apps:page.introText" />
                </Typography>
                <CardPlacementContext.Provider value={cardPlacement}>
                  <Stack spacing={6} sx={{ mt: 1 }}>
                    <AppsSection
                      id="enabled"
                      title={t("page.sections.enabled.title")}
                      description={t("page.sections.enabled.description")}
                      mode="ENABLED"
                      modes={modes}
                      loadingError={loadingError}
                    />
                    <AppsSection
                      id="disabled"
                      title={t("page.sections.disabled.title")}
                      description={t("page.sections.disabled.description")}
                      mode="DISABLED"
                      modes={modes}
                      loadingError={loadingError}
                    />
                    <AppsSection
                      id="unavailable"
                      title={t("page.sections.unavailable.title")}
                      description={t("page.sections.unavailable.description")}
                      mode="UNAVAILABLE"
                      modes={modes}
                      loadingError={loadingError}
                    />
                    <AppsSection
                      id="third-party-rspace-integrations"
                      title={t("page.sections.external.title")}
                      description={t("page.sections.external.description")}
                      mode="EXTERNAL"
                      modes={modes}
                      loadingError={loadingError}
                    />
                  </Stack>
                </CardPlacementContext.Provider>
              </Box>
            </Box>
          </AnalyticsContext.Provider>
        </DialogBoundary>
      </ThemeProvider>
    </>
  );
}

export default App;
