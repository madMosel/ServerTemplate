package at.mad_mosel.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;


/**
 * Extend this class and provide its Constructor to the
 * server. The server will create a new Instance of this
 * class, everytime a client connects.
 * Note that this will create two threads, one for
 */
public abstract class Session {
    private static int idCounter = 0;

    public final int id = idCounter++;
    private final String msgPrefix = "SessionFactory " + id + ": ";

    boolean shutdownSwitch = false;

    Thread dataProcessor;
    Thread send;

    protected InputStream dataIn;
    protected OutputStream dataOut;

    Runnable readsData = () -> {
        printInfo("Processing thread starting...");
        while (shutdownSwitch) {
            receive();
        }
    };
    Runnable sendsData = () -> {
        printInfo("Sending thread starting...");
        while (shutdownSwitch) {
            sendData();
        }
    };

    protected void init(Socket socket) throws IOException {
        dataIn = socket.getInputStream();
        dataOut = socket.getOutputStream();

        printInfo("Launching communication threads...");
        dataProcessor = new Thread(readsData);
        send = new Thread(sendsData);
        dataProcessor.start();
        send.start();
        printInfo("Communication threads up.");
        userInit();
    }

    /**
     * This method is meant to help customizing the fields
     * of InputStream dataIn and OutputStream dataOut. Do
     * something like:
     *   * dataIn = new BufferedInputStream(dataIn);
     *   * dataOut = new BufferedInputStream(dataOut);
     * The fields will be Initialized at the moment this
     * member is called. Better not call it yourself.
     */
    public abstract void userInit();

    /**
     * Runs in an endless loop within its own
     * Thread.
     * Use InputStream dataIn field
     */
    public abstract void receive();

    /**
     * Runs in an endless loop within its own
     * Thread.
     * Use OutputStream dataOut field
     */
    public abstract void sendData();

    private void printInfo(String msg) {
        Server.logger.printInfo(msgPrefix + msg);
    }


}
