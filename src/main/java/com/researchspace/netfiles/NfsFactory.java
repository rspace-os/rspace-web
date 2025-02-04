package com.researchspace.netfiles;

import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.netfiles.irods.IRODSClient;
import com.researchspace.netfiles.irods.JargonFacade;
import com.researchspace.netfiles.samba.JcifsClient;
import com.researchspace.netfiles.samba.JcifsSmbjClient;
import com.researchspace.netfiles.samba.SmbjClient;
import com.researchspace.netfiles.sftp.SftpClient;
import org.apache.commons.lang.StringUtils;
import org.irods.jargon.core.connection.AuthScheme;
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy;
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.IRODSSession;
import org.irods.jargon.core.connection.SettableJargonProperties;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Class providing various Net File Store implementations depending on deployment.properties */
@Component
public class NfsFactory {

  private static final int IRODS_DEFAULT_PORT = 1247;

  private static final String IRODS_DEFAULT_CSNEG = "CS_NEG_REFUSE";

  private static final String IRODS_DEFAULT_AUTH = "NATIVE";

  private static final Logger log = LoggerFactory.getLogger(NfsFactory.class);

  @Autowired private NfsUserPasswordAuthentication userPasswordAuthentication;

  @Autowired private NfsPublicKeyAuthentication publicKeyAuthentication;

  @Value("${netfilestores.auth.pubKey.passphrase}")
  private String passphrase;

  @Value("${netfilestores.smbj.download}")
  private Boolean smbjDownload;

  // this is for legacy systems only and doesn't need defining for new setups
  @Value("${netfilestores.smbj.shareName}")
  private String smbjShareName;

  @Value("${netfilestores.smbj.withDfsEnabled}")
  private Boolean smbjWithDfsEnabled = false;

  @Value("${netfilestores.extraSystemProps}")
  private String extraSystemProps;

  @Value("${netfilestores.smbj.name.match.path}")
  private String sambaNameMustMatchFilePath;

  private boolean extraSystemPropsConfigured;

  public NfsClient getNfsClient(String nfsusername, String nfspassword, NfsFileSystem fileSystem) {

    verifyFileSystemProperties(fileSystem);
    configureExtraSystemProperties();

    NfsClientType clientType = fileSystem.getClientType();
    if (NfsClientType.SAMBA.equals(clientType)) {
      if (smbjDownload != null && smbjDownload) {
        return new JcifsSmbjClient(
            nfsusername,
            nfspassword,
            fileSystem.getClientOption(NfsFileSystemOption.SAMBA_DOMAIN),
            fileSystem.getUrl(),
            smbjShareName);
      }
      return new JcifsClient(
          nfsusername,
          nfspassword,
          fileSystem.getClientOption(NfsFileSystemOption.SAMBA_DOMAIN),
          fileSystem.getUrl());
    }
    if (NfsClientType.SMBJ.equals(clientType)) {
      return new SmbjClient(
          nfsusername,
          nfspassword,
          fileSystem.getClientOption(NfsFileSystemOption.SAMBA_DOMAIN),
          fileSystem.getUrl(),
          fileSystem.getClientOption(NfsFileSystemOption.SAMBA_SHARE_NAME),
          smbjWithDfsEnabled,
          Boolean.parseBoolean(sambaNameMustMatchFilePath));
    }
    if (NfsClientType.SFTP.equals(clientType)) {
      return new SftpClient(
          nfsusername,
          nfspassword,
          fileSystem.getUrl(),
          fileSystem.getClientOption(NfsFileSystemOption.SFTP_SERVER_PUBLIC_KEY));
    }
    if (NfsClientType.IRODS.equals(clientType)) {

      int irodsPort;
      String irodsCSNeg;
      String irodsAuth;

      irodsPort =
          (StringUtils.isBlank(fileSystem.getClientOption(NfsFileSystemOption.IRODS_PORT)))
              ? IRODS_DEFAULT_PORT
              : Integer.parseInt(fileSystem.getClientOption(NfsFileSystemOption.IRODS_PORT));
      irodsCSNeg =
          (StringUtils.isBlank(fileSystem.getClientOption(NfsFileSystemOption.IRODS_CSNEG)))
              ? IRODS_DEFAULT_CSNEG
              : fileSystem.getClientOption(NfsFileSystemOption.IRODS_CSNEG);

      IRODSAccount ia =
          new IRODSAccount(
              fileSystem.getUrl(),
              irodsPort,
              nfsusername,
              nfspassword,
              fileSystem.getClientOption(NfsFileSystemOption.IRODS_HOME_DIR),
              fileSystem.getClientOption(NfsFileSystemOption.IRODS_ZONE),
              "");
      JargonFacade jf = new JargonFacade();

      // set up iRODS CS NEG
      // here you could set all jargon/irods props
      // see
      // jargon-core/src/main/java/org/irods/jargon/core/connection/SettableJargonProperties.java
      IRODSFileSystem iRODSFs = jf.iRODSFs;
      IRODSSession session = iRODSFs.getIrodsSession();
      SettableJargonProperties props = new SettableJargonProperties(session.getJargonProperties());
      SslNegotiationPolicy sslNegPolicy =
          ClientServerNegotiationPolicy.findSslNegotiationPolicyFromString(irodsCSNeg);
      props.setNegotiationPolicy(sslNegPolicy);
      session.setJargonProperties(props);

      // set iRODS auth scheme
      irodsAuth =
          (StringUtils.isBlank(fileSystem.getClientOption(NfsFileSystemOption.IRODS_AUTH)))
              ? IRODS_DEFAULT_AUTH
              : fileSystem.getClientOption(NfsFileSystemOption.IRODS_AUTH);

      AuthScheme ias = (irodsAuth.equals("NATIVE")) ? AuthScheme.STANDARD : AuthScheme.PAM;
      ia.setAuthenticationScheme(ias);

      return new IRODSClient(ia, jf);
    }

    return null;
  }

