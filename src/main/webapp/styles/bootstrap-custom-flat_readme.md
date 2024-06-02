bootstrap-custom-flat.css is mainly created by the method described in src/main/webapp/scripts/bootstrap-namespace/Readme.md

It additionally was modified to account that namespace .bootstrap-custom-flat may not be attached to body, that is why to all css rules of type .bootstrap-custom-flat body one more rule was added -> .bootstrap-custom-flat. Thus, while using it the root element must be prefixed with workspace-new class otherwise changes won't happen. It is done in order to avoid css conflicts.  

The second change is updating the first @import ('Roboto' font) which now points to stylesheet in /styles/google-roboto-font rather than to remote css at fonts.googleapis.com (RSPAC-1720).