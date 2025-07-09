"use strict";
import React, { useState, useEffect } from "react";
import ChemCard from "./chemCard";
import { createRoot } from "react-dom/client";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()((theme) => ({
  wrapper: {
    display: "table",
    border: "1px solid rgb(200,200,200)",
    borderCollapse: "collapse",
    margin: "10px 0px",
  },
}));

export default function PreviewInfo(props) {
  const { classes } = useStyles();
  useEffect(() => {
    document.dispatchEvent(new Event("images-replaced"));
  }, []);
  return (
    <span className={classes.wrapper}>
      <div style={{ display: "flex", minHeight: "200px", maxHeight: "334px" }}>
        {/* TODO: make scrolling */}
        <div style={{ alignSelf: "center" }}>
          <img
            id={props.item.id}
            className={props.item.class}
            src={props.item.src}
            width={props.item.width}
            height={props.item.height}
            data-rsrevision={props.item["data-rsrevision"]}
            data-fullwidth={props.item["data-fullwidth"]}
            data-fullheight={props.item["data-fullheight"]}
            data-chemfileid={props.item["data-chemfileid"]}
          />
        </div>
        <ChemCard item={props.item} inline />
      </div>
    </span>
  );
}

function render(attributes, element) {
  const root = createRoot(element);
  root.render(<PreviewInfo item={{ ...attributes }} />);
}

document.addEventListener("document-placed", function (e) {
  $.fn.getAttributes = function () {
    var attributes = {};
    if (this.length) {
      $.each(this[0].attributes, function (i, attr) {
        attributes[attr.name] = attr.value;
      });
    }
    return attributes;
  };
  let domElements = e.detail ? $(`#div_${e.detail} img.chem`) : $("img.chem");

  domElements.each((i) => {
    let domContainer = $(domElements[i]);
    let parent = domContainer.parent();
    let attributes = domContainer.getAttributes();
    parent.find("img.chem").remove();
    let contents = parent.html();

    const root = createRoot(parent[0]);
    root.render(<PreviewInfo item={{ ...attributes }} />);
    parent.append(contents);
  });

  // Tell React that a new document was placed into the dom
  document.dispatchEvent(new Event("images-replaced"));
});

document.addEventListener("chem-updated", function (e) {
  $.fn.getAttributes = function () {
    var attributes = {};
    if (this.length) {
      $.each(this[0].attributes, function (i, attr) {
        attributes[attr.name] = attr.value;
      });
    }
    return attributes;
  };

  let domElements = e.detail ? $(`#div_${e.detail} img.chem`) : $("img.chem");

  domElements.each((i) => {
    let domContainer = $(domElements[i]);
    let span = domContainer.closest("span");
    render(domContainer.getAttributes(), span[0]);
  });
});
