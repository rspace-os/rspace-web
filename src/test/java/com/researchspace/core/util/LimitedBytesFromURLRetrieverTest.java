package com.researchspace.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class LimitedBytesFromURLRetrieverTest {
    public static final String VALID_URL = "https://www.google.com";
    public static final String INVALID_URL = "https://www.g12345oogle.com";
    Supplier<byte[]> defaultSupplier = ()->new byte[]{-22};

    static class LimitedBytesFromURLRetrieverTSS extends LimitedBytesFromURLRetriever {
        static byte [] source;
        public LimitedBytesFromURLRetrieverTSS(int timeoutMillis, long maxByteSize) {
            super(timeoutMillis, maxByteSize);
        }

        InputStream getInputStream(URLConnection urlConnection) {
            return new ByteArrayInputStream(source);
        }
    }


    @Test
    void assertConstructorArgs(){
        assertThrows(IllegalArgumentException.class, ()->new LimitedBytesFromURLRetriever(0,0));
        assertThrows(IllegalArgumentException.class, ()->new LimitedBytesFromURLRetriever(-1,1));
        assertThrows(IllegalArgumentException.class, ()->new LimitedBytesFromURLRetriever(1,-1));
    }

    @Test
    void limitExceededReturnsDefault(){
        LimitedBytesFromURLRetrieverTSS urlRetriever = new LimitedBytesFromURLRetrieverTSS(100, 10);
        urlRetriever.source = new byte []{1,2,3,4,5,6,7,8,9,10,11}; // 1 too big
        byte[] bytes = urlRetriever.retrieveUrlBytesQuietly(VALID_URL, defaultSupplier);
        assertArrayEquals(defaultSupplier.get(), bytes);
    }

    @Test
    void limitNotExceededReturnsBytes(){
        LimitedBytesFromURLRetrieverTSS urlRetriever = new LimitedBytesFromURLRetrieverTSS(100, 10);
        urlRetriever.source = new byte []{1,2,3,4,5,6,7,8,9,10}; // 10 is max allowed length
        byte[] bytes = urlRetriever.retrieveUrlBytesQuietly(VALID_URL, defaultSupplier);
        assertArrayEquals(urlRetriever.source, bytes);
    }

    @Test
    @Timeout(1)
    void timeoutTriggeredForInvalidURL(){
        int timeoutMillis = 100;
        LimitedBytesFromURLRetriever urlRetriever = new LimitedBytesFromURLRetriever(timeoutMillis, 10);
        byte[] bytes = urlRetriever.retrieveUrlBytesQuietly(INVALID_URL, defaultSupplier);
        assertArrayEquals(defaultSupplier.get(), bytes);
    }

}