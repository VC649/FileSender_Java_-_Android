package com.example.mobileapp;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.*;
import java.net.*;

public class ReceiveFragment extends Fragment {

    TextView status, progressText;
    ProgressBar progressBar;

    private static final int DISCOVERY_PORT = 5001;

    private void update(String msg) {
        requireActivity().runOnUiThread(() -> status.setText(msg));
    }

    private void connectToSender() {
        try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
            byte[] buffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (true) {
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength());
                if (msg.equals("DISCOVER_SERVER")) {

                    update("device found...");

                    DatagramPacket replyPacket = new DatagramPacket("SERVER_HERE".getBytes(), "SERVER_HERE".length(), packet.getAddress(), packet.getPort());
                    socket.send(replyPacket);
                    update("connected to device");
                }
            }
        } catch (Exception e) {
            update("Discovery error");
        }
    }

    private void receiveFile() {
        try {
            ServerSocket serverSocket = new ServerSocket(5000);
            update("Waiting for connection...");

            while(true) { // <-- handle multiple files
                Socket socket = serverSocket.accept();
                update("Client connected!");

                DataInputStream dis = new DataInputStream(socket.getInputStream());

                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

                // How many items total
                int itemCount = dis.readInt();
                update("Receiving " + itemCount + " items...");

                for(int i=0; i<itemCount; i++) {

                    requireActivity().runOnUiThread(() -> {
                        progressText.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);
                    });

                    int itemType = dis.readInt();

                    // FILE
                    if(itemType == 0) {
                        String fileName = dis.readUTF();

                        long fileSize = dis.readLong();

                        File outFile = new File(downloads, fileName);
                        FileOutputStream fos = new FileOutputStream(outFile);

                        byte[] buffer = new byte[4096];

                        long remaining = fileSize;

                        while (remaining > 0) {
                            int bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            fos.write(buffer, 0, bytesRead);
                            remaining -= bytesRead;

                            long done = fileSize - remaining;

                            int p = (int) ((done * 100)/fileSize);
                            requireActivity().runOnUiThread(() -> {
                                progressBar.setProgress(p);
                                progressText.setText(p+"%");
                            });
                        }
                        fos.close();
                        update("Received file: " + fileName);
                    }

                    // FOLDERS
                    else if (itemType == 1) {
                        String folderName = dis.readUTF();
                        File folder = new File(downloads, folderName);
                        folder.mkdirs();

                        int fileCount = dis.readInt();

                        for(int f=0; f<fileCount; f++) {
                            String fileName = dis.readUTF();
                            long fileSize = dis.readLong();

                            File outFile = new File(folder, fileName);
                            FileOutputStream fos = new FileOutputStream(outFile);

                            byte[] buffer = new byte[4096];
                            long remaining = fileSize;

                            while(remaining > 0) {
                                int bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                                fos.write(buffer, 0, bytesRead);
                                remaining -= bytesRead;

                                long done = fileSize - remaining;

                                int p = (int) ((done * 100)/fileSize);

                                requireActivity().runOnUiThread(() -> {
                                    progressBar.setProgress(p);
                                    progressText.setText(p+"%");
                                });
                            }
                            fos.close();
                        }
                        update("Received folder: " + folderName);
                    }
                }
                update("All items received!");
                socket.close();
            }
        } catch(Exception e) {
            update("Error: " + e.getMessage());
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_receive, container, false);

        status = view.findViewById(R.id.test);
        progressText = view.findViewById(R.id.progressText);

        progressBar = view.findViewById(R.id.progressBar);

        new Thread(this::connectToSender).start();
        new Thread(this::receiveFile).start();
        return view;
    }
}