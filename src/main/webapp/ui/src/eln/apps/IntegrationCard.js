//@flow strict

import React, {
  type Node,
  useState,
  useContext,
  type ElementProps,
  forwardRef,
  type Ref,
} from "react";
import Typography from "@mui/material/Typography";
import { type IntegrationState } from "./useIntegrationsEndpoint";
import Card from "@mui/material/Card";
import CardMedia from "@mui/material/CardMedia";
import Button from "@mui/material/Button";
import { Dialog } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import { observer } from "mobx-react-lite";
import Grow from "@mui/material/Grow";
import { createTheme, ThemeProvider, useTheme } from "@mui/material/styles";
import DialogTitle from "@mui/material/DialogTitle";
import CardActionArea from "@mui/material/CardActionArea";
import { makeStyles } from "tss-react/mui";
import Link from "@mui/material/Link";
import docLinks from "../../assets/DocLinks";
import Divider from "@mui/material/Divider";
import CardHeader from "@mui/material/CardHeader";
import Grid from "@mui/material/Grid";
import AnalyticsContext from "../../stores/contexts/Analytics";

function hsl(
  hue: number,
  saturation: number,
  lightness: number,
  opacity: number
) {
  // lightness is reduced so that all text meets WCAG2.1 AAA guidelines
  // a reduction of 8 is arbitrary and should be increased to ensure compliance
  const adjustedLightness = Math.max(lightness - 8, 0);

  return window.matchMedia("(prefers-contrast: more)").matches
    ? `hsl(${hue} ${saturation}% ${adjustedLightness}% / 100%)`
    : `hsl(${hue} ${saturation}% ${lightness}% / ${opacity}%)`;
}

const accentTextColor = (
  color: {| hue: number, saturation: number, lightness: number |},
  opacity: number = 100
) => hsl(color.hue, color.saturation, 27, opacity);

const mainTextColor = (
  color: {| hue: number, saturation: number, lightness: number |},
  opacity: number = 100
) => hsl(color.hue, color.saturation, 20, opacity);

const borderColor = (
  color: {| hue: number, saturation: number, lightness: number |},
  opacity: number = 25
) => hsl(color.hue, color.saturation, 20, opacity);

const shadowColor = (
  color: {| hue: number, saturation: number, lightness: number |},
  elevation: "high" | "low"
) =>
  `${hsl(color.hue, color.saturation, 20, 20)} 0px 2px ${
    elevation === "high" ? 12 : 8
  }px ${elevation === "high" ? 4 : 0}px`;

type IntegrationCardArgs<Credentials> = {|
  // The name of the integration, as rendered in the UI. Be sure to check how
  // the company chooses to brand the service.
  name: string,

  // Brief explanation of what the service is, for those who haven't
  // heard of it before. Derive this from the service's website. Keep it very
  // brief as it will be shown both in the card and in the header of the dialog
  explanatoryText: string,

  // The website of the integration's service. Format like as `example.com`, as
  // `https://` will be prefixed when used as a `href` property, and will be
  // displayed in the UI as passed.
  website: string,

  // Logo, or similar image. This MUST be an SVG. Try to make any new images
  // conform with the existing ones; either a white silhouette where the logo
  // allows, or else with a border based on the logo's most common colour.
  image: string,

  // The most common colour in the logo. This will be the accent colour of both
  // the card, the dialog, and both their contents. If the most appropriate
  // colour is very light, then do make sure that all UI elements meet the AA
  // colour contrast accessibility requirement.
  color: {| hue: number, saturation: number, lightness: number |},

  // The current state of the integration; whether it is enabled or disabled,
  // any configurations/credentials.
  integrationState: IntegrationState<Credentials>,

  // An explanation of what value connect the service to RSpace provides.
  // Describe the behaviour of RSpace when the integration is enabled.
  usageText: string,

  // The name of a link in ../../assets/DocLinks to our user-facing
  // documentation. We cannot infer this from `name` because some very similar
  // integrations share a single page of documentation.
  docLink: string,

  // The text that should be shown when linking to our user-facing
  // documentation. This string should follow accessibility best-practices, in
  // that it should be understandable without any surrounding context.
  // Something like "Foo integration docs" is best, simply "Foo" is not.
  helpLinkText: string,

  // This is the content of the dialog beneath the "Setup" heading.
  // This include instructions, which should use regular <ol> and <li> tags to
  // provide list(s) of instructions.
  // This should also include any <forms>s for providing credentials.
  // If nothing else is necessary, at the very lease, instruct the user on
  // where they can use the integration within RSpace once enabled.
  setupSection: Node,

  // Function to be called to update the mode of the integration i.e. whether
  // it is enabled or disable.
  update: (IntegrationState<Credentials>["mode"]) => void,
|};

const CustomGrow = forwardRef<ElementProps<typeof Grow>, {||}>(
  (props: ElementProps<typeof Grow>, ref: Ref<typeof Grow>) => (
    <Grow
      {...props}
      ref={ref}
      timeout={
        window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 200
      }
      style={{
        transformOrigin: "center 70%",
      }}
    />
  )
);
CustomGrow.displayName = "CustomGrow";

