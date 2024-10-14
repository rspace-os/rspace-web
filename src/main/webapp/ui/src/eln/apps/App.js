//@flow strict

import { observer } from "mobx-react-lite";
import { observable } from "mobx";
import React, {
  type Node,
  type ComponentType,
  useState,
  useContext,
  useEffect,
} from "react";
import Typography from "@mui/material/Typography";
import {
  useIntegrationsEndpoint,
  type IntegrationStates,
} from "./useIntegrationsEndpoint";
import VisuallyHiddenHeading from "../../components/VisuallyHiddenHeading";
import Grid from "@mui/material/Grid";
import Tabs from "@mui/material/Tabs";
import Box from "@mui/material/Box";
import CardListing from "./CardListing";
import docLinks from "../../assets/DocLinks";
import LinkTab from "./LinkTab";
import * as FetchingData from "../../util/fetchingData";
import { doNotAwait, mapObject } from "../../util/Util";
import Alert from "@mui/material/Alert";
import Skeleton from "@mui/material/Skeleton";
import HelpFab from "../../components/Help/Fab";
import { DialogBoundary } from "../../components/DialogBoundary";
import Divider from "@mui/material/Divider";
import Link from "@mui/material/Link";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { getByKey } from "../../util/optional";
import { useDeploymentProperty } from "../useDeploymentProperty";

