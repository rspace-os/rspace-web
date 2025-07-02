"use strict";
import React, { useEffect } from "react";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import IconButton from "@mui/material/IconButton";
import CloseIcon from "@mui/icons-material/Close";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import axios from "@/common/axios";
import { makeStyles } from "tss-react/mui";
import { isUrl } from "../util/Util";

const useStyles = makeStyles()((theme) => ({
  text: {
    fontSize: "14px !important",
    padding: "2px",
    color: "#1465b7 !important",
  },
  paper: {
    padding: theme.spacing(0),
  },
  cardContent: {
    padding: "0",
    paddingBottom: "0px !important",
  },
  card: {
    margin: "0px",
    borderRadius: "0px",
    marginBottom: "10px",
    zIndex: 1,
    backgroundColor: "rgb(240,240,240)",
  },
  cardHeader: {
    padding: "7px",
    background: "white",
  },
  tableCell: {
    maxWidth: "150px",
    overflowX: "auto",
    whiteSpace: "nowrap",
  },
  table: {
    marginBottom: "0px",
    backgroundColor: "white",
  },
  avatar: {
    maxHeight: "48px",
    maxWidth: "180px",
    width: "auto",
    height: "auto",
  },
  closeButton: {
    margin: "10px",
  },
}));

export default function ChemCard(props) {
  const { classes } = useStyles();
  const [chem, setChem] = React.useState({
    reactants: [],
    products: [],
    molecules: [],
  });

  // fetch chem details
  const fetchChemDetails = () => {
    // eslint-disable-next-line no-undef
    const publicView = $("#public_document_view").length > 0;
    const publicUrlPrepend = publicView ? "/public/publicView" : "";
    let url =
      publicUrlPrepend + `/chemical/ajax/getInfo?chemId=${props.item.id}`;
    axios
      .get(url)
      .then((response) => {
        if (response.data.data) {
          setChem(response.data.data);
        } else {
          console.warn(
            "error when retrieving info for chem elem " + props.item.id,
            response.data.error
          );
        }
      })
      .catch((error) => {
        console.warn(error);
      });
  };

  useEffect(() => {
    fetchChemDetails();
  }, []);

  const chemInfoTable = (chemical) => {
    return (
      <React.Fragment key={chemical.formula}>
        <TableRow style={{ background: "rgb(240,240,240" }}>
          <TableCell component="th" scope="row"></TableCell>
          <TableCell component="th" scope="row" align="center">
            {chemical.role}
          </TableCell>
        </TableRow>
        {chemical.name != "" && (
          <TableRow>
            <TableCell component="th" scope="row">
              Name
            </TableCell>
            <TableCell align="right" className={classes.tableCell}>
              {chemical.name}
            </TableCell>
          </TableRow>
        )}
        <TableRow>
          <TableCell component="th" scope="row">
            Formula
          </TableCell>
          <TableCell
            align="right"
            className={classes.tableCell}
            dangerouslySetInnerHTML={{
              __html: _getHtmlFormattedFormula(chemical.formula),
            }}
          ></TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Mass
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.mass}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            {/* Monoisotopic mass symbol = Exact mass */}M<sub>mi</sub>
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.exactMass}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Charge
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.formalCharge}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Bonds
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.bondCount}
          </TableCell>
        </TableRow>
        <TableRow>
          <TableCell component="th" scope="row">
            Atoms
          </TableCell>
          <TableCell align="right" className={classes.tableCell}>
            {chemical.atomCount}
          </TableCell>
        </TableRow>
        {Object.entries(JSON.parse(chemical.additionalMetadata ?? "{}")).map(
          ([k, v]) => (
            <TableRow>
              <TableCell component="th" scope="row">
                {k}
              </TableCell>
              <TableCell align="right" className={classes.tableCell}>
                {isUrl(v) ? (
                  <a href={v} target="_blank" rel="noopener noreferrer">
                    {v}
                  </a>
                ) : (
                  v
                )}
              </TableCell>
            </TableRow>
          )
        )}
      </React.Fragment>
    );
  };

  return (
    <Card
      className={classes.card}
      elevation={props.inline ? 0 : 4}
      style={
        props.inline
          ? {
              height:
                props.height < 200
                  ? "200px"
                  : `${parseInt(props.height) + 6}px`,
              overflowY: "auto",
              margin: "0px",
            }
          : {}
      }
    >
      {!props.inline && (
        <CardHeader
          className={classes.cardHeader}
          subheader={
            <img
              className={classes.avatar}
              src={props.item.imageSrc}
              height="48px"
            />
          }
          action={
            <IconButton
              size="small"
              aria-label="close"
              onClick={() => props.onClose(props.item.id)}
              className={classes.closeButton}
            >
              <CloseIcon />
            </IconButton>
          }
        />
      )}
      <CardContent className={classes.cardContent}>
        <Table size="small" className={classes.table}>
          <TableBody>
            {chem.reaction && (
              <TableRow>
                <TableCell component="th" scope="row">
                  Formula
                </TableCell>
                <TableCell
                  align="right"
                  className={classes.tableCell}
                  dangerouslySetInnerHTML={{
                    __html: _getHtmlFormattedFormula(chem.formula),
                  }}
                ></TableCell>
              </TableRow>
            )}
            {chem.reactants.map((r) => chemInfoTable(r))}
            {chem.products.map((p) => chemInfoTable(p))}
            {chem.molecules.map((m) => chemInfoTable(m))}
            {Object.entries(JSON.parse(chem.additionalMetadata ?? "{}")).map(
              ([k, v]) => (
                <TableRow key={k}>
                  <TableCell component="th" scope="row">
                    {k}
                  </TableCell>
                  <TableCell align="right" className={classes.tableCell}>
                    {isValidUrl(v) ? (
                      <a href={v} target="_blank" rel="noopener noreferrer">
                        {v}
                      </a>
                    ) : (
                      v
                    )}
                  </TableCell>
                </TableRow>
              )
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
