package de.diesner.ehzlogger.source;

import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import org.openmuc.jsml.structures.SML_File;
import org.openmuc.jsml.tl.SML_SerialReceiver;

import java.io.IOException;

/**
 * Created by hjuhls on 11.05.17.
 */
public class SerialSMLSource implements SMLSource {

    private SML_SerialReceiver receiver;
    private final String serialPort;
    boolean initialized = false;

    public SerialSMLSource(String serialPort) {
        this.serialPort = serialPort;
        receiver = new SML_SerialReceiver();
        System.setProperty("gnu.io.rxtx.SerialPorts", serialPort);
    }



    public void open() throws SMLSourceException {
        try {
            receiver.setupComPort(serialPort);
        } catch (PortInUseException e) {
            throw new SMLSourceException(e);
        } catch (UnsupportedCommOperationException e) {
            throw new SMLSourceException(e);
        } catch (IOException e) {
            throw new SMLSourceException(e);
        }
    }

    @Override
    public void close() {
        try {
            receiver.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SML_File read() {
        if (!initialized) {
            try {
                open();
                initialized = true;
            } catch (SMLSourceException e) {
                e.printStackTrace();
            }
        }
        SML_File file = null;
        try {
            file = receiver.getSMLFile();
        } catch (IOException | NullPointerException e) {
            try {
                receiver.close();
            } catch (IOException ex) {
                // ignore closing exception
            }
            System.err.println("IOException during read - will wait for 60sec and try again");
            try {
                Thread.sleep(60000);
            } catch (InterruptedException ex) {
                // ignore
            }
            try {
                receiver = new SML_SerialReceiver();
                open();
            } catch (SMLSourceException ex) {
                ex.printStackTrace();
                System.err.println("will try again");

            }

        }
        return file;
    }
}
