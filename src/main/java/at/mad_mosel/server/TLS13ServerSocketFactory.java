package at.mad_mosel.server;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

class TLS13ServerSocketFactory {
    private static ServerSocketFactory ssf = null;

    protected static ServerSocketFactory getTLS13ServerSocketFactory(String pathServerCert, String password) throws Exception {
        if (ssf != null) return ssf;
        Security.addProvider(new BouncyCastleProvider());       //BouncyCastle Security Provider v1.71
        Security.addProvider(new BouncyCastleJsseProvider());   //Bouncy Castle JSSE Provider Version 1.0.13
//        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", "BC");


        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        serverKeyStore.load(new FileInputStream(pathServerCert), password.toCharArray());
//        PrivateKey serverKey = (PrivateKey) serverKeyStore.getKey("server_key", "password".toCharArray());
//        X509Certificate serverCertificate = (X509Certificate) serverKeyStore.getCertificate("server_key");

        SecureRandom serverSecureRandom = new SecureRandom(); //TODO: Bouncy castle secure random

        KeyManagerFactory serverKeyManagerFactory = KeyManagerFactory.getInstance("PKIX", "BCJSSE");
        serverKeyManagerFactory.init(serverKeyStore, "password".toCharArray());
        KeyManager[] serverKeyManagers = serverKeyManagerFactory.getKeyManagers();

        TrustManagerFactory serverTrustManagerFactory = TrustManagerFactory.getInstance("PKIX", "BCJSSE");
        serverTrustManagerFactory.init(serverKeyStore);
        TrustManager[] serverTrustManagers = serverTrustManagerFactory.getTrustManagers();

        SSLContext serverSSLContext = SSLContext.getInstance("TLSv1.3", "BCJSSE");
        serverSSLContext.init(serverKeyManagers, serverTrustManagers, serverSecureRandom);
        ssf = serverSSLContext.getServerSocketFactory();
        return ssf;
    }
}
