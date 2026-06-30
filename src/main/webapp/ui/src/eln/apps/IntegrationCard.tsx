import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActionArea from "@mui/material/CardActionArea";
import CardHeader from "@mui/material/CardHeader";
import CardMedia from "@mui/material/CardMedia";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import { formControlLabelClasses } from "@mui/material/FormControlLabel";
import Grow from "@mui/material/Grow";
import Link from "@mui/material/Link";
import { radioClasses } from "@mui/material/Radio";
import Stack from "@mui/material/Stack";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { createTheme, type ThemeOptions, ThemeProvider, useTheme } from "@mui/material/styles";
import Typography, { typographyClasses } from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React, { forwardRef, useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import type { Hsl } from "../../accentedTheme";
import type { DocLinkName } from "../../assets/DocLinks";
import docLinks from "../../assets/DocLinks";
import { Dialog } from "../../components/DialogBoundary";
import AnalyticsContext from "../../stores/contexts/Analytics";
import type { IntegrationState } from "./useIntegrationsEndpoint";

function hsl(hue: number, saturation: number, lightness: number, opacity: number) {
  // lightness is reduced so that all text meets WCAG2.1 AAA guidelines
  // a reduction of 8 is arbitrary and should be increased to ensure compliance
  const adjustedLightness = Math.max(lightness - 8, 0);
  return window.matchMedia("(prefers-contrast: more)").matches
    ? `hsl(${hue} ${saturation}% ${adjustedLightness}% / 100%)`
    : `hsl(${hue} ${saturation}% ${lightness}% / ${opacity}%)`;
}
const accentTextColor = (color: Hsl, opacity: number = 100) => hsl(color.hue, color.saturation, 27, opacity);
const mainTextColor = (color: Hsl, opacity: number = 100) => hsl(color.hue, color.saturation, 20, opacity);
const borderColor = (color: Hsl, opacity: number = 25) => hsl(color.hue, color.saturation, 20, opacity);
type IntegrationCardArgs<Credentials> = {
  // The name of the integration, as rendered in the UI. Be sure to check how
  // the company chooses to brand the service.
  name: string;

  // Brief explanation of what the service is, for those who haven't
  // heard of it before. Derive this from the service's website. Keep it very
  // brief as it will be shown both in the card and in the header of the dialog
  explanatoryText: string;

  // The website of the integration's service. Format like as `example.com`, as
  // `https://` will be prefixed when used as a `href` property, and will be
  // displayed in the UI as passed.
  website?: string;

  // Logo, or similar image. This MUST be an SVG. Try to make any new images
  // conform with the existing ones; either a white silhouette where the logo
  // allows, or else with a border based on the logo's most common colour.
  image: string;

  // The most common colour in the logo. This will be the accent colour of both
  // the card, the dialog, and both their contents. If the most appropriate
  // colour is very light, then do make sure that all UI elements meet the AA
  // colour contrast accessibility requirement.
  color: Hsl;

  // The current state of the integration; whether it is enabled or disabled,
  // any configurations/credentials.
  integrationState: IntegrationState<Credentials>;

  // An explanation of what value connect the service to RSpace provides.
  // Describe the behaviour of RSpace when the integration is enabled.
  usageText: React.ReactNode;

  // The name of a link in ../../assets/DocLinks to our user-facing
  // documentation. We cannot infer this from `name` because some very similar
  // integrations share a single page of documentation.
  docLink: DocLinkName;

  // The text that should be shown when linking to our user-facing
  // documentation. This string should follow accessibility best-practices, in
  // that it should be understandable without any surrounding context.
  // Something like "Foo integration docs" is best, simply "Foo" is not.
  helpLinkText: string;

  // This is the content of the dialog beneath the "Setup" heading.
  // This include instructions, which should use regular <ol> and <li> tags to
  // provide list(s) of instructions.
  // This should also include any <forms>s for providing credentials.
  // If nothing else is necessary, at the very lease, instruct the user on
  // where they can use the integration within RSpace once enabled.
  setupSection: React.ReactNode;

  // Function to be called to update the mode of the integration i.e. whether
  // it is enabled or disable.
  update: (newState: IntegrationState<Credentials>["mode"]) => void;
};
const CustomGrow = forwardRef<typeof Grow, React.ComponentProps<typeof Grow>>((props, ref) => (
  <Grow
    {...props}
    ref={ref}
    timeout={window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 200}
    style={{
      transformOrigin: "center 70%",
    }}
  />
));
CustomGrow.displayName = "CustomGrow";
type NoopTransitionProps = {
  in?: boolean;
  children?: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
};
const NoopTransition = React.forwardRef<HTMLElement, NoopTransitionProps>(
  ({ in: inProp, children, className, style }, ref) => {
    if (!inProp) return null;
    if (React.isValidElement(children)) {
      return React.cloneElement(children as React.ReactElement<Record<string, unknown>>, {
        ref,
        className,
        style,
        tabIndex: -1,
      });
    }
    return children ?? null;
  },
);
NoopTransition.displayName = "NoopTransition";
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
}: IntegrationCardArgs<Credentials>): React.ReactNode {
  const { t } = useTranslation("apps");
  const [open, setOpen] = useState(false);
  const mode = integrationState.mode;
  const theme = useTheme();
  const { trackEvent } = useContext(AnalyticsContext);

  const cardMediaWrapperSx = {
    borderRadius: theme.spacing(0.75),
    margin: theme.spacing(0.5),
    backgroundColor: hsl(color.hue, color.saturation, color.lightness, 100),
    border: `4px solid ${hsl(color.hue, color.saturation, color.lightness, 100)}`,
    alignSelf: "flex-start",
  } as const;

  return (
    <ThemeProvider
      theme={createTheme(theme as unknown as ThemeOptions, {
        components: {
          MuiLink: {
            defaultProps: {
              target: "_blank",
              rel: "noreferrer",
            },
            styleOverrides: {
              root: {
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
                margin: 0,
                borderBottom: `1px solid ${borderColor(color, 20)}`,
                textTransform: "none",
              },
            },
          },
          MuiDialogContent: {
            styleOverrides: {
              root: {
                padding: `${theme.spacing(1.5)} ${theme.spacing(2)}`,
                paddingTop: theme.spacing(2),
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
                [`&.${formControlLabelClasses.disabled}`]: {
                  [`& .${typographyClasses.root}`]: {
                    color: "grey",
                  },
                },
              },
            },
          },
          MuiRadio: {
            styleOverrides: {
              root: {
                [`& .${svgIconClasses.root}`]: {
                  color: accentTextColor(color),
                },
                [`&.${radioClasses.disabled}`]: {
                  [`& .${svgIconClasses.root}`]: {
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
      <Card
        variant="outlined"
        aria-label={name}
        sx={{
          display: "flex",
          flexDirection: "column",
          width: "100%",
          borderRadius: theme.spacing(1),
          justifyContent: "space-between",
          boxShadow: mode === "UNAVAILABLE" ? "unset" : `${hsl(color.hue, color.saturation, 20, 20)} 0px 2px 8px 0px`,
          filter: mode === "UNAVAILABLE" ? "grayscale(0.6) opacity(0.8)" : "unset",
          transitionDuration: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "0s" : "0.3s",
          "&:hover": {
            boxShadow:
              mode === "UNAVAILABLE" ? "unset" : `${hsl(color.hue, color.saturation, 20, 20)} 0px 2px 12px 4px`,
          },
          "&:focus-within": {
            boxShadow: `${hsl(color.hue, color.saturation, 20, 20)} 0px 2px 12px 4px`,
            borderColor: theme.palette.primary.main,
          },
        }}
      >
        <CardActionArea
          disabled={mode === "UNAVAILABLE"}
          onClick={() => {
            setOpen(true);
            trackEvent("Apps page dialog opened", {
              integrationName: name,
            });
          }}
        >
          <CardHeader
            title={name}
            subheader={explanatoryText}
            avatar={
              <Box sx={cardMediaWrapperSx}>
                <CardMedia component="img" src={image} alt="" role="presentation" />
              </Box>
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
        sx={{
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
        }}
        slotProps={{
          paper: {
            tabIndex: -1,
          },
        }}
        slots={{
          transition: CustomGrow,
        }}
      >
        <DialogTitle>
          <Stack
            direction="row"
            sx={{
              flexWrap: "nowrap",
              alignItems: "flex-start",
              gap: 1,
            }}
          >
            <Box>
              <Box sx={cardMediaWrapperSx}>
                <CardMedia component="img" src={image} alt="" role="presentation" />
              </Box>
            </Box>
            <Box>
              {name}
              <Typography variant="body2" component="div">
                {explanatoryText}
              </Typography>
            </Box>
          </Stack>
        </DialogTitle>
        <DialogContent sx={{ p: 0 }}>
          <Stack sx={{ p: 2 }}>
            <section>
              <Typography variant="body2">{usageText}</Typography>
              {typeof website === "string" ? (
                <Typography variant="body2">
                  {t("integrationCard.moreInfo.websitePrefix")}{" "}
                  <Link href={website.startsWith("/") ? website : `https://${website}`}>{website}</Link>{" "}
                  {t("integrationCard.moreInfo.websiteMiddle")} <Link href={docLinks[docLink]}>{helpLinkText}</Link>{" "}
                  {t("integrationCard.moreInfo.suffix")}
                </Typography>
              ) : (
                <Typography variant="body2">
                  {t("integrationCard.moreInfo.docsOnlyPrefix")} <Link href={docLinks[docLink]}>{helpLinkText}</Link>{" "}
                  {t("integrationCard.moreInfo.suffix")}
                </Typography>
              )}
            </section>
            <Divider orientation="horizontal" sx={{ gap: 0 }} />
            <section>
              <Typography variant="subtitle1" component="h4">
                {t("integrationCard.setup")}
              </Typography>
              {setupSection}
            </section>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: `0 ${theme.spacing(2)} ${theme.spacing(2)}` }}>
          <Button
            onClick={() => {
              setOpen(false);
            }}
          >
            {t("actions.close")}
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
              {mode === "ENABLED" ? t("integrationCard.disable") : t("integrationCard.enable")}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}
export default observer(IntegrationCard);
