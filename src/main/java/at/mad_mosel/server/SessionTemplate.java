package at.mad_mosel.server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Queue;


/**
 * Extend this class and provide its Constructor to the
 * server. The server will create a new Instance of this
 * class, everytime a client connects.
 * Note that this will create two threads, one for
 */
public abstract class SessionTemplate {
    private static int idCounter = 0;

    public final int id = idCounter++;
    private final String msgPrefix = "Session " + id + ": ";
    private Server server;

    Socket socket;
    Thread processor;
    Thread receiver;
    ObjectOutputStream dataOut;

    protected final Queue<Serializable> dataQueue = new LinkedList<>();


    Runnable processesData = () -> {
        printInfo("Processing thread starting...");
        try {
            dataOut = new ObjectOutputStream(socket.getOutputStream());
            userStartPoint();

            while (true) {
                Serializable data;
                synchronized (dataQueue) {
                    while (dataQueue.isEmpty()) dataQueue.wait();
                    data = dataQueue.remove();
                }
                this.processData(data);
            }
        } catch (InterruptedException breakLoop) {
        } catch (Exception e) {
            onError(e);
        }
        printInfo("Processor shutting down...");
    };

    Runnable receiveData = () -> {
        receive();
    };

    protected void init(Socket socket, Server server) throws IOException {
        this.server = server;
        this.socket = socket;
        userInit();
        printInfo("Launching processor thread...");
        processor = new Thread(processesData);
        processor.start();
        printInfo("Processor up.");
        receiver = new Thread(receiveData);
        receiver.start();
    }

    /**
     * Server calls this when a new connection is established.
     * At this point the Constructor has been called, but no
     * sender or receiver Threads are created. If you need to
     * do initial communication place the code in
     */
    public abstract void userInit();

    /**
     * This is called automatically after the OutputStream has been created.
     * If you need to do initial work but require sending ability place your
     * code in here.
     */
    public abstract void userStartPoint();

    private void receive() {
        printInfo("Receiver thread starting...");
        try {
            ObjectInputStream dataIn = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Serializable data = (Serializable) dataIn.readObject();
                Server.logger.printVerbose("Received: " + data.getClass());

                synchronized (dataQueue) {
                    dataQueue.add(data);
                    dataQueue.notify();
                }
            }
        } catch (SocketException | EOFException se) {
            printVerbose("Receiver - socket closed");
        } catch (Exception e) {
            onError(e);
        }
        printInfo("Receiver shutting down...");
    }


    public synchronized void send(Serializable data) {
        try {
            Server.logger.printVerbose("Sending " + data.getClass());
            dataOut.writeObject(data);
        } catch (IOException e) {
            if (Server.logger.exception) e.printStackTrace();
            printDebug("Failed on send. Trying kill()...");
            kill();
        }
    }


    /**
     * This will be called when data arrives.
     */
    public abstract void processData(Serializable object) throws Exception;

    private void printInfo(String msg) {
        Server.logger.printInfo(msgPrefix + msg);
    }

    private void printDebug(String msg) {
        Server.logger.printDebug(msgPrefix + msg);
    }

    private void printVerbose(String msg) {
        Server.logger.printVerbose(msgPrefix + msg);
    }


    private void onError(Exception e) {
        if (Server.logger.debug) e.printStackTrace();
        printDebug(e.getMessage());

        try {
            socket.close();
            processor.interrupt();
        } catch (Exception x) {
            x.printStackTrace();
            printDebug(x.getMessage());
            printDebug("This should really not happen! Killing app for safety!");
            printInfo("There was some issue with terminating the processor thread.");
            System.exit(-1);
        }
        kill();
        server.removeSession(this);
    }

    public void kill() {
        try {
            socket.close();
            printVerbose("Socket successfully closed");
            processor.interrupt();
            printInfo("Killed Session.");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        server.removeSession(this);
    }
}
