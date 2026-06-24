import { faMinus } from "@fortawesome/free-solid-svg-icons/faMinus";
import { faPlus } from "@fortawesome/free-solid-svg-icons/faPlus";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { Button } from "@mui/material";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import type React from "react";
import { useEffect, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import GenericsearchBar from "../components/GenericsearchBar";
import materialTheme from "../theme";

type Location = { geonames_details: { name: string; country_name: string } };
type Name = { types: Array<string>; value: string };
type Link = { type: string; value: string };
type RORDataV2 = {
  status: string;
  id: string;
  locations: Array<Location>;
  country: { country_name: string };
  links: Array<Link>;
  names: Array<Name>;
  exceptionMessage?: string;
};

type RoRApiResponse = RORDataV2;

type RSpaceApiResponse = { data: { exceptionMessage?: string } };

const RSPACE_ROR_FORWARD_SLASH_DELIM = "__rspacror_forsl__";

function RorDetails(props: React.HTMLAttributes<HTMLDivElement>): React.ReactNode {
  return <Box {...props} sx={{ fontSize: "18px", margin: "0.5em 0.5em 0.5em 0" }} />;
}

function RorHelpText(props: React.HTMLAttributes<HTMLDivElement>): React.ReactNode {
  return (
    <Box
      {...props}
      sx={{
        width: "80%",
        fontSize: "14px",
        margin: "0.5em 0.5em 0.5em 0",
      }}
    />
  );
}

function RorErrorHelpText(props: React.HTMLAttributes<HTMLSpanElement>): React.ReactNode {
  return <Box component="span" {...props} sx={{ fontSize: "14px", backgroundColor: "#d9d9d9" }} />;
}

function RoRIntegration(): React.ReactNode {
  const { t } = useTranslation("system");
  const [ror, setRor] = useState<string>("");
  const [candidateRor, setCandidateRor] = useState<string>("");
  const [rorDetails, setRorDetails] = useState<RoRApiResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const handleNetworkError = (e: Error) => {
    setErrorMessage(e.message ? e.message : t("errors.networkProblem"));
  };

  useEffect(() => {
    const fetchExistingRor = async () => {
      try {
        const response: { data: string } = await axios.get("/system/ror/existingGlobalRoRID");
        setRor(response.data);
      } catch (e) {
        if (e instanceof Error) handleNetworkError(e);
      }
    };

    void fetchExistingRor();
  }, []);

  const parseRorError = (rorErrorMessage: string): string => {
    if (rorErrorMessage.includes("errors")) {
      //error from ROR API
      return rorErrorMessage.substring(rorErrorMessage.indexOf("errors") + 10, rorErrorMessage.length - 6);
    }
    return rorErrorMessage; //RSpace error message
  };

  const updateRor = async () => {
    const updatedRoR = candidateRor.replaceAll("/", RSPACE_ROR_FORWARD_SLASH_DELIM);
    try {
      const response: RSpaceApiResponse = await axios.post(`/system/ror/rorForID/${updatedRoR}`);
      if (response.data.exceptionMessage) {
        const message: string = response.data.exceptionMessage;
        setErrorMessage(parseRorError(message));
      } else {
        setErrorMessage("");
        setRor(candidateRor);
      }
    } catch (e) {
      if (e instanceof Error) handleNetworkError(e);
    }
  };
  const deleteRor = async () => {
    try {
      const response: RSpaceApiResponse = await axios.delete("/system/ror/rorForID/");
      if (response.data.exceptionMessage) {
        setErrorMessage(parseRorError(response.data.exceptionMessage));
      } else {
        setErrorMessage("");
        setRor("");
        setCandidateRor("");
        setRorDetails(null);
      }
    } catch (e) {
      if (e instanceof Error) handleNetworkError(e);
    }
  };

  const fetchDetails = async (rorID: string) => {
    if (rorID) {
      const searchTerm = rorID.replaceAll("/", RSPACE_ROR_FORWARD_SLASH_DELIM);
      try {
        const response: { data: RoRApiResponse } = await axios.get(`/system/ror/rorForID/${searchTerm}`);
        if (response.data.exceptionMessage) {
          setErrorMessage(parseRorError(response.data.exceptionMessage));
          setRorDetails(null);
        } else {
          setErrorMessage("");
          setRorDetails(response.data);
        }
      } catch (e) {
        if (e instanceof Error) handleNetworkError(e);
      }
    } else {
      setRorDetails(null);
    }
  };
  const handleSearch = (searchTerm: string) => {
    if (searchTerm) {
      setCandidateRor(searchTerm.trim());
    } else {
      //clear search term and reset candidateRor
      setErrorMessage("");
      setCandidateRor(ror);
    }
  };

  useEffect(() => {
    void fetchDetails(ror);
  }, [ror]);

  useEffect(() => {
    void fetchDetails(candidateRor);
  }, [candidateRor]);

  const getDisplayName = (): string | null => {
    if (rorDetails?.names) {
      return rorDetails.names.filter((name) => name.types.includes("ror_display")).map((name) => name.value)[0];
    }
    return null;
  };
  const getSeverity = (errorMessage: string): "error" | "warning" => {
    if (errorMessage.includes("valid")) {
      return "error";
    }
    return "warning";
  };

  const rorMatchesCandidateRor = (): boolean => {
    if (ror && candidateRor) {
      return ror.includes(candidateRor) || candidateRor.includes(ror);
    } else {
      return false;
    }
  };

  const showLinkAction = Boolean(candidateRor) && !rorMatchesCandidateRor() && !errorMessage;
  const showUnlinkAction = (rorMatchesCandidateRor() || (Boolean(ror) && !candidateRor)) && !errorMessage;

  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <div>
          <h1>{t("ror.heading")}</h1>
          <RorHelpText>
            {t("ror.introPrefix")}{" "}
            <a target="_blank" rel="noreferrer" href="https://ror.org">
              {t("ror.rorIdLink")}
            </a>{" "}
            {t("ror.introSuffix")}
          </RorHelpText>
        </div>
        <RorHelpText>
          <h2>{t("ror.institutionalIdHeading")}</h2>
        </RorHelpText>
        {!ror && (
          <GenericsearchBar
            handleSearch={handleSearch}
            placeholder={"https://ror.org/038xqyz77"}
            searchToolTip={t("ror.searchTooltip")}
          />
        )}
        {!rorDetails && (
          <RorHelpText>
            {t("ror.searchHelpPrefix")}{" "}
            <a target="_blank" rel="noreferrer" href="https://ror.org/search">
              {t("ror.registryLink")}
            </a>{" "}
            {t("ror.searchHelpMiddle")}{" "}
            <a
              target="_blank"
              rel="noreferrer"
              href="https://docs.google.com/forms/d/e/1FAIpQLSdJYaMTCwS7muuTa-B_CnAtCSkKzt19lkirAKG4u7umH9Nosg/viewform"
            >
              {t("ror.curationRequestLink")}
            </a>
          </RorHelpText>
        )}
        {showLinkAction && (
          <RorHelpText>
            {t("ror.linkHelpPrefix")} <strong>{t("ror.linkHelpAction")}</strong> {t("ror.linkHelpSuffix")}
          </RorHelpText>
        )}
        {showUnlinkAction && (
          <RorHelpText>
            {t("ror.unlinkHelpPrefix")} <strong>{t("ror.unlinkHelpAction")}</strong> {t("ror.unlinkHelpSuffix")}
          </RorHelpText>
        )}
        {rorDetails && (
          <>
            <RorDetails>
              {" "}
              <h2>
                <a target="_blank" rel="noreferrer" href={rorDetails.id}>
                  {rorDetails.id}
                </a>
              </h2>
            </RorDetails>
            <RorDetails>
              {" "}
              <h2>{getDisplayName()}</h2>
            </RorDetails>
            {rorDetails.locations.map((location, index) => {
              const cityCountry = `${location.geonames_details.name}, ${location.geonames_details.country_name}`;
              return (
                <RorDetails
                  key={`${location.geonames_details.name}-${location.geonames_details.country_name}-${index}`}
                >
                  <h5>{cityCountry}</h5>
                </RorDetails>
              );
            })}
            {rorDetails.links.map((link, index) => (
              <RorDetails key={`${link.value}-${index}`}>
                <h5>
                  <a target="_blank" rel="noreferrer" href={link.value}>
                    {link.value}
                  </a>
                </h5>
              </RorDetails>
            ))}
            <RorDetails>
              <h5>
                {t("ror.statusLabel")} {rorDetails.status}
              </h5>
            </RorDetails>
          </>
        )}
        {errorMessage && <Alert severity={getSeverity(errorMessage)}>{errorMessage}</Alert>}
        {errorMessage && getSeverity(errorMessage) === "error" && (
          <RorHelpText>
            {t("ror.invalidFormatHelp")}
            <RorErrorHelpText>https://ror.org/02mhbdp94</RorErrorHelpText>,{" "}
            <RorErrorHelpText>ror.org/02mhbdp94</RorErrorHelpText>, <RorErrorHelpText>02mhbdp94</RorErrorHelpText>
          </RorHelpText>
        )}
        {showLinkAction && (
          <Button
            color="primary"
            data-test-id="ror-link"
            variant="contained"
            sx={{ marginTop: "10px" }}
            onClick={() => void updateRor()}
            startIcon={<FontAwesomeIcon icon={faPlus} />}
          >
            {t("ror.linkButton")}
          </Button>
        )}
        {showUnlinkAction && (
          <Button
            color="primary"
            data-test-id="ror-link"
            variant="contained"
            sx={{ marginTop: "10px" }}
            onClick={() => void deleteRor()}
            startIcon={<FontAwesomeIcon icon={faMinus} />}
          >
            {t("ror.unlinkButton")}
          </Button>
        )}
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

type HTMLElementWithRorRoot = HTMLElement & { rorRoot?: Root };

function mountRoRIntegration(event?: Event) {
  event?.preventDefault();

  const mainArea: HTMLElementWithRorRoot | null = document.getElementById("mainArea");

  if (!mainArea) {
    return;
  }

  mainArea.rorRoot?.unmount();

  let domContainer = document.getElementById("rorIntegration");

  if (!domContainer) {
    domContainer = document.createElement("div");
    domContainer.id = "rorIntegration";
  }

  domContainer.style.width = "70%";

  mainArea.replaceChildren(domContainer);

  const root = createRoot(domContainer);
  mainArea.rorRoot = root;
  root.render(
    <I18nRoot namespaces={["system"]}>
      <RoRIntegration />
    </I18nRoot>,
  );
}

window.addEventListener("load", () => {
  document.getElementById("rorRegistryLink")?.addEventListener("click", (event) => void mountRoRIntegration(event));
});

export default RoRIntegration;
