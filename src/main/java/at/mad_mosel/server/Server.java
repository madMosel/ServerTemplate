package at.mad_mosel.server;

import at.mad_mosel.ConfigParser;
import at.mad_mosel.Configuration;
import at.mad_mosel.Logger.Logger;

import javax.net.ServerSocketFactory;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.*;

/**
 * Provides an WelcomeSocket running in own thread.
 * On Connection a new Session is spawned. You must
 * implement abstract class Session and provide the
 * constructor.
 * The Server can be configured using a config file.
 * There are defaults predefined. It is recommended
 * to start the server - the configs will get printed
 * to file. You can then edit the file.
 * Configs:
 * port;int;{\\d*}
 * tls;false;{true,false}
 * certPath;path;{[^;]*}
 * printInfo;true;{true,false}
 * printVerbose;false;{true,false}
 * printDebug;false;{true,false}
 * printException;true;{true,false}
 */
public class Server {
    protected static Logger logger = new Logger();

    //default configuration
    private boolean printDebug = false;
    private boolean printVerbose = false;
    private boolean printInfo = true;
    private boolean printException = true;
    private int port = 8001;
    private boolean tls = false;
    private String p12file = "";
    private String password = "";


    //control
    private boolean welcomeActive = false;
    private final List<SessionTemplate> sessions = new ArrayList<>();

    //action
    ServerSocketFactory ssf = ServerSocketFactory.getDefault();
    Constructor sessionConstructor;

    /**
     * Implement abstract Session class and provide
     * its Constructor.
     * Information on Config see java-doc of Class
     */
    public Server(Constructor sessionConstructor) {
        this.sessionConstructor = sessionConstructor;
        parseConfigAndInsertMissing();
        logger.debug = printDebug;
        logger.verbose = printVerbose;
        logger.info = printInfo;
        logger.exception = printException;
    }

    public void start() {
        welcomeSocket.start();
    }


    Thread welcomeSocket = new Thread(() -> {
        this.welcomeActive = true;
        try {
            try (ServerSocket welcomeSocket = ssf.createServerSocket(port)) {
                logger.printInfo("Server: running on port " + port);

                while (true) {
                    logger.printInfo("Server: waiting on connection... ");
                    Socket connection = welcomeSocket.accept();
                    logger.printInfo("Server: connection requested");
                    SessionTemplate session = (SessionTemplate) sessionConstructor.newInstance();
                    session.init(connection, this);
                    sessions.add(session);
                    logger.printInfo("Server: launched session " + session.id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    });


    public void shutdownWelcomeSocket() {
        welcomeSocket.stop();
        welcomeActive = false;
    }

    protected void removeSession(SessionTemplate session) {
        synchronized (this.sessions) {
            sessions.remove(session);
        }
    }

    public LinkedList<Integer> getActiveSessionIds() {
        LinkedList<Integer> sessionIds = new LinkedList<>();
        for (SessionTemplate s : this.sessions) {
            sessionIds.add(s.id);
        }
        return sessionIds;
    }

    /**
     * Tries to kill the session of specified sessionId. If session
     * exists ond killing is not successful after 3s the server will
     * exit -1.
     * @return true if session with sessionId EXISTS, false if not
     */
    public boolean killSession(int sessionId) {
        SessionTemplate session = null;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                throw new IllegalStateException("Failed to cancel Session " + sessionId);
            }
        }, 3000);

        synchronized (sessions) {
            for (SessionTemplate s : this.sessions) {
                if (s.id == sessionId) session = s;
                break;
            }
        }
        if (session == null) return false;

        session.kill();
        timer.cancel();

        synchronized (sessions) {
            sessions.remove(session);
        }
        return true;
    }

    private void parseConfigAndInsertMissing() {
        ConfigParser configParser = new ConfigParser("server.conf");
        configParser.readFile();

        Configuration pd = configParser.getConfiguration("printDebug");
        if (pd != null && pd.getValue().equals("true")) printDebug = true;
        else if (pd != null && pd.getValue().equals("false")) printDebug = false;
        else configParser.addConfiguration("printDebug", Boolean.toString(printDebug), "true", "false");

        Configuration pv = configParser.getConfiguration("printVerbose");
        if (pd != null && pd.getValue().equals("true")) printVerbose = true;
        else if (pd != null && pd.getValue().equals("false")) printVerbose = false;
        else configParser.addConfiguration("printVerbose", Boolean.toString(printVerbose), "true", "false");

        Configuration pi = configParser.getConfiguration("printInfo");
        if (pi != null && pi.getValue().equals("true")) printInfo = true;
        else if (pi != null && pi.getValue().equals("false")) printInfo = false;
        else {
            configParser.addConfiguration("printInfo", Boolean.toString(printInfo), "true", "false");
        }

        Configuration pe = configParser.getConfiguration("printException");
        if (pe != null && pe.getValue().equals("true")) printException = true;
        else if (pe != null && pe.getValue().equals("false")) printException = false;
        else {
            configParser.addConfiguration("printException", Boolean.toString(printException), "true", "false");
        }

        Configuration port = configParser.getConfiguration("port");
        if (port != null) this.port = Integer.parseInt(port.getValue());
        else configParser.addConfiguration("port", Integer.toString(this.port), "\\d*");

        Configuration tlsConfig = configParser.getConfiguration("tls");
        if (tlsConfig != null && tlsConfig.getValue().equals("true")) tls = true;
        else if (tlsConfig != null && tlsConfig.getValue().equals("false")) tls = false;
        else configParser.addConfiguration("tls", Boolean.toString(tls), "true", "false");

        if (tls) {
            try {
                Configuration p12file = configParser.getConfiguration("p12file");
                if (p12file == null)
                    throw new IllegalStateException("TSL set true but p12file not specified in server.conf!");
                else if (p12file.getValue() != null && !p12file.getValue().equals(""))
                    this.p12file = p12file.getValue();
                else throw new IllegalStateException("p12file path is missing in server.conf!");
                Configuration passwd = configParser.getConfiguration("password");
                if (passwd == null)
                    throw new IllegalStateException("TSL but no password specified! Check config file!");
                else if (passwd.getValue() != null && !passwd.getValue().equals("")) this.password = passwd.getValue();
                else throw new IllegalStateException("passwd path is missing in server.conf!");
                this.ssf = TLS13ServerSocketFactory.getTLS13ServerSocketFactory(this.p12file, this.password);
            } catch (IllegalStateException ise) {
                if (!configParser.containsKeys("p12file")) configParser.addConfiguration("p12file");
                if (!configParser.containsKeys("passwd")) configParser.addConfiguration("passwd");
                configParser.saveConfigs();
                ise.printStackTrace();
                System.exit(-1);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        if (!configParser.containsKeys("certPath")) configParser.addConfiguration("certPath");
        if (!configParser.containsKeys("password")) configParser.addConfiguration("password");
        configParser.saveConfigs();
    }
}
