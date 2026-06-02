import { observer } from "mobx-react-lite";
import { observable } from "mobx";
import React, { useState, useContext, useEffect } from "react";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import {
  useIntegrationsEndpoint,
  type IntegrationStates,
} from "./useIntegrationsEndpoint";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import CardListing from "./CardListing";
import docLinks from "../../assets/DocLinks";
import * as FetchingData from "../../util/fetchingData";
import { mapObject } from "../../util/Util";
import Alert from "@mui/material/Alert";
import Skeleton from "@mui/material/Skeleton";
import { DialogBoundary } from "../../components/DialogBoundary";
import Divider from "@mui/material/Divider";
import Link from "@mui/material/Link";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { getByKey } from "../../util/optional";
import AppBar from "../../components/AppBar";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import GoogleLoginProvider from "../../components/GoogleLoginProvider";
import { ACCENT_COLOR } from "../../assets/branding/rspace/other";

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

function ErrorMessage() {
  return (
    <Alert severity="error">
      Something went wrong! Please refresh the page. If this error persists,
      please contact{" "}
      <a
        href="mailto:support@researchspace.com"
        rel="noreferrer"
        target="_blank"
      >
        support@researchspace.com
      </a>{" "}
      with details of when the issue happens.
    </Alert>
  );
}

type AppsSectionArgs = {
  id: string;
  title: string;
  description: React.ReactNode;
  mode: React.ComponentProps<typeof CardListing>["mode"];
  allStates: FetchingData.Fetched<IntegrationStates>;
};

function AppsSection({
  id,
  title,
  description,
  mode,
  allStates,
}: AppsSectionArgs): React.ReactNode {
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
          success: (integrationStates) => (
            <CardListing mode={mode} integrationStates={integrationStates} />
          ),
          loading: () => <LoadingSkeleton />,
          error: () => <ErrorMessage />,
        })}
      </Box>
    </Box>
  );
}

function App(): React.ReactNode {
  const { allIntegrations } = useIntegrationsEndpoint();
  const { trackEvent, isAvailable: analyticsIsAvailable } =
    useContext(AnalyticsContext);
  const [, setLastDialogOpened] = useState<string | null>(null);

  const [allStates, setAllStates] = useState<
    FetchingData.Fetched<IntegrationStates>
  >(observable({ tag: "loading" }));

  useEffect(
    () => {
      void (async () => {
      try {
        setAllStates(
          observable({
            tag: "success",
            value: mapObject((k, v) => observable(v), await allIntegrations()),
          }) as FetchingData.Fetched<IntegrationStates>,
        );
      } catch (e) {
        if (e instanceof Error)
          setAllStates(observable({ tag: "error", error: e.message }));
      }
      })();
    },
    [],
  );

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
                    getByKey("integrationName", properties).do(
                      (integrationName: unknown) => {
                        if (typeof integrationName === "string") {
                          setLastDialogOpened(integrationName);
                        }
                      },
                    );
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
                <Typography variant="h1">Apps</Typography>
                <Typography variant="body1">
                  RSpace provides integrations with various third-party apps
                  that enable extra features. Apps need to be enabled to work,
                  and some require authentication.{" "}
                  <Link
                    href={docLinks.appsIntroduction}
                    target="_blank"
                    rel="noreferrer"
                  >
                    See Apps Introduction to learn more.
                  </Link>
                </Typography>
                <Stack spacing={6} sx={{ mt: 1 }}>
                  <AppsSection
                    id="enabled"
                    title="Enabled"
                    description={
                      <>
                        The following Apps are enabled on this account. Click on
                        an App card to modify or disable the integration.
                      </>
                    }
                    mode="ENABLED"
                    allStates={allStates}
                  />
                  <AppsSection
                    id="disabled"
                    title="Disabled"
                    description={
                      <>
                        The following Apps are not currently enabled on this
                        account. Click on an App card for setup instructions on
                        how to enable the integration.
                      </>
                    }
                    mode="DISABLED"
                    allStates={allStates}
                  />
                  <AppsSection
                    id="unavailable"
                    title="Unavailable"
                    description={
                      <>
                        The following Apps need to be enabled by your System
                        Administrator before they can be used; please get in
                        touch with them directly to set this up.
                      </>
                    }
                    mode="UNAVAILABLE"
                    allStates={allStates}
                  />
                  <AppsSection
                    id="third-party-rspace-integrations"
                    title="Third-party RSpace Integrations"
                    description={
                      <>
                        These RSpace applications have been built by partners or
                        other external software developers. Note, ResearchSpace
                        does not provide direct support for these integrations.
                      </>
                    }
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
