package at.mad_mosel.server;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;


/**
 * Extend this class and provide its Constructor to the
 * server. The server will create a new Instance of this
 * class, everytime a client connects.
 * Note that this will create two threads, one for
 */
public abstract class Session {
    private static int idCounter = 0;

    public final int id = idCounter++;
    private final String msgPrefix = "Session " + id + ": ";

    boolean shutdownSwitch = false;

    Socket socket;
    Thread processor;
    ObjectOutputStream dataOut;

    protected final Queue<Serializable> dataQueue = new LinkedList<>();


    Runnable processesData = () -> {
        printInfo("Processing thread starting...");
        try {
            dataOut = new ObjectOutputStream(socket.getOutputStream());
            while (!shutdownSwitch) {
                Serializable data;
                synchronized (dataQueue) {
                    while (dataQueue.isEmpty()) dataQueue.wait();
                    data = dataQueue.remove();
                }

                this.processData(data);
            }
        } catch (Exception e) {
            Server.logger.printException(e.getMessage());
            if (Server.logger.debug) e.printStackTrace();
            printInfo("Processor shutting down...");
        }
    };

    protected void init(Socket socket) throws IOException {
        printInfo("Launching processor thread...");
        this.socket = socket;
        processor = new Thread(processesData);
        processor.start();
        printInfo("Processor up.");
        userInit();
        receive();
    }

    /**
     * This method is meant to help customizing the fields
     * of InputStream dataIn and OutputStream dataOut. Do
     * something like:
     * * dataIn = new BufferedInputStream(dataIn);
     * * dataOut = new BufferedInputStream(dataOut);
     * The fields will be Initialized at the moment this
     * member is called. Do not call this from your Code.
     */
    public abstract void userInit();

    /**
     * This will run in an endless loop within a Thread.
     * Implement whatever Server shall do in on receive
     * here.
     */
    private void receive() {
        printInfo("Receiver thread starting...");
        try {
            ObjectInputStream dataIn = new ObjectInputStream(socket.getInputStream());

            while (!shutdownSwitch) {
                Serializable data = (Serializable) dataIn.readObject();

                synchronized (dataQueue) {
                    dataQueue.add(data);
                    dataQueue.notify();
                }
            }
        } catch (
                Exception e) {
            Server.logger.printException(e.getMessage());
            if (Server.logger.debug) e.printStackTrace();
            printInfo("Receiver shutting down...");
        }
    }


    public void send(Serializable data) {
        try {
            dataOut.writeObject(data);
        } catch (IOException e) {
            if (Server.logger.debug) Server.logger.printException(e.getMessage() + "while sending" + data);
            if (Server.logger.exception) e.printStackTrace();
        }
    }


    /**
     * This will run in an endless loop within a Thread.
     */
    public abstract void processData(Serializable object) throws Exception;

    private void printInfo(String msg) {
        Server.logger.printInfo(msgPrefix + msg);
    }


    //TODO good java-doc
    //TODO unify exit strategy (on error)
}