const useStyles = makeStyles()((theme, { color, mode }) => ({
  card: {
    display: "flex",
    flexDirection: "column",
    width: "100%",
    borderRadius: 8,
    justifyContent: "space-between",
    boxShadow:
      mode === "UNAVAILABLE"
        ? "unset"
        : `${hsl(color.hue, color.saturation, 20, 20)} 0px 2px 8px 0px`,
    filter: mode === "UNAVAILABLE" ? "grayscale(0.6) opacity(0.8)" : "unset",
    transitionDuration: window.matchMedia("(prefers-reduced-motion: reduce)")
      .matches
      ? "0s"
      : "0.3s",
    "&:hover": {
      boxShadow:
        mode === "UNAVAILABLE"
          ? "unset"
          : `${hsl(color.hue, color.saturation, 20, 20)} 0px 2px 12px 4px`,
    },
    "&:focus-within": {
      boxShadow: `${hsl(color.hue, color.saturation, 20, 20)} 0px 2px 12px 4px`,
      borderColor: theme.palette.primary.main,
    },
  },
  cardMediaWrapper: {
    borderRadius: theme.spacing(0.75),
    margin: theme.spacing(0.5),
    backgroundColor: hsl(color.hue, color.saturation, color.lightness, 100),
    border: `4px solid ${hsl(
      color.hue,
      color.saturation,
      color.lightness,
      100
    )}`,
    alignSelf: "flex-start",
  },
  dialog: {
    // these styles allow callers of this component to use regular HTML tags to
    // markup the `setupSection`
    "& ol": {
      marginTop: theme.spacing(0.5),
      paddingLeft: theme.spacing(3),
      marginBottom: 0,
    },
    "& ol > li": {
      position: "relative",
      paddingLeft: theme.spacing(1),
      color: mainTextColor(color),
      paddingBottom: theme.spacing(0.5),
      lineHeight: 1.6,
      fontSize: "0.875rem",
      "&::marker": {
        color: mainTextColor(color),
        fontWeight: 500,
        content: "counter(list-item) ' '",
        paddingRight: theme.spacing(0.25),
        paddingLeft: theme.spacing(0.25),
      },
      "&:before": {
        content: "''",
        width: theme.spacing(2.5),
        height: theme.spacing(2.5),
        display: "block",
        position: "absolute",
        left: "-18px",
        top: "1px",
        borderRadius: "50%",
        backgroundColor: window.matchMedia("(prefers-contrast: more)").matches
          ? "transparent"
          : borderColor(color),
      },
    },
  },
}));

