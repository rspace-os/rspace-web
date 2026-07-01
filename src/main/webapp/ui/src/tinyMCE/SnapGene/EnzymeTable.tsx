import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import LoadingCircular from "../../components/LoadingCircular";
import { getSorting, paginationOptions } from "../../util/table";
import type { Order } from "../../util/types";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function EnzymeTable(props: any) {
  const { t } = useTranslation("apps");
  const enzymeSetOptions = {
    UNIQUE_SIX_PLUS: t("tinyMce.snapGene.enzymeSetOptions.uniqueSixPlus"),
    UNIQUE: t("tinyMce.snapGene.enzymeSetOptions.unique"),
    SIX_PLUS: t("tinyMce.snapGene.enzymeSetOptions.sixPlus"),
    UNIQUE_AND_DUAL: t("tinyMce.snapGene.enzymeSetOptions.uniqueAndDual"),
    COMMERCIAL_NONREDUNDANT: t("tinyMce.snapGene.enzymeSetOptions.commercialNonredundant"),
  };
  const headCells = [
    { id: "name", numeric: false, disablePadding: false, label: t("tinyMce.snapGene.columns.enzyme") },
    {
      id: "bottomCutPosition",
      numeric: true,
      disablePadding: false,
      label: t("tinyMce.snapGene.columns.bottomCutPosition"),
    },
    { id: "topCutPosition", numeric: true, disablePadding: false, label: t("tinyMce.snapGene.columns.topCutPosition") },
  ];
  const [order, setOrder] = React.useState<Order>("desc");
  const [orderBy, setOrderBy] = React.useState("enzyme");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(10);
  const [loading, setLoading] = React.useState(true);
  const [enzymeSet, setEnzymeSet] = React.useState("UNIQUE_SIX_PLUS");
  const [oldEnzymeSet, setOldEnzymeSet] = React.useState("UNIQUE_SIX_PLUS");
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [enzymeList, setEnzymeList] = React.useState<Array<any>>([]);

  const fetchEnzymes = () => {
    setLoading(true);

    const url = `/molbiol/dna/enzymes/${props.id}?enzymeSet=${enzymeSet}`;
    axios
      .get(url)
      .then((response) => {
        generateEnzymeList(response.data.enzymes);
      })
      .catch((error) => {
        RS.confirm(error.response.data, "warning", "infinite");
      })
      .finally(() => {
        setLoading(false);
      });
  };

  // handle apply
  useEffect(() => {
    setPage(0);
    setEnzymeList([]);
    setOldEnzymeSet(enzymeSet);
    fetchEnzymes();
  }, [props.clicked]);

  useEffect(() => {
    updateDisabled();
  }, [enzymeSet, oldEnzymeSet]);

  const handleChange = (value: string) => {
    setEnzymeSet(value);
  };

  const handleRequestSort = (_event: React.MouseEvent<HTMLSpanElement>, property: string) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (_event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const generateEnzymeList = (list: Array<any>) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    const new_list = list.map((enzyme: any) => {
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      return enzyme.hits.map((hit: any) => {
        return {
          name: enzyme.name,
          id: enzyme.id,
          topCutPosition: hit.topCutPosition,
          bottomCutPosition: hit.bottomCutPosition,
        };
      });
    });
    setEnzymeList(new_list.flat());
  };

  const updateDisabled = () => {
    props.setDisabled(enzymeSet === oldEnzymeSet);
  };

  const emptyRows = rowsPerPage - Math.min(rowsPerPage, enzymeList.length - page * rowsPerPage);

  return (
    <>
      <Grid size={8}>
        {loading && <LoadingCircular />}
        {!loading && (
          <>
            <TableContainer sx={{ maxHeight: "387px" }}>
              <Table stickyHeader size="small" aria-label={t("tinyMce.snapGene.enzymeTableLabel")}>
                <EnhancedTableHead
                  headCells={headCells}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={enzymeList.length}
                />
                <TableBody>
                  {enzymeList
                    .toSorted(getSorting(order, orderBy))
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                    .map((enzyme: any) => (
                      <TableRow
                        hover
                        tabIndex={-1}
                        key={`${enzyme.name}-${enzyme.id}-${enzyme.topCutPosition}-${enzyme.bottomCutPosition}`}
                      >
                        <TableCell align="left">{enzyme.name}</TableCell>
                        <TableCell align="right">{enzyme.bottomCutPosition}</TableCell>
                        <TableCell align="right">{enzyme.topCutPosition}</TableCell>
                      </TableRow>
                    ))}
                  {emptyRows > 0 && (
                    <TableRow sx={{ height: 33 * emptyRows }}>
                      <TableCell colSpan={6} />
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={paginationOptions(enzymeList.length)}
              component="div"
              count={enzymeList.length}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          </>
        )}
      </Grid>
      <Grid sx={{ textAlign: "right" }} size={2}>
        <FormControl component="fieldset" sx={{ mb: "30px" }}>
          <FormLabel component="legend" sx={{ mb: "10px" }}>
            {t("tinyMce.snapGene.enzymeSets")}
          </FormLabel>
          <RadioGroup
            aria-label={t("tinyMce.snapGene.enzymeTypeLabel")}
            name="enzymeSet"
            value={enzymeSet}
            onChange={(event) => handleChange(event.target.value)}
          >
            {Object.keys(enzymeSetOptions).map((key) => (
              <FormControlLabel
                value={key}
                key={key}
                control={<Radio color="primary" />}
                label={enzymeSetOptions[key as keyof typeof enzymeSetOptions]}
                labelPlacement="start"
              />
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
    </>
  );
}
