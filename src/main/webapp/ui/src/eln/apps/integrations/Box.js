//@flow strict

import Grid from "@mui/material/Grid";
import React, { type AbstractComponent, type Node } from "react";
import IntegrationCard from "../IntegrationCard";
import { type IntegrationStates } from "../useIntegrationsEndpoint";
import BoxIcon from "../../../assets/branding/box/logo.svg";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import RadioGroup from "@mui/material/RadioGroup";
import Radio from "@mui/material/Radio";
import Button from "@mui/material/Button";
import FormControlLabel from "@mui/material/FormControlLabel";
import Typography from "@mui/material/Typography";
import { Optional } from "../../../util/optional";
import { LOGO_COLOR } from "../../../assets/branding/box";

type BoxArgs = {|
  integrationState: IntegrationStates["BOX"],
  update: (IntegrationStates["BOX"]) => void,
|};

/*
 * Note that authentication with box is performaned when the user goes to
 * use the integration by inserting a file into their RSpace document.
 * The /integrations API does return a "box.linking.enabled" boolean,
 * which a setting configured by the sysadmin, but it is not parsed out by
 * useIntegrationsEndpoint because it is not used by any of this code. Even if
 * the user has enabled the integration, if the sysadmin has set this flag to
 * false then the import file button will not be available in the RSpace
 * document editor.
 */
function Box({ integrationState, update }: BoxArgs): Node {
  const [linkType, setLinkType] = React.useState(
    integrationState.credentials.BOX_LINK_TYPE.orElse("LIVE")
  );

  return (
    <Grid item sm={6} xs={12} sx={{ display: "flex" }}>
      <IntegrationCard
        name="Box"
        explanatoryText="Collaborate with anyone from anywhere with a content management and workflow cloud."
        image={BoxIcon}
        color={LOGO_COLOR}
        update={(newMode) =>
          update({
            mode: newMode,
            credentials: integrationState.credentials,
          })
        }
        integrationState={integrationState}
        usageText="You can include files from Box in your RSpace documents. Files are embedded as links to the Box location of that file."
        setupSection={
          <>
            <ol>
              <li>Enable the integration.</li>
              <li>Check that the link type option below is correct.</li>
              <li>
                When editing a document, click on the Box icon in the text
                editor toolbar.
              </li>
            </ol>
            <Card variant="outlined" sx={{ mt: 2 }}>
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void update({
                    mode: integrationState.mode,
                    credentials: {
                      BOX_LINK_TYPE: Optional.present(linkType),
                      "box.api.enabled":
                        integrationState.credentials["box.api.enabled"],
                    },
                  });
                }}
              >
                <CardContent>
                  {!integrationState.credentials["box.api.enabled"].orElse(
                    false
                  ) && (
                    <Typography variant="body2">
                      To enable the disabled options, please contact your
                      sysadmin.
                    </Typography>
                  )}
                  <RadioGroup
                    value={linkType}
                    sx={{ alignItems: "flex-start" }}
                    onChange={(_event, value) => {
                      setLinkType(value);
                    }}
                  >
                    <FormControlLabel
                      value="LIVE"
                      control={<Radio />}
                      label={
                        <>
                          <Typography variant="body1">Live</Typography>
                          <Typography
                            variant="body2"
                            sx={{ fontSize: "0.825em" }}
                          >
                            Only live links (that always point to the latest
                            version of the file) are inserted.
                          </Typography>
                        </>
                      }
                    />
                    <FormControlLabel
                      value="VERSIONED"
                      sx={{ alignItems: "flex-start" }}
                      disabled={
                        !integrationState.credentials["box.api.enabled"].orElse(
                          false
                        )
                      }
                      control={<Radio />}
                      label={
                        <>
                          <Typography variant="body1">Versioned</Typography>
                          <Typography
                            variant="body2"
                            sx={{ fontSize: "0.825em" }}
                          >
                            If the file is updated in Box after you made the
                            link, you&apos;ll still be able to download the
                            original version. This feature only works for
                            premium Box accounts.
                          </Typography>
                        </>
                      }
                    />
                    <FormControlLabel
                      value="ASK"
                      sx={{ alignItems: "flex-start" }}
                      disabled={
                        !integrationState.credentials["box.api.enabled"].orElse(
                          false
                        )
                      }
                      control={<Radio />}
                      label={
                        <>
                          <Typography variant="body1">
                            Decide between live and versioned links when
                            inserting a link.
                          </Typography>
                          <Typography
                            variant="body2"
                            sx={{ fontSize: "0.825em" }}
                          >
                            This allows some links to be to live files and some
                            to be to older versions.
                          </Typography>
                        </>
                      }
                    />
                  </RadioGroup>
                </CardContent>
                <CardActions>
                  <Button type="submit">Save</Button>
                </CardActions>
              </form>
            </Card>
          </>
        }
        helpLinkText="Cloud Storage integrations docs"
        website="box.com"
        docLink="cloudstorage"
      />
    </Grid>
  );
}

export default (React.memo(Box): AbstractComponent<BoxArgs>);
