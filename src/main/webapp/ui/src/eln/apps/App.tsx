import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { mapValues } from "es-toolkit";
import { observable } from "mobx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/other";
import AppBar from "../../components/AppBar";
import { DialogBoundary } from "../../components/DialogBoundary";
import GoogleLoginProvider from "../../components/GoogleLoginProvider";
import AnalyticsContext from "../../stores/contexts/Analytics";
import * as FetchingData from "../../util/fetchingData";
import { getByKey } from "../../util/optional";
import CardListing from "./CardListing";
import { type IntegrationStates, useIntegrationsEndpoint } from "./useIntegrationsEndpoint";

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
  allStates: FetchingData.Fetched<IntegrationStates>;
};

function AppsSection({ id, title, description, mode, allStates }: AppsSectionArgs): React.ReactNode {
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
      <Box sx={{ mt: 0.5 }}>
        {FetchingData.match(allStates, {
          success: (integrationStates) => <CardListing mode={mode} integrationStates={integrationStates} />,
          loading: () => <LoadingSkeleton />,
          error: (error) => {
            throw new Error(error);
          },
        })}
      </Box>
    </Box>
  );
}

function App(): React.ReactNode {
  const { t } = useTranslation("apps");
  const { allIntegrations } = useIntegrationsEndpoint();
  const { trackEvent, isAvailable: analyticsIsAvailable } = useContext(AnalyticsContext);
  const [, setLastDialogOpened] = useState<string | null>(null);

  const [allStates, setAllStates] = useState<FetchingData.Fetched<IntegrationStates>>(observable({ tag: "loading" }));

  useEffect(() => {
    void (async () => {
      try {
        setAllStates(
          observable({
            tag: "success",
            value: mapValues(await allIntegrations(), (v) => observable(v)),
          }) as FetchingData.Fetched<IntegrationStates>,
        );
      } catch (e) {
        if (e instanceof Error) setAllStates(observable({ tag: "error", error: e.message }));
      }
    })();
  }, []);

  return (
    <>
      <GoogleLoginProvider />
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <DialogBoundary>
          <AnalyticsContext.Provider
            value={{
              isAvailable: analyticsIsAvailable,
              trackEvent: (event, properties) => {
                if (event === "Apps page dialog opened") {
                  if (typeof properties !== "undefined") {
                    getByKey("integrationName", properties).do((integrationName: unknown) => {
                      if (typeof integrationName === "string") {
                        setLastDialogOpened(integrationName);
                      }
                    });
                  }
                } else {
                  trackEvent(event, properties);
                }
              },
            }}
          >
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
                <Stack spacing={6} sx={{ mt: 1 }}>
                  <AppsSection
                    id="enabled"
                    title={t("page.sections.enabled.title")}
                    description={t("page.sections.enabled.description")}
                    mode="ENABLED"
                    allStates={allStates}
                  />
                  <AppsSection
                    id="disabled"
                    title={t("page.sections.disabled.title")}
                    description={t("page.sections.disabled.description")}
                    mode="DISABLED"
                    allStates={allStates}
                  />
                  <AppsSection
                    id="unavailable"
                    title={t("page.sections.unavailable.title")}
                    description={t("page.sections.unavailable.description")}
                    mode="UNAVAILABLE"
                    allStates={allStates}
                  />
                  <AppsSection
                    id="third-party-rspace-integrations"
                    title={t("page.sections.external.title")}
                    description={t("page.sections.external.description")}
                    mode="EXTERNAL"
                    allStates={allStates}
                  />
                </Stack>
              </Box>
            </Box>
          </AnalyticsContext.Provider>
        </DialogBoundary>
      </ThemeProvider>
    </>
  );
}

export default observer(App);
