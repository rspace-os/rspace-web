package com.researchspace.documentconversion.ext;

import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.service.LicenseService;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;

public class CustomerIDSupplier implements Supplier<String> {

  private @Autowired LicenseService licenseService;

  @Override
  public String get() {
    String customerId;
    try {
      customerId =
          licenseService
              .getCustomerName()
              .map(s -> s.replaceAll("[^A-Za-z0-9_]", ""))
              .orElse("unknown-customer");
    } catch (LicenseServerUnavailableException lsue) {
      customerId = "unknown-customer";
    }
    return customerId;
  }
}
