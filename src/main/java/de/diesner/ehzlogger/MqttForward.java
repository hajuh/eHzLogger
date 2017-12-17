package de.diesner.ehzlogger;

import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.LoggingFilter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.openmuc.jsml.structures.*;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class MqttForward extends TimerTask implements SmlForwarder {

    private final SmartMeterRegisterList smartMeterRegisterList;
    private final List<DataToPost> postDataList = new ArrayList<>();

    private final Timer timer;

    @Getter
    @AllArgsConstructor
    private static class DataToPost {
        private String postData;
        private int retriesLeft;

        public void decRetriesLeft() {
            retriesLeft--;
        }
    }

    private final String clientId;
    private final String remoteUri;
    private final String topic;
    private final MqttClient client;
    private final Gson gson = new Gson();

    public MqttForward(String remoteUri,  String clientId, String topic,SmartMeterRegisterList smartMeterRegisterList) throws MqttException {
        this.smartMeterRegisterList = smartMeterRegisterList;
        this.clientId = clientId;
        this.topic = topic;
        this.remoteUri = remoteUri;

        client = new MqttClient(remoteUri, clientId);

        timer = new Timer();
        timer.schedule(this, 1000, 1000);
    }

    @Override
    public void messageReceived(List<SML_Message> messageList) {
        Map<String, String> values = extractValues(messageList);
        addPostItem(getDataToPost(values));
    }

    private DataToPost getDataToPost(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return new DataToPost("", 0);
        }
        String postData = toLineProtocol(values);
        int MAXRETRYCOUNT = 3;
        return new DataToPost(postData, MAXRETRYCOUNT);
    }

    private Map<String, String> extractValues(List<SML_Message> messageList) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < messageList.size(); i++) {
            SML_Message sml_message = messageList.get(i);
            int tag = sml_message.getMessageBody().getTag().getVal();
            if (tag == SML_MessageBody.GetListResponse) {
                SML_GetListRes resp = (SML_GetListRes) sml_message.getMessageBody().getChoice();
                SML_List smlList = resp.getValList();

                SML_ListEntry[] list = smlList.getValListEntry();

                for (SML_ListEntry entry : list) {
                    int unit = entry.getUnit().getVal();

                    if (unit == SML_Unit.WATT || unit == SML_Unit.WATT_HOUR) {
                        SML_Value value = entry.getValue();
                        Long numericalValue = SmlDecoder.decodeASN(value.getChoice());
                        if (numericalValue == null) {
                            System.out.println("Got non-numerical value for an energy measurement. Skipping.");
                            continue;
                        }

                        byte objNameBytes[] = entry.getObjName().getOctetString();
                        for (SmartMeterRegister register : smartMeterRegisterList.getRegisterList()) {
                            if (register.matches(objNameBytes)) {
                                values.put(register.getLabel(), String.valueOf(numericalValue / 10.0));
                                break;
                            }
                        }
                    }
                }
            }
        }
        return values;
    }

    private String toLineProtocol( Map<String, String> values) {

       return gson.toJson(values);
    }

    private boolean postData(DataToPost dataToPost) throws MqttException, UnsupportedEncodingException {

        if (!client.isConnected()) {
            // TODO: Exponential backoff
            client.connect();
        }

        if (dataToPost == null || dataToPost.getRetriesLeft() == 0) {
            return true;
        }

        if (client.isConnected()) {
            client.publish(topic, dataToPost.postData.getBytes("UTF-8"), 0, false);
        } else {
            return false;
        }

        return true;
    }

    private DataToPost getPostItem() {
        synchronized (postDataList) {
            if (!postDataList.isEmpty()) {
                return postDataList.remove(0);
            }
        }
        return null;
    }

    private void addPostItem(DataToPost dataToPost) {
        if ((dataToPost != null) && (dataToPost.getRetriesLeft() > 0)) {
            synchronized (postDataList) {
                postDataList.add(dataToPost);
            }
        }
    }

    @Override
    public void run() {
        List<DataToPost> failedRequests = new ArrayList<>();
        DataToPost dataToPost;
        do {
            dataToPost = getPostItem();
            if (dataToPost != null && dataToPost.getRetriesLeft() > 0) {
                boolean success = false;
                try {
                    success = postData(dataToPost);
                } catch (Exception e) {
                    System.out.println("Exception while posting: " + e.getMessage());
                    e.printStackTrace();
                }
                if ((!success) && (dataToPost.getRetriesLeft() > 0)) {
                    failedRequests.add(dataToPost);
                }
            }
        } while (dataToPost != null);

        for (DataToPost retry: failedRequests) {
            retry.decRetriesLeft();
            addPostItem(retry);
        }
    }
}
