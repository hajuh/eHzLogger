package de.diesner.ehzlogger;

import de.diesner.ehzlogger.source.SMLSource;
import de.diesner.ehzlogger.source.SMLSourceException;
import de.diesner.ehzlogger.source.SerialSMLSource;
import de.diesner.ehzlogger.source.TcpSMLSource;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.openmuc.jsml.structures.SML_File;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class EhzLogger {

    private String connString;
    private SmartMeterRegisterList smartMeterRegisterList;
    private List<SmlForwarder> forwarderList;
    private Properties properties;
    private SMLSource source;

    public static void main(String[] args) {
        EhzLogger ehzLogger;
        boolean keepRunning = true;

        while (keepRunning) {
            ehzLogger = new EhzLogger();
            try {
                keepRunning = ehzLogger.initialize(args);
                ehzLogger.receiveMessageLoop();
            } catch (PortInUseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (UnsupportedCommOperationException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (MqttException e) {
                e.printStackTrace();
            } catch (SMLSourceException e) {
                e.printStackTrace();
            }
            ehzLogger.shutdown();
        }
    }

    /**
     * returns true on success
     * @param args command line arguments
     * @return
     * @throws PortInUseException
     * @throws IOException
     * @throws UnsupportedCommOperationException
     */
    private boolean initialize(String[] args) throws PortInUseException, IOException, UnsupportedCommOperationException, URISyntaxException, MqttException, SMLSourceException {
        properties = new Properties();

        InputStream is;
        if (args.length == 1) {
            System.out.println("Loading properties file: "+args[0]);
            is = new FileInputStream(args[0]);
        } else {
            is = getClass().getResourceAsStream("/application.properties");
        }
        try {
            properties.load(is);
        } finally {
            is.close();
        }

        connString = properties.getProperty("sml.src", "serial:///dev/ttyUSB0");
        smartMeterRegisterList = new SmartMeterRegisterList(properties);

        URI uri = new URI(connString);
        String scheme = uri.getScheme();
        if (scheme.equalsIgnoreCase("tcp")) {
            source = new TcpSMLSource(uri.getSchemeSpecificPart());
        } else if (scheme.equalsIgnoreCase("serial")) {
            source = new SerialSMLSource(uri.getSchemeSpecificPart());
        } else {
            throw new RuntimeException("Unknown scheme: " + scheme );
        }

        forwarderList = new ArrayList<>();
        if (Boolean.parseBoolean(properties.getProperty("output.cmdline.enabled"))) {
            forwarderList.add(new CmdLinePrint(smartMeterRegisterList));
        }
        if (Boolean.parseBoolean(properties.getProperty("output.influxdb.enabled"))) {
            forwarderList.add(new InfluxDbForward(properties.getProperty("output.influxdb.remoteUri"), properties.getProperty("output.influxdb.measurement"), smartMeterRegisterList));
        }
        if (Boolean.parseBoolean(properties.getProperty("output.mqtt.enabled"))) {
            forwarderList.add(new MqttForward(properties.getProperty("output.mqtt.remoteUri"), properties.getProperty("output.mqtt.clientId"), properties.getProperty("output.mqtt.topic"), smartMeterRegisterList));
        }

        if (forwarderList.isEmpty()) {
            return false;
        }

        return true;
    }

    private void receiveMessageLoop() throws IOException {

        while (true) {
            try {
                SML_File smlFile = source.read();
                if (smlFile!=null) {
                    for (SmlForwarder forwarder : forwarderList) {
                        forwarder.messageReceived(smlFile.getMessages());
                    }
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (SMLSourceException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    private void shutdown() {
        try {
            source.close();
        } catch (SMLSourceException e) {
            System.err.println("Error while trying to close serial port: " + e.getMessage());
        }
    }

}