function LoadingSkeleton() {
  return (
    <Grid container spacing={2} alignItems="stretch">
      <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
        <Skeleton variant="rounded" width="100%" height={125} />
      </Grid>
      <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
        <Skeleton variant="rounded" width="100%" height={125} />
      </Grid>
      <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
        <Skeleton variant="rounded" width="100%" height={125} />
      </Grid>
      <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
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

function App(): Node {
  const inventoryAvailable = useDeploymentProperty("inventory.available");

  const { allIntegrations } = useIntegrationsEndpoint();
  const { trackEvent, isAvailable: analyticsIsAvailable } =
    useContext(AnalyticsContext);
  const [lastDialogOpened, setLastDialogOpened] = useState<string | null>(null);

  const [allStates, setAllStates] = useState<
    FetchingData.Fetched<IntegrationStates>
  >(observable({ tag: "loading" }));

  useEffect(
    doNotAwait(async () => {
      try {
        setAllStates(
          // $FlowExpectedError[incompatible-call]
          observable({
            tag: "success",
            // $FlowExpectedError[prop-missing]
            // $FlowExpectedError[incompatible-call]
            // $FlowExpectedError[incompatible-indexer]
            value: mapObject((k, v) => observable(v), await allIntegrations()),
          })
        );
      } catch (e) {
        setAllStates(observable({ tag: "error", error: e.message }));
      }
    }),
    []
  );

  return (
    <>
      <HelpFab />
      <DialogBoundary>
        <AnalyticsContext.Provider
          value={{
            isAvailable: analyticsIsAvailable,
            trackEvent: (event, properties) => {
              if (event === "Apps page dialog opened") {
                if (typeof properties !== "undefined") {
                  getByKey("integrationName", properties).do(
                    (integrationName: mixed) => {
                      if (typeof integrationName === "string") {
                        setLastDialogOpened(integrationName);
                      }
                    }
                  );
                }
              } else {
                trackEvent(event, properties);
              }
            },
          }}
        >
          <Grid container direction="row" spacing={1}>
            <Grid item xs={1} md={2}></Grid>
            <Grid item xs={10} md={8}>
              <Grid
                container
                direction="row"
                justifyContent="space-between"
                component="header"
              >
                <Grid item>
                  <a href="/workspace">
                    {/* These dimensions are hard coded from the existing UI */}
                    <img
                      src="/public/banner"
                      alt="RSpace"
                      width={180}
                      height={55}
                    />
                  </a>
                </Grid>
                <Grid item alignSelf="flex-end">
                  <nav>
                    <Tabs value={3} sx={{ minHeight: 5 }}>
                      <LinkTab
                        label="Workspace"
                        href="/workspace"
                        tabIndex={0}
                      />
                      <LinkTab label="Gallery" href="/gallery" tabIndex={0} />
                      <LinkTab
                        label="Messaging"
                        href="/dashboard"
                        tabIndex={0}
                      />
                      <LinkTab label="Apps" href="#" tabIndex={0} />
                      <LinkTab
                        label="Inventory"
                        href="/inventory"
                        tabIndex={0}
                        disabled={FetchingData.match(inventoryAvailable, {
                          success: (v) => v !== "ALLOWED",
                          loading: () => true,
                          error: () => true,
                        })}
                      />
                      <LinkTab
                        label="My RSpace"
                        href="/userform"
                        tabIndex={0}
                      />
                    </Tabs>
                  </nav>
                </Grid>
              </Grid>
              <main>
                <Box sx={{ mt: 4 }}>
                  <VisuallyHiddenHeading variant="h1">
                    Apps
                  </VisuallyHiddenHeading>
                  <Grid container spacing={2} direction="column">
                    <Grid item sx={{ mb: 1 }}>
                      <Typography variant="body1">
                        RSpace provides integrations with various third-party
                        apps that enable extra features. Apps need to be enabled
                        to work, and some require authentication.{" "}
                        <Link
                          href={docLinks.appsIntroduction}
                          target="_blank"
                          rel="noreferrer"
                        >
                          See Apps Introduction to learn more.
                        </Link>
                      </Typography>
                    </Grid>
                    <Grid item sx={{ mb: 6 }}>
                      <section aria-labelledby="enabled">
                        <Grid container>
                          <Grid item width={"100%"} sx={{ mb: 2 }}>
                            <Divider>
                              <Typography
                                variant="h5"
                                component="h2"
                                sx={{ p: 1, mb: 1 }}
                                id="enabled"
                              >
                                Enabled
                              </Typography>
                            </Divider>
                            <Typography variant="body1" sx={{ mb: 2 }}>
                              The following Apps are enabled on this account.
                              Click on an App card to modify or disable the
                              integration.
                            </Typography>
                          </Grid>
                          <Grid item mt={0.5} xs={12}>
                            {FetchingData.match(allStates, {
                              success: (integrationStates) => (
                                <CardListing
                                  mode="ENABLED"
                                  integrationStates={integrationStates}
                                />
                              ),
                              loading: () => <LoadingSkeleton />,
                              error: () => <ErrorMessage />,
                            })}
                          </Grid>
                        </Grid>
                      </section>
                    </Grid>
                    <Grid item sx={{ mb: 6 }}>
                      <section aria-labelledby="disabled">
                        <Grid container>
                          <Grid item width={"100%"} sx={{ mb: 2 }}>
                            <Divider>
                              <Typography
                                variant="h5"
                                component="h2"
                                sx={{ p: 1, mb: 1 }}
                                id="disabled"
                              >
                                Disabled
                              </Typography>
                            </Divider>
                            <Typography variant="body1" sx={{ mb: 2 }}>
                              The following Apps are not currently enabled on
                              this account. Click on an App card for setup
                              instructions on how to enable the integration.
                            </Typography>
                          </Grid>
                          <Grid item mt={0.5} xs={12}>
                            {FetchingData.match(allStates, {
                              success: (integrationStates) => (
                                <CardListing
                                  mode="DISABLED"
                                  integrationStates={integrationStates}
                                />
                              ),
                              loading: () => <LoadingSkeleton />,
                              error: () => <ErrorMessage />,
                            })}
                          </Grid>
                        </Grid>
                      </section>
                    </Grid>
                    <Grid item sx={{ mb: 6 }}>
                      <section aria-labelledby="unavailable">
                        <Grid container>
                          <Grid item width={"100%"} sx={{ mb: 2 }}>
                            <Divider>
                              <Typography
                                variant="h5"
                                component="h2"
                                sx={{ p: 1, mb: 1 }}
                                id="unavailable"
                              >
                                Unavailable
                              </Typography>
                            </Divider>
                            <Typography variant="body1" sx={{ mb: 2 }}>
                              The following Apps need to be enabled by your
                              System Administrator before they can be used;
                              please get in touch with them directly to set this
                              up.
                            </Typography>
                          </Grid>
                          <Grid item mt={0.5} xs={12}>
                            {FetchingData.match(allStates, {
                              success: (integrationStates) => (
                                <CardListing
                                  mode="UNAVAILABLE"
                                  integrationStates={integrationStates}
                                />
                              ),
                              loading: () => <LoadingSkeleton />,
                              error: () => <ErrorMessage />,
                            })}
                          </Grid>
                        </Grid>
                      </section>
                    </Grid>
                    <Grid item sx={{ mb: 6 }}>
                      <section aria-labelledby="third-party rspace integrations">
                        <Grid container>
                          <Grid item width={"100%"} sx={{ mb: 2 }}>
                            <Divider>
                              <Typography
                                variant="h5"
                                component="h2"
                                sx={{ p: 1, mb: 1 }}
                                id="third-party-rspace-integrations"
                              >
                                Third-party RSpace Integrations
                              </Typography>
                            </Divider>
                            <Typography variant="body1" sx={{ mb: 2 }}>
                              These RSpace applications have been built by
                              partners or other external software developers.
                              Note, ResearchSpace does not provide direct
                              support for these integrations.
                            </Typography>
                          </Grid>
                          <Grid item mt={0.5} xs={12}>
                            {FetchingData.match(allStates, {
                              success: (integrationStates) => (
                                <CardListing
                                  mode="EXTERNAL"
                                  integrationStates={integrationStates}
                                />
                              ),
                              loading: () => <LoadingSkeleton />,
                              error: () => <ErrorMessage />,
                            })}
                          </Grid>
                        </Grid>
                      </section>
                    </Grid>
                  </Grid>
                </Box>
              </main>
            </Grid>
            <Grid item xs={1} md={2}></Grid>
          </Grid>
        </AnalyticsContext.Provider>
      </DialogBoundary>
    </>
  );
}

export default (observer(App): ComponentType<{||}>);
