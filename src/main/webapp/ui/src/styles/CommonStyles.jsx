import Box from "@mui/material/Box";
import PropTypes from "prop-types";
import React from "react";

const cardWrapperSx = {
  "& .MuiCard-root": {
    margin: "0px 0px 20px 0px",
    "& .title": {
      fontSize: "18px",
      fontWeight: "bold",
      margin: "10px 0px",
      paddingLeft: "15px",
    },
    "& .MuiCardHeader-root": {
      padding: "10px 15px",
    },
    "& .MuiCardContent-root": {
      padding: "0px 15px 5px 15px",
    },
    "& .MuiCardActions-root": {
      justifyContent: "space-between",
      "& button": {
        width: "auto",
        "& svg": {
          marginRight: "5px",
        },
      },
      "& .group-right button": {
        marginLeft: "10px",
      },
    },
  },
  "& textarea:focus, & textarea:hover": {
    backgroundColor: "transparent !important",
  },
};

/**
 * Wrapper that applies the common card spacing styles used by legacy pages.
 */
export function CardWrapper(props) {
  return <Box {...props} sx={{ ...cardWrapperSx, ...props.sx }} />;
}

CardWrapper.propTypes = {
  sx: PropTypes.oneOfType([PropTypes.array, PropTypes.func, PropTypes.object]),
};
