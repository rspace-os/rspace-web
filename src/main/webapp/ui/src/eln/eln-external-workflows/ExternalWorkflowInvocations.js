// @flow
import CssBaseline from "@mui/material/CssBaseline";
import materialTheme from "@/theme";
import TitledBox from "@/Inventory/components/TitledBox";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React, {Node, useEffect, useState} from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import {ThemeProvider} from "@mui/material/styles";
import {withStyles} from "Styles";
import Badge from "@mui/material/Badge";
import Fab from "@mui/material/Fab";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {createSvgIcon, Icon} from "@mui/material";
import SvgIcon from "@mui/material/SvgIcon";
import {makeStyles} from "tss-react/mui";
import axios from "@/common/axios";
import {DataGrid} from "@mui/x-data-grid";
import {DataGridColumn} from "@/util/table";
import Link from "@mui/material/Link";
import MaterialsDialog
  from "@/eln-inventory-integration/MaterialsListing/MaterialsDialog";
import ExternalWorkflowDialog
  from "@/eln/eln-external-workflows/ExternalWorkflowDialog";

function ExternalWorkflowInvocations({
  isForNotebookPage = false,
  fieldId
}) {
  const [count,setCount] = useState(0);
  const [isVisible, setIsVisible] = useState(false);
  const [galaxyDataSummary, setGalaxyDataSummary] = useState([]);
  const [showDialog, setShowDialog] = useState(false);
  const BUTTON_TOP = isForNotebookPage ? 115: 100;
  const BUTTON_RIGHT = isForNotebookPage ? 15: -24;
  const BUTTON_BOTTOM = BUTTON_TOP-48;
  const { classes } = useStyles({ BUTTON_TOP, BUTTON_RIGHT,BUTTON_BOTTOM });
  const CustomBadge = withStyles< {| children: Node, count: number  |},{ root: string, badge: string }>(() => ({
    root: {
      position: "sticky",
      top: BUTTON_TOP,
      zIndex: 1, // so it appears above the TinyMCE Editor
      pointerEvents: "auto",
    },
    badge: {
      transform: "none",
    },
  }))(({classes, children, count}) => (
      <Badge badgeContent={count} color="primary" classes={classes}>
        {children}
      </Badge>
  ));

  useEffect( () => {
    async function fetchData() {
      await updateGalaxyDataSummary();
    }
    fetchData();
  }, []);

  const updateGalaxyDataSummary = async () => {
    const data = await getGalaxyData();
    if (data === null || data === "") {
      setGalaxyDataSummary([])
    } else {
      setGalaxyDataSummary(data);
      setCount(data.filter(d => d.galaxyInvocationName !== null).length);
    }
  }

  const getGalaxyData = async () => {
    return (await axios.get(
        "/apps/galaxy/getSummaryGalaxyDataForRSpaceField/" + fieldId)).data;
  }

  window.addEventListener("galaxy-used", function (e) {
    const eFieldId = e.detail.fieldId.substring(4);
    if (eFieldId === fieldId) {
      setIsVisible(true);
    }
  });

  return (

      <>  {(isVisible || galaxyDataSummary.length > 0) && (
          <>
            <div className={classes.launcherWrapper}>
              <CustomBadge count={count}>
                <Fab
                    onClick={async () => {
                      await updateGalaxyDataSummary();
                      setShowDialog(true);
                    }}
                    color="primary"
                    size="medium"
                    aria-label="Show computational workflows associated with this field"
                    aria-haspopup="menu"
                    className={classes.fab}
                >
                  <WorkFlowIcon width="100%" viewBox="0 0 225 225"
                                enableBackground="new 0 0 225 225"></WorkFlowIcon>
                </Fab>
              </CustomBadge>
            </div>
            <ExternalWorkflowDialog open={showDialog} setOpen={setShowDialog}
                                    galaxySummaryReport={galaxyDataSummary}/>
          </>
      )}
      </>
  );
}

const  WorkFlowIcon = createSvgIcon(<svg id="Layer_1" xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 20 20">
      <g>
        <circle cx="5.055" cy="3.51" r="2.332" fill="#fff"/>
        <circle cx="5.154" cy="10" r="2.332" fill="#fff"/>
        <circle cx="5.154" cy="16.49" r="2.332" fill="#fff"/>
      </g>
      <line x1="6.443" y1="10" x2="17.276" y2="10" fill="#fff" stroke="#fff" stroke-miterlimit="10" stroke-width="1.4"/>
      <path d="M4.932,3.535c3.136,0,5.677,2.894,5.677,6.465s-2.542,6.465-5.677,6.465" fill="none" stroke="#fff" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.4"/>
      <polyline points="15.073 7.867 17.206 10 15.073 12.133" fill="none" stroke="#fff" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.4"/>
    </svg>
    ,
    'WorkFlow'
);

const useStyles = makeStyles()(
    (theme, {BUTTON_TOP, BUTTON_RIGHT, BUTTON_BOTTOM}) => ({
      launcherWrapper: {
        position: "absolute",
        top: BUTTON_TOP,
        right: BUTTON_RIGHT,
        bottom: BUTTON_BOTTOM,
        pointerEvents: "none",
        "@media print": {
          display: "none",
        },
      },
      growTransform: {transformOrigin: "center right"},
      primary: {color: theme.palette.primary.main},
      popper: {
        zIndex: 1, // so it appears above the TinyMCE Editor
        pointerEvents: "auto",
      },
      itemName: {fontWeight: "bold"},
      itemText: {
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        width: "240px",
      },
      fab: {
        zIndex: "initial",
      },
    }));
export default ExternalWorkflowInvocations;
