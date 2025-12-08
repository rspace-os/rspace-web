import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import React, { useEffect } from "react";
import { makeStyles } from "tss-react/mui";
import axios from "@/common/axios";
import LoadingCircular from "../../components/LoadingCircular";

const useStyles = makeStyles()((_theme) => ({
    settings: {
        textAlign: "right",
    },
    fastaString: {
        maxHeight: "calc(100vh - 300px)",
        overflowY: "auto",
        width: "auto",
        fontFamily: "Courier New",
    },
}));

export default function FastaView(props) {
    const { classes } = useStyles();
    const [loading, setLoading] = React.useState(true);
    const [sequence, setSequence] = React.useState("");

    const fetchData = () => {
        setLoading(true);

        const url = `/molbiol/dna/fasta/${props.id}`;
        axios
            .get(url)
            .then((response) => {
                setSequence(response.data);
            })
            .catch((error) => {
                RS.confirm(error.response.data, "warning", "infinite");
            })
            .finally(() => {
                setLoading(false);
            });
    };

    const copyToClipboard = () => {
        const el = document.getElementById("copy-text"); // Create a <textarea> element
        const selected =
            document.getSelection().rangeCount > 0 // Check if there is any content selected previously
                ? document
                      .getSelection()
                      .getRangeAt(0) // Store selection if found
                : false; // Mark as false to know no selection existed before
        el.select(); // Select the <textarea> content
        try {
            const successful = document.execCommand("copy");
            const _msg = successful ? "successful" : "unsuccessful";
            RS.confirm("Copied to clipboard", "notice", 3000);
        } catch (_err) {
            RS.confirm("Couldn't copy to clipboard. Try again manually.", "warning", 5000);
        }
        document.body.removeChild(el); // Remove the <textarea> element
        if (selected) {
            // If a selection existed before copying
            document.getSelection().removeAllRanges(); // Unselect everything on the HTML document
            document.getSelection().addRange(selected);
        }
    };

    // immediately fetch the default enzyme table
    useEffect(() => {
        fetchData();
        props.setDisabled(true);
    }, [fetchData, props.setDisabled]);

    return (
        <>
            <Grid item xs={8}>
                {loading && <LoadingCircular />}
                {!loading && (
                    <>
                        <Typography gutterBottom className={classes.fastaString}>
                            {sequence}
                        </Typography>

                        <textarea id="copy-text" style={{ position: "absolute", left: "-20000px" }}>
                            {sequence}
                        </textarea>
                    </>
                )}
            </Grid>
            <Grid item xs={2} className={classes.settings}>
                <Button onClick={copyToClipboard} color="primary" variant="outlined" disabled={loading}>
                    Copy to Clipboard
                </Button>
            </Grid>
        </>
    );
}
