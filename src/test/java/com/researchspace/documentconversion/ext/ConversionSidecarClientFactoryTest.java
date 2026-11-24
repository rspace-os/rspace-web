package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class ConversionSidecarClientFactoryTest {

  @Test
  void createsClientsWhenTheSidecarIsUnreachableAtStartup() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    var settings =
        new ConversionSidecarClientFactory.Settings(500, 500, 1000, 1000, 1024, 1024, 1024);

    var clients = ConversionSidecarClientFactory.create("http://127.0.0.1:" + closedPort, settings);

    assertNotNull(clients.pdf());
    assertNotNull(clients.word());
  }
}
