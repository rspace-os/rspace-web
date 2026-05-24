"use strict";
/* global RS */
import React, { useEffect } from "react";
import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import Grid from "@mui/material/Grid";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import Box from "@mui/material/Box";
import { TransformWrapper, TransformComponent } from "react-zoom-pan-pinch";
import LoadingCircular from "../../components/LoadingCircular";
import PropTypes from "prop-types";

/**
 * Preview pane for SnapGene DNA images.
 */
export default function DnaPreview(props) {
  const { clicked, id, setDisabled } = props;
  const [state, setState] = React.useState({
    linear: false,
    showEnzymes: true,
    showORFs: true,
  });
  const [loadedImage, setLoadedImage] = React.useState(null);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const appliedState = React.useMemo(() => ({ ...state }), [clicked]);

  const image = `/molbiol/dna/png/${id}?linear=${appliedState.linear}&showEnzymes=${appliedState.showEnzymes}&showORFs=${appliedState.showORFs}`;

  const loading = loadedImage !== image;

  const handleChange = (name, value) => {
    setState({
      ...state,
      [name]: value === "true",
    });
  };

  useEffect(() => {
    const changed =
      state.linear === appliedState.linear &&
      state.showEnzymes === appliedState.showEnzymes &&
      state.showORFs === appliedState.showORFs;

    setDisabled(changed);
  }, [appliedState, setDisabled, state]);

  const onImageError = () => {
    RS.confirm(
      "An error has occurred. This could be because the Snapgene server is down or the DNA sequence is invalid.",
      "warning",
      "infinite",
    );
    setLoadedImage(image);
  };

  return (
    <>
      <Grid size={8}>
        <TransformWrapper velocitySensitivity={5000} step={20} defaultScale={1}>
          {({ zoomIn, zoomOut, resetTransform }) => (
            <Box
              sx={{
                "& .react-transform-component, & .react-transform-element": {
                  width: "100%",
                },
                "& img": {
                  margin: "0 auto",
                },
              }}
            >
              <ButtonGroup
                sx={{
                  position: "absolute",
                  bottom: "60px",
                  zIndex: 100,
                }}
                size="small"
                aria-label="small outlined button group"
              >
                <Button onClick={zoomIn}>+</Button>
                <Button onClick={zoomOut}>-</Button>
                <Button onClick={resetTransform}>Reset</Button>
              </ButtonGroup>
              <TransformComponent className="hello" style={{ width: "100%" }}>
                <img
                  src={image}
                  alt="DNA preview"
                  style={{
                    display: "block",
                    maxWidth: "100%",
                    maxHeight: "65vh",
                    width: "auto",
                    height: "auto",
                  }}
                  onLoad={() => setLoadedImage(image)}
                  onError={onImageError}
                />
              </TransformComponent>
            </Box>
          )}
        </TransformWrapper>
        {loading && <LoadingCircular />}
      </Grid>
      <Grid sx={{ textAlign: "right" }} size={2}>
        <FormControl component="fieldset" sx={{ mb: "30px" }}>
          <FormLabel component="legend" sx={{ mb: "10px" }}>
            Image type
          </FormLabel>
          <RadioGroup
            aria-label="Linear choice"
            name="linear"
            value={state.linear.toString()}
            onChange={(event) =>
              handleChange(event.target.name, event.target.value)
            }
          >
            <FormControlLabel
              value="true"
              control={<Radio color="primary" />}
              label="Linear"
              labelPlacement="start"
            />
            <FormControlLabel
              value="false"
              control={<Radio color="primary" />}
              label="Circular"
              labelPlacement="start"
            />
          </RadioGroup>
        </FormControl>
        <br />
        <FormControl component="fieldset">
          <FormControlLabel
            control={
              <Checkbox
                checked={state.showEnzymes}
                onChange={(event) =>
                  handleChange(
                    event.target.value,
                    (!state.showEnzymes).toString(),
                  )
                }
                value="showEnzymes"
                color="primary"
              />
            }
            label="Show enzymes"
            labelPlacement="start"
          />
        </FormControl>
        <br />
        <FormControl component="fieldset">
          <FormControlLabel
            control={
              <Checkbox
                checked={state.showORFs}
                onChange={(event) =>
                  handleChange(event.target.value, (!state.showORFs).toString())
                }
                value="showORFs"
                color="primary"
              />
            }
            label="Show ORFs"
            labelPlacement="start"
          />
        </FormControl>
      </Grid>
    </>
  );
}

DnaPreview.propTypes = {
  clicked: PropTypes.any,
  id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  setDisabled: PropTypes.func.isRequired,
};