  private void configureExtraSystemProperties() {
    if (!extraSystemPropsConfigured) {
      extraSystemPropsConfigured = true;
      if (!StringUtils.isEmpty(extraSystemProps)) {
        String[] props = StringUtils.split(extraSystemProps, ",");
        for (String prop : props) {
          String[] propNameValue = StringUtils.split(prop, "=");
          log.info("Setting system property '{}' to '{}'", propNameValue[0], propNameValue[1]);
          System.setProperty(propNameValue[0], propNameValue[1]);
        }
      }
    }
  }

  public NfsClient getNfsClient(UserKeyPair userKeyPair, NfsFileSystem fileSystem) {

    verifyFileSystemProperties(fileSystem);

    NfsClientType clientType = fileSystem.getClientType();
    if (NfsClientType.SAMBA.equals(clientType) || NfsClientType.SMBJ.equals(clientType)) {
      throw new UnsupportedOperationException(
          "samba connection not supported with pubkey authentication");
    }
    if (NfsClientType.SFTP.equals(clientType)) {
      return new SftpClient(
          userKeyPair,
          passphrase,
          fileSystem.getUrl(),
          fileSystem.getClientOption(NfsFileSystemOption.SFTP_SERVER_PUBLIC_KEY));
    }
    return null;
  }

  public NfsAuthentication getNfsAuthentication(NfsFileSystem fileSystem) {
    verifyFileSystemProperties(fileSystem);
    return getNfsAuthentication(fileSystem.getAuthType());
  }

  public NfsAuthentication getNfsAuthentication(NfsAuthenticationType authType) {
    if (NfsAuthenticationType.PUBKEY.equals(authType)) {
      return publicKeyAuthentication;
    }
    if (NfsAuthenticationType.PASSWORD.equals(authType)) {
      return userPasswordAuthentication;
    }
    return null;
  }

  private void verifyFileSystemProperties(NfsFileSystem fileSystem) {

    if (StringUtils.isEmpty(fileSystem.getUrl())) {
      throw new IllegalStateException("nfsFileSystem url is empty");
    }
    if (fileSystem.getClientType() == null) {
      throw new IllegalStateException("nfsFileSystem client type is empty");
    }
    if (fileSystem.getAuthType() == null) {
      throw new IllegalStateException("nfsFileSystem authentication type is empty");
    }
  }
}
