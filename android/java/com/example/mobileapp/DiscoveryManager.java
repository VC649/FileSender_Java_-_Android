package com.example.mobileapp;

import java.net.*;

public class DiscoveryManager {
    public interface Callback {
        void onConnected(InetAddress pcIp);
        void onStatus(String msg);
    }

    private static final int DISCOVERY_PORT = 5001;
    private volatile boolean running = true;

    public void stop() {
        running = false;
    }

    void startDiscovery(Callback callback) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {

                socket.setSoTimeout(3000); // retry every 3s

                byte[] buffer = new byte[1024];

                callback.onStatus("Searching for device...");

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        String msg = new String(packet.getData(), 0, packet.getLength());
                        if (msg.equals("DISCOVER_SERVER")) {

                            InetAddress pcIp = packet.getAddress();

                            DatagramPacket reply = new DatagramPacket("SERVER_HERE".getBytes(), "SERVER_HERE".length(), pcIp, packet.getPort());
                            socket.send(reply);

                            callback.onConnected(pcIp);
                            break;
                        }
                    } catch (SocketTimeoutException ignored) {
                        callback.onStatus("Retrying...");
                    }
                }
            } catch (Exception e) {
                callback.onStatus("Discovery failed");
            }
    }).start();
    }
}