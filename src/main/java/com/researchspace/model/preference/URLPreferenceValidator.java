package com.researchspace.model.preference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class URLPreferenceValidator {
    
    protected Logger log = LoggerFactory.getLogger(getClass());
    
    /** error message if can't connect to provided url  */
    protected static final String URL_MALFORMED_MSG = "apps.error.url.malformed.msg";
    protected static final String URL_UNREACHABLE_MSG = "apps.error.url.unreachable.msg";
    protected static final String EXPECTED_FRAGMENT_NOT_FOUND_MSG = "apps.error.expected.fragment.not.found";

    /**
     * Check if the provided urlString is a valid and reachable URL, 
     * and (optionally) that the page behind the URL contains expectedPageFragment.
     * 
     * @param urlString to check, provided by the user
     * @param expectedPageFragment (optional) if provided, 
     * @return error message key, or null if there is no error  
     */
     public String connectAndReadUrl(String urlString, String expectedPageFragment) {
        if (StringUtils.isEmpty(urlString)) {
            return null; // allow setting empty/null URL
        }

        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            log.warn("coudn't parse provided URL: " + urlString);
            return URL_MALFORMED_MSG;
        }
        
        try (InputStream input = url.openStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String line = reader.readLine();

            if (StringUtils.isEmpty(expectedPageFragment)) {
                return null; // url valid and reacheable at this point  
            }
            while (line != null) {
                if (line.contains(expectedPageFragment)) {
                    return null; // found the fragment
                }
                line = reader.readLine();
            }
        } catch (IOException ioe) {
            log.warn("coudn't reach provided URL: " + urlString + ", got " + ioe.getMessage());
            log.debug("exception details: ", ioe);
            return URL_UNREACHABLE_MSG;
        }

        log.info("expected fragment: " + expectedPageFragment + " not found at: " + url);
        return EXPECTED_FRAGMENT_NOT_FOUND_MSG;
    }
}