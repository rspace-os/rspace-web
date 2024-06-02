This folder contains actual JSON returned by the clustermarket API. These files can be used to investigate any clustermarket API changes.

There is no Java API in RSpace for Clustermarket, its modelled as JsonNode for flexibility. There will therefore not be any exceptions logged
in the backend if the Clustermarket API changes. Javascript failures in the front end might be logged to the browser console but will frequently
fail silently.

The data comes from our prod account on Clustermarket, the credentials are in Bitwarden