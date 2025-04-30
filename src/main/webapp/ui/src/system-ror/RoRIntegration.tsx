import React, { useEffect, useState } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import { createRoot } from "react-dom/client";
import GenericsearchBar from "../components/GenericsearchBar";
import axios from "@/common/axios";
import styled from "@emotion/styled";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { library } from "@fortawesome/fontawesome-svg-core";
import { faPlus, faMinus } from "@fortawesome/free-solid-svg-icons";
import Alert from "@mui/material/Alert";
import { Button } from "@mui/material";
library.add(faPlus, faMinus);

type AddressV1 = { city: string };
type RORDataV1 = {
  status: string;
  id: string;
  addresses: Array<AddressV1>;
  country: { country_name: string };
  links: Array<string>;
  name: string;
  exceptionMessage?: string;
};

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

type RoRApiResponse = RORDataV1 | RORDataV2;

type RSpaceApiResponse = { data: { exceptionMessage?: string } };

const RorDetails = styled.div`
  font-size: 18px;
  margin: 0.5em 0.5em 0.5em 0;
`;

const RorHelpText = styled.div`
  width: 80%;
  font-size: 14px;
  margin: 0.5em 0.5em 0.5em 0;
`;

const RorErrorHelpText = styled.span`
  font-size: 14px;
  background-color: #d9d9d9;
`;

