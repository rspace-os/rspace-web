"use strict";
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
import { makeStyles } from "tss-react/mui";
import { TransformWrapper, TransformComponent } from "react-zoom-pan-pinch";
import styled from "@emotion/styled";
import LoadingCircular from "../../components/LoadingCircular";

const useStyles = makeStyles()((theme) => ({
  radio: {
    marginBottom: "30px",
  },
  label: {
    marginBottom: "10px",
  },
  settings: {
    textAlign: "right",
  },
  imageWrapper: {
    width: "100%",
  },
  image: {
    display: "block",
    maxWidth: "100%",
    maxHeight: "65vh",
    width: "auto",
    height: "auto",
  },
  tools: {
    position: "absolute",
    bottom: "60px",
    zIndex: "100",
  },
}));

const ImageWrapper = styled.div`
  .react-transform-component,
  .react-transform-element {
    width: 100%;
  }

  img {
    margin: 0 auto;
  }
`;

export default function DnaPreview(props) {
  const { classes } = useStyles();
  const [image, setImage] = React.useState(null);
  const [loading, setLoading] = React.useState(true);
  const [state, setState] = React.useState({
    linear: false,
    showEnzymes: true,
    showORFs: true,
  });
  const [oldState, setOldState] = React.useState({
    linear: false,
    showEnzymes: true,
    showORFs: true,
  });

  // handle apply
  useEffect(() => {
    setImage(null);
    fetchData();
    setOldState({ ...state });
  }, [props.clicked]);

  useEffect(() => {
    updateDisabled();
  }, [state, oldState]);

  const handleChange = (name, value) => {
    setState({
      ...state,
      [name]: value === "true",
    });
  };

  const fetchData = async () => {
    setLoading(true);
    setImage(
      `/molbiol/dna/png/${props.id}?linear=${state.linear}&showEnzymes=${state.showEnzymes}&showORFs=${state.showORFs}`
    );
  };

  const updateDisabled = () => {
    let changed =
      state.linear === oldState.linear &&
      state.showEnzymes === oldState.showEnzymes &&
      state.showORFs === oldState.showORFs;

    props.setDisabled(changed);
  };

  const onImageError = (e) => {
    RS.confirm(
      "An error has occurred. This could be because the Snapgene server is down or the DNA sequence is invalid.",
      "warning",
      "infinite"
    );
    setLoading(false);
  };

  return (
    <>
      <Grid item xs={8}>
        {image && (
          <TransformWrapper
            velocitySensitivity={5000}
            step={20}
            defaultScale={1}
          >
            {({ zoomIn, zoomOut, resetTransform, ...rest }) => (
              <ImageWrapper>
                <ButtonGroup
                  className="tools"
                  className={classes.tools}
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
                    className={classes.image}
                    onLoad={(e) => setLoading(false)}
                    onError={(e) => onImageError(e)}
                  />
                </TransformComponent>
              </ImageWrapper>
            )}
          </TransformWrapper>
        )}
        {loading && <LoadingCircular />}
      </Grid>
      <Grid item xs={2} className={classes.settings}>
        <FormControl component="fieldset" className={classes.radio}>
          <FormLabel component="legend" className={classes.label}>
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
        <FormControl component="fieldset" className={classes.option}>
          <FormControlLabel
            control={
              <Checkbox
                checked={state.showEnzymes}
                onChange={(event) =>
                  handleChange(
                    event.target.value,
                    (!state.showEnzymes).toString()
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
        <FormControl component="fieldset" className={classes.option}>
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
