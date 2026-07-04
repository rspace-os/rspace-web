import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import React, { useCallback, useContext, useEffect } from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import LoadingCircular from "../../components/LoadingCircular";

type FastaViewProps = {
  id: string | number;
  clicked?: number;
  setDisabled: (disabled: boolean) => void;
};

/**
 * Displays the FASTA sequence for a SnapGene file and lets the user copy it.
 */
export default function FastaView({ id, setDisabled }: FastaViewProps) {
  const [loading, setLoading] = React.useState(true);
  const [sequence, setSequence] = React.useState("");
  const { addAlert } = useContext(AlertContext);

  const fetchData = useCallback(() => {
    setLoading(true);

    const url = `/molbiol/dna/fasta/${id}`;
    axios
      .get(url)
      .then((response) => {
        setSequence(response.data);
      })
      .catch((error) => {
        addAlert(mkAlert({ message: error.response.data, variant: "warning", isInfinite: true }));
      })
      .finally(() => {
        setLoading(false);
      });
  }, [id, addAlert]);

  const copyToClipboard = () => {
    const el = document.getElementById("copy-text") as HTMLTextAreaElement | null; // Create a <textarea> element
    const selected =
      (document.getSelection()?.rangeCount ?? 0) > 0 // Check if there is any content selected previously
        ? document.getSelection()?.getRangeAt(0) // Store selection if found
        : false; // Mark as false to know no selection existed before
    el?.select(); // Select the <textarea> content
    try {
      document.execCommand("copy");
      addAlert(mkAlert({ message: "Copied to clipboard", variant: "notice", duration: 3000 }));
    } catch {
      addAlert(
        mkAlert({ message: "Couldn't copy to clipboard. Try again manually.", variant: "warning", duration: 5000 }),
      );
    }
    if (el) {
      document.body.removeChild(el); // Remove the <textarea> element
    }
    if (selected) {
      // If a selection existed before copying
      document.getSelection()?.removeAllRanges(); // Unselect everything on the HTML document
      document.getSelection()?.addRange(selected);
    }
  };

  // immediately fetch the default enzyme table
  useEffect(() => {
    setDisabled(true);
    const timeoutId = window.setTimeout(() => {
      fetchData();
    }, 0);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [fetchData, setDisabled]);

  return (
    <>
      <Grid size={8}>
        {loading && <LoadingCircular />}
        {!loading && (
          <>
            <Typography
              gutterBottom
              sx={{
                maxHeight: "calc(100vh - 300px)",
                overflowY: "auto",
                width: "auto",
                fontFamily: "Courier New",
              }}
            >
              {sequence}
            </Typography>

            <textarea id="copy-text" style={{ position: "absolute", left: "-20000px" }}>
              {sequence}
            </textarea>
          </>
        )}
      </Grid>
      <Grid sx={{ textAlign: "right" }} size={2}>
        <Button onClick={copyToClipboard} color="primary" variant="outlined" disabled={loading}>
          Copy to Clipboard
        </Button>
      </Grid>
    </>
  );
}