// eslint-disable-next-line complexity
function RoRIntegration(): React.ReactNode {
  const [ror, setRor] = useState<string>("");
  const [candidateRor, setCandidateRor] = useState<string>("");
  const [rorDetails, setRorDetails] = useState<RoRApiResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const handleNetworkError = (e: Error) => {
    setErrorMessage(
      e.message ? e.message : "There is a problem, please try again later"
    );
  };

  useEffect(() => {
    const fetchExistingRor = async () => {
      try {
        const response: { data: string } = await axios.get(
          "/system/ror/existingGlobalRoRID"
        );
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
      return rorErrorMessage.substring(
        rorErrorMessage.indexOf("errors") + 10,
        rorErrorMessage.length - 6
      );
    }
    return rorErrorMessage; //RSpace error message
  };

  const RSPACE_ROR_FORWARD_SLASH_DELIM = "__rspacror_forsl__";

  const updateRor = async () => {
    const updatedRoR = candidateRor
      ? candidateRor.replaceAll("/", RSPACE_ROR_FORWARD_SLASH_DELIM)
      : "";
    try {
      const response: RSpaceApiResponse = await axios.post(
        "/system/ror/rorForID/" + updatedRoR
      );
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
      const response: RSpaceApiResponse = await axios.delete(
        "/system/ror/rorForID/"
      );
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
      const searchTerm = rorID
        ? rorID.replaceAll("/", RSPACE_ROR_FORWARD_SLASH_DELIM)
        : "";
      try {
        const response: { data: RoRApiResponse } = await axios.get(
          "/system/ror/rorForID/" + searchTerm
        );
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

  const getCityCountryAddressesFromRoRDetails = () => {
    if (rorDetails && "addresses" in rorDetails) {
      //v1 api uses addresses
      return rorDetails.addresses.map((address, i) => {
        const cityCountry =
          address.city + ", " + rorDetails.country.country_name;
        return (
          <RorDetails key={i}>
            <h5>{cityCountry}</h5>
          </RorDetails>
        );
      });
    } else if (rorDetails) {
      //v2 api uses locations in place of addresses
      return rorDetails.locations.map((location) => {
        const cityCountry =
          location.geonames_details.name +
          ", " +
          location.geonames_details.country_name;
        return (
          <RorDetails>
            <h5>{cityCountry}</h5>
          </RorDetails>
        );
      });
    }
  };

  const getLinksFromRoRDetails = () => {
    if (rorDetails && "locations" in rorDetails) {
      return rorDetails.links.map((link) => {
        //v2 api
        return (
          <RorDetails>
            <h5>
              <a target="_blank" rel="noreferrer" href={link.value}>
                {link.value}
              </a>
            </h5>
          </RorDetails>
        );
      });
    } else if (rorDetails) {
      return rorDetails.links.map((link) => {
        //v1 api
        return (
          <RorDetails>
            <h5>
              <a target="_blank" rel="noreferrer" href={link}>
                {link}
              </a>
            </h5>
          </RorDetails>
        );
      });
    }
  };

  const getDisplayName = (): string | null => {
    if (rorDetails && "name" in rorDetails) {
      //v1 api
      return rorDetails.name;
    } else if (rorDetails && rorDetails.names) {
      return rorDetails.names
        .filter((name) => name.types.includes("ror_display"))
        .map((name) => name.value)[0];
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
  const rorDoesNotMatchCandidateRor = (): boolean => {
    return !rorMatchesCandidateRor();
  };

  return (
    <>
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <div>
            <h1>Research Organization Registry (ROR) Integration</h1>
            <RorHelpText>
              By associating a{" "}
              <a target="_blank" rel="noreferrer" href="https://ror.org">
                ROR ID
              </a>{" "}
              with your RSpace instance, you ensure the research outputs
              produced in RSpace are connected with your research organisation.
              All research outputs with a DOI will automatically include the ROR
              ID in their affiliation metadata.
            </RorHelpText>
          </div>
          <RorHelpText>
            <h2>Institutional ROR ID</h2>
          </RorHelpText>
          {!ror && (
            <GenericsearchBar
              handleSearch={handleSearch}
              style={{
                alignItems: "center",
                display: "flex",
                background: "white",
                border: "1px solid #808080",
              }}
              placeholder={"https://ror.org/02mhbdp94"}
              searchToolTip={"Search Registry"}
            />
          )}
          {!rorDetails && (
            <RorHelpText>
              You can search the{" "}
              <a target="_blank" rel="noreferrer" href="https://ror.org/search">
                ROR registry
              </a>{" "}
              to ensure you are adding the correct ROR ID. If your institution
              does not have a ROR ID, you can submit a{" "}
              <a
                target="_blank"
                rel="noreferrer"
                href="https://docs.google.com/forms/d/e/1FAIpQLSdJYaMTCwS7muuTa-B_CnAtCSkKzt19lkirAKG4u7umH9Nosg/viewform"
              >
                curation request form.
              </a>
            </RorHelpText>
          )}
          {candidateRor && rorDoesNotMatchCandidateRor() && !errorMessage && (
            <RorHelpText>
              ROR ID found. Click <b>Link</b> to associate with this RSpace
              Instance.
            </RorHelpText>
          )}
          {(rorMatchesCandidateRor() || (ror && !candidateRor)) &&
            !errorMessage && (
              <RorHelpText>
                A ROR ID is linked to this RSpace Instance. Click on{" "}
                <b>UNLINK</b> to remove the association. Future published or
                updated DOIs will not include the ROR ID.
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
              {getCityCountryAddressesFromRoRDetails()}
              {getLinksFromRoRDetails()}
              <RorDetails>
                <h5>Status: {rorDetails.status}</h5>
              </RorDetails>
            </>
          )}
          {errorMessage && (
            <Alert severity={getSeverity(errorMessage)}>{errorMessage}</Alert>
          )}
          {errorMessage && getSeverity(errorMessage) === "error" && (
            <RorHelpText>
              Please ensure the ROR ID is one of the following formats:
              <RorErrorHelpText>
                https://ror.org/02mhbdp94
              </RorErrorHelpText>,{" "}
              <RorErrorHelpText>ror.org/02mhbdp94</RorErrorHelpText>,{" "}
              <RorErrorHelpText>02mhbdp94</RorErrorHelpText>
            </RorHelpText>
          )}
          {candidateRor && rorDoesNotMatchCandidateRor() && !errorMessage && (
            <Button
              color="primary"
              data-test-id="ror-link"
              variant="contained"
              sx={{ marginTop: "10px" }}
              onClick={() => void updateRor()}
              startIcon={<FontAwesomeIcon icon="plus" />}
            >
              Link
            </Button>
          )}
          {(rorMatchesCandidateRor() || (ror && !candidateRor)) &&
            !errorMessage && (
              <Button
                color="primary"
                data-test-id="ror-link"
                variant="contained"
                sx={{ marginTop: "10px" }}
                onClick={() => void deleteRor()}
                startIcon={<FontAwesomeIcon icon="minus" />}
              >
                UnLink
              </Button>
            )}
        </ThemeProvider>
      </StyledEngineProvider>
    </>
  );
}

window.addEventListener("loadROR", () => {
  const domContainer = document.getElementById("rorIntegration");

  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(<RoRIntegration />);
  }
});

export default RoRIntegration;
