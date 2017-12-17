package de.diesner.ehzlogger.source;

import org.openmuc.jsml.structures.SML_File;
import org.openmuc.jsml.tl.SML_TConnection;
import org.openmuc.jsml.tl.SML_TSAP;

import java.io.IOException;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by hjuhls on 11.05.17.
 */
public class TcpSMLSource implements SMLSource {

    final static Pattern DEST_PATTERN = Pattern.compile("(?<HOST>[^:]+):(?<PORT>\\d+)");

    final static long millisBetweenRetries = 60000;

    SML_TSAP tsap;
    SML_TConnection conn = null;

    long lastConnTimestamp = 0;
    final String host;
    final Integer port;
    final Integer timeout;

    /**
     *
     * @param dest destination - e.g. hostname:port
     */
    public TcpSMLSource(String dest) {
        tsap = new SML_TSAP();
        Matcher destMatcher = DEST_PATTERN.matcher(dest);
        if (!destMatcher.matches()) {
            throw new IllegalArgumentException("Illegal dest: " + dest);
        }

        host = destMatcher.group("HOST");
        port = Integer.parseInt(destMatcher.group("PORT"));
        timeout = 1000;

    }

    @Override
    public void close() {
        conn.close();
    }

    @Override
    public SML_File read() {
        SML_File file = null;
        try {
            if (conn == null) {
                if (System.currentTimeMillis() > (lastConnTimestamp + millisBetweenRetries)) {
                    try {
                        lastConnTimestamp = System.currentTimeMillis();
                        conn = tsap.connectTo(InetAddress.getByName(host), port, timeout);
                    } catch (IOException e) {
                        System.err.println("Error connecting to meter: " + e.getLocalizedMessage());
                    }
                }
            } else {
                file = conn.receive();
            }
        } catch (IOException e) {
            conn.close();
            conn=null;
        }
        return file;
    }
}
