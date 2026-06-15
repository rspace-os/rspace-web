import React from "react";
import { withStyles } from "Styles";
import { type emptyObject } from "../../../../util/types";

const InstrumentHeader = withStyles<emptyObject, { svg: string }>(() => ({
  svg: {
    height: 67,
    opacity: 0.7,
    position: "absolute",
    bottom: -15,
    width: 67,
    right: -19,
  },
}))(({ classes }) => (
  <svg
    version="1.1"
    x="0px"
    y="0px"
    className={classes.svg}
    width="200px"
    height="200px"
    viewBox="0 0 200 200"
    enableBackground="new 0 0 200 200"
  >
    <g>
      <g>
        <path d="M91.656,69.778c17.506,17.506,17.506,45.889,0,63.396-6.745,6.745-15.105,10.891-23.835,12.439l3.968,18.038c12.031-2.282,23.526-8.079,32.838-17.391,24.67-24.67,24.67-64.668,0-89.338l-12.971,12.857Z" fill="#f2d1c2" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
        <rect x="-.548" y="118.303" width="51.395" height="9.007" rx="4.504" ry="4.504" transform="translate(94.204 18.186) rotate(45)" fill="#fcf2ed" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
        <rect x="17.436" y="168.679" width="79.999" height="12.439" rx="4.504" ry="4.504" fill="#f2d1c2" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
        <rect x="38.936" y="136.679" width="37" height="31.643" rx="4.504" ry="4.504" fill="#f2d1c2" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
        <circle cx="57.436" cy="152.679" r="7" fill="#fcf2ed" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
        <g>
          <rect x="68.835" y="23.186" width="32.39" height="79.091" rx="4.317" ry="4.317" transform="translate(69.262 -41.751) rotate(45)" fill="#fcf2ed" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
          <rect x="23.664" y="88.971" width="56.174" height="14.08" rx="4.504" ry="4.504" transform="translate(83.047 -8.472) rotate(45)" fill="#fcf2ed" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
          <rect x="32.91" y="99.891" width="18.23" height="11.69" rx="2.929" ry="2.929" transform="translate(87.076 1.253) rotate(45)" fill="#f2d1c2" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
          <rect x="111.271" y="22.494" width="13.911" height="14.08" transform="translate(55.512 -74.948) rotate(45)" fill="#f2d1c2" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
          <rect x="109.399" y="12.19" width="38.265" height="14.08" rx="3.082" ry="3.082" transform="translate(51.244 -85.253) rotate(45)" fill="#fcf2ed" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
        </g>
        <g>
          <line x1="21.703" y1="141.911" x2="38.417" y2="158.625" fill="#fcf2ed" stroke="#d38d6e" stroke-linecap="round" stroke-linejoin="round" stroke-width="4"/>
          <path d="M20.731,145.046L2.902,127.217c-1.194-1.194-1.203-3.122-.02-4.306h0c1.184-1.184,3.111-1.175,4.306.02l17.829,17.829c1.194,1.194,1.203,3.122.02,4.306h0c-1.184,1.184-3.111,1.175-4.306-.02Z" fill="#f2d1c2" stroke="#d38d6e" stroke-miterlimit="10" stroke-width="4"/>
        </g>
        <rect x="54.569" y="64.032" width="72.832" height="9.536" rx="2.448" ry="2.448" transform="translate(-22 84.487) rotate(-45)" fill="#f4e4dc"/>
        <rect x="132.888" y="22.728" width="8.241" height="9.536" rx="2.448" ry="2.448" transform="translate(20.686 104.933) rotate(-45)" fill="#f4e4dc"/>
        <rect x="62.039" y="105.709" width="8.386" height="9.536" rx="2.448" ry="2.448" transform="translate(-58.72 79.191) rotate(-45)" fill="#f4e4dc"/>
      </g>
    </g>
  </svg>
));

InstrumentHeader.displayName = "Instrument";
export default InstrumentHeader;
