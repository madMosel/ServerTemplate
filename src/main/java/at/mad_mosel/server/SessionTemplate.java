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
     */
    public abstract void userInit();

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
        } catch (Exception e) {
            onError(e);
        }
        printInfo("Receiver shutting down...");
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
     * This will be called when data arrives.
     */
    public abstract void processData(Serializable object) throws Exception;

    private void printInfo(String msg) {
        Server.logger.printInfo(msgPrefix + msg);
    }

    private void printException(String msg) {
        Server.logger.printException(msgPrefix + msg);
    }

    private void printVerbose(String msg) {
        Server.logger.printVerbose(msgPrefix + msg);
    }


    private void onError(Exception e) {
        if (Server.logger.debug) e.printStackTrace();
        printException(e.getMessage());

        try {
            processor.interrupt();
            receiver.interrupt();
        } catch (Exception x) {
            x.printStackTrace();
            printException(x.getMessage());
            printException("This should really not happen! Killing app for safety!");
            printInfo("There was some issue with terminating the processor thread.");
            System.exit(-1);
        }
        printInfo("Killed Session.");
        server.removeSession(this);
    }

    void kill() {
        try {
            socket.close();
            printVerbose("Socket successfully closed");
            processor.interrupt();
        } catch (IOException e) {
            System.out.println("Fuck this");
        }
    }
}
