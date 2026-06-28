import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import Checkbox from "@mui/material/Checkbox";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { TransformComponent, TransformWrapper } from "react-zoom-pan-pinch";
import LoadingCircular from "../../components/LoadingCircular";

declare const RS: {
  confirm: (message: string, level: "warning" | "notice", timeout: string | number) => void;
};

type DnaPreviewProps = {
  clicked: unknown;
  id: string | number;
  setDisabled: (disabled: boolean) => void;
};

type DnaPreviewState = {
  linear: boolean;
  showEnzymes: boolean;
  showORFs: boolean;
};

/**
 * Preview pane for SnapGene DNA images.
 */
export default function DnaPreview(props: DnaPreviewProps) {
  const { t } = useTranslation("apps");
  const { clicked, id, setDisabled } = props;
  const [state, setState] = React.useState<DnaPreviewState>({
    linear: false,
    showEnzymes: true,
    showORFs: true,
  });
  const [loadedImage, setLoadedImage] = React.useState<string | null>(null);

  const appliedState = React.useMemo(() => ({ ...state }), [clicked]);

  const image = `/molbiol/dna/png/${id}?linear=${appliedState.linear}&showEnzymes=${appliedState.showEnzymes}&showORFs=${appliedState.showORFs}`;

  const loading = loadedImage !== image;

  const handleChange = (name: keyof DnaPreviewState, value: string) => {
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
        <TransformWrapper
          initialScale={1}
          wheel={{ step: 20 }}
          pinch={{ step: 20 }}
          velocityAnimation={{ sensitivityMouse: 5000, sensitivityTouch: 5000 }}
        >
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
                aria-label={t("tinyMce.snapGene.zoomControlsAria")}
              >
                <Button onClick={() => zoomIn()}>+</Button>
                <Button onClick={() => zoomOut()}>-</Button>
                <Button onClick={() => resetTransform()}>{t("tinyMce.snapGene.reset")}</Button>
              </ButtonGroup>
              <TransformComponent wrapperStyle={{ width: "100%" }}>
                <img
                  src={image}
                  alt={t("tinyMce.snapGene.dnaPreviewAlt")}
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
            {t("tinyMce.snapGene.imageType")}
          </FormLabel>
          <RadioGroup
            aria-label={t("tinyMce.snapGene.linearChoiceAria")}
            name="linear"
            value={state.linear.toString()}
            onChange={(event) => handleChange(event.target.name as keyof DnaPreviewState, event.target.value)}
          >
            <FormControlLabel
              value="true"
              control={<Radio color="primary" />}
              label={t("tinyMce.snapGene.linear")}
              labelPlacement="start"
            />
            <FormControlLabel
              value="false"
              control={<Radio color="primary" />}
              label={t("tinyMce.snapGene.circular")}
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
                  handleChange(event.target.value as keyof DnaPreviewState, (!state.showEnzymes).toString())
                }
                value="showEnzymes"
                color="primary"
              />
            }
            label={t("tinyMce.snapGene.showEnzymes")}
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
                  handleChange(event.target.value as keyof DnaPreviewState, (!state.showORFs).toString())
                }
                value="showORFs"
                color="primary"
              />
            }
            label={t("tinyMce.snapGene.showORFs")}
            labelPlacement="start"
          />
        </FormControl>
      </Grid>
    </>
  );
}