function IntegrationCard<Credentials>({
  name,
  explanatoryText,
  image,
  color,
  update,
  integrationState,
  usageText,
  helpLinkText,
  docLink,
  website,
  setupSection,
}: IntegrationCardArgs<Credentials>): Node {
  const [open, setOpen] = useState(false);
  const mode = integrationState.mode;
  const theme = useTheme();
  const { classes } = useStyles({ color, mode });
  const { trackEvent } = useContext(AnalyticsContext);

  return (
    <ThemeProvider
      theme={createTheme({
        ...theme,
        components: {
          ...theme.components,
          MuiLink: {
            defaultProps: {
              target: "_blank",
              rel: "noreferrer",
            },
            styleOverrides: {
              root: {
                ...theme?.components?.MuiLink?.styleOverrides?.root,
                color: accentTextColor(color),
                textDecorationColor: accentTextColor(color, 40),
                outlineColor: mainTextColor(color, 25),
              },
            },
          },
          MuiCard: {
            styleOverrides: {
              root: {
                border: `2px solid ${borderColor(color)}`,
                borderRadius: theme.spacing(1),
              },
            },
          },
          MuiCardHeader: {
            styleOverrides: {
              avatar: {
                alignSelf: "flex-start",
                marginRight: theme.spacing(1.5),
              },
              title: {
                fontWeight: 500,
                fontSize: "1.3rem",
                color: accentTextColor(color),
                paddingBottom: 0,
              },
              subheader: {
                color: mainTextColor(color),
              },
            },
          },
          MuiCardMedia: {
            defaultProps: {
              role: "presentation",
              component: "img",
            },
            styleOverrides: {
              root: {
                margin: 0,
                width: "3.4rem",
                height: "3.4rem",
                borderRadius: theme.spacing(0.5),
              },
            },
          },
          MuiCardActionArea: {
            styleOverrides: {
              root: {
                // these are so that the focus ring is over the whole card, not
                // just this inner button
                color: "white",
                outline: "0 !important",
              },
            },
          },
          MuiDialog: {
            styleOverrides: {
              paper: {
                border: `3px solid ${borderColor(color)}`,
                borderRadius: theme.spacing(1),
              },
            },
          },
          MuiDialogTitle: {
            defaultProps: {
              component: "h3",
            },
            styleOverrides: {
              root: {
                padding: theme.spacing(1),
                marginBottom: theme.spacing(2),
                borderBottom: `1px solid ${borderColor(color, 20)}`,
              },
            },
          },
          MuiDialogContent: {
            styleOverrides: {
              root: {
                padding: `${theme.spacing(1.5)} ${theme.spacing(2)}`,
              },
            },
          },
          MuiDialogActions: {
            styleOverrides: {
              root: {
                // buttons are positioned in the bottom left so that on mobile
                // devices, the help FAB doesn't obscure the buttons
                justifyContent: "flex-start",
              },
            },
          },
          MuiButton: {
            styleOverrides: {
              root: {
                border: `2px solid ${borderColor(color)}`,
                color: mainTextColor(color),
                outlineColor: `${accentTextColor(color)} !important`,
                borderRadius: 6,
                paddingTop: theme.spacing(0.5),
                paddingBottom: theme.spacing(0.5),
                paddingLeft: theme.spacing(2.5),
                paddingRight: theme.spacing(2.5),
                transition: "background-color ease 0.3s",
                "&:hover": {
                  border: `2px solid ${mainTextColor(color, 20)}`,
                  // hover background is not adjusted for prefers-contrast as
                  // it is purely a visual effect and not necessary for
                  // understanding the interface
                  backgroundColor: `hsl(${color.hue} ${color.saturation}% 20% / 5%)`,
                },
              },
            },
          },
          MuiTypography: {
            styleOverrides: {
              h6: {
                color: accentTextColor(color),
                lineHeight: 1.5,
              },
              subtitle1: {
                color: accentTextColor(color),
                fontWeight: 500,
              },
              body1: {
                color: mainTextColor(color),
              },
              body2: {
                color: mainTextColor(color),
                paddingBottom: theme.spacing(0.5),
              },
            },
          },
          MuiFormControlLabel: {
            styleOverrides: {
              root: {
                marginBottom: theme.spacing(1),
              },
              label: {
                "&.Mui-disabled": {
                  "& .MuiTypography-root": {
                    color: "grey",
                  },
                },
              },
            },
          },
          MuiRadio: {
            styleOverrides: {
              root: {
                "& .MuiSvgIcon-root": {
                  color: accentTextColor(color),
                },
                "&.Mui-disabled": {
                  "& .MuiSvgIcon-root": {
                    color: "grey",
                  },
                },
              },
            },
          },
          MuiDivider: {
            styleOverrides: {
              root: {
                borderColor: borderColor(color, 20),
                marginTop: theme.spacing(2),
                marginBottom: theme.spacing(2),
              },
            },
          },
          MuiOutlinedInput: {
            styleOverrides: {
              notchedOutline: {
                borderColor: `${accentTextColor(color)} !important`,
              },
            },
          },
          MuiInputLabel: {
            styleOverrides: {
              root: {
                color: `${accentTextColor(color)} !important`,
              },
            },
          },
        },
      })}
    >
      <Card variant="outlined" className={classes.card} aria-label={name}>
        <CardActionArea
          disabled={mode === "UNAVAILABLE"}
          onClick={() => {
            setOpen(true);
            trackEvent("Apps page dialog opened", { integrationName: name });
          }}
        >
          <CardHeader
            title={name}
            subheader={explanatoryText}
            avatar={
              <div className={classes.cardMediaWrapper}>
                <CardMedia image={image} />
              </div>
            }
          />
        </CardActionArea>
      </Card>
      <Dialog
        onClose={() => {
          setOpen(false);
        }}
        open={open}
        maxWidth="sm"
        fullWidth
        TransitionComponent={CustomGrow}
        className={classes.dialog}
      >
        <DialogTitle>
          <Grid container direction="row" flexWrap="nowrap" spacing={1}>
            <Grid item>
              <div className={classes.cardMediaWrapper}>
                <CardMedia image={image} />
              </div>
            </Grid>
            <Grid item>
              {name}
              <Typography variant="body2" component="div">
                {explanatoryText}
              </Typography>
            </Grid>
          </Grid>
        </DialogTitle>
        <DialogContent>
          <section>
            <Typography variant="body2">{usageText}</Typography>
            <Typography variant="body2">
              See <Link href={`https://${website}`}>{website}</Link>
              {" and our "}
              <Link href={docLinks[docLink]}>{helpLinkText}</Link> for more.
            </Typography>
          </section>
          <Divider orientation="horizontal" />
          <section>
            <Typography variant="subtitle1" component="h4">
              Setup
            </Typography>
            {setupSection}
          </section>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setOpen(false);
            }}
          >
            Close
          </Button>
          {integrationState.mode !== "EXTERNAL" && (
            <Button
              onClick={() => {
                if (mode === "ENABLED") {
                  update("DISABLED");
                } else {
                  update("ENABLED");
                }
              }}
            >
              {mode === "ENABLED" ? "DISABLE" : "ENABLE"}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}

export default (observer(IntegrationCard): typeof IntegrationCard);
