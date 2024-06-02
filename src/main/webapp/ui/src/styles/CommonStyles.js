import styled from "@emotion/styled";

export const CardWrapper = styled.div`
  .MuiCard-root {
    margin: 0px 0px 20px 0px;
    .title {
      font-size: 18px;
      font-weigh: bold;
      margin: 10px 0px;
      padding-left: 15px;
    }
  }

  .MuiCard-root {
    .MuiCardHeader-root {
      padding: 10px 15px;
    }
    .MuiCardContent-root {
      padding: 0px 15px 5px 15px;
    }
    .MuiCardActions-root {
      justify-content: space-between;

      button {
        width: auto;

        svg {
          margin-right: 5px;
        }
      }
      .group-right button {
        margin-left: 10px;
      }
    }
  }

  textarea:focus,
  textarea:hover {
    background-color: transparent !important;
  }
`;

export const DialogWrapper = styled.div`
  [class^="MuiPaper-root"] {
    [class^="MuiDialogActions] {

    }
  }
`;
