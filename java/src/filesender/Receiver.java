package filesender;

import java.io.*;
import java.net.*;
import javax.swing.*;

public class Receiver extends JFrame {
    
    JLabel showLabel;
    JProgressBar progress;
    
    private InetAddress findReceiverIp() {
        
        final int DISCOVERY_PORT = 5001;
        
        while (true) {
            try (DatagramSocket socket = new DatagramSocket()){
                socket.setBroadcast(true);

                byte[] sendData = "DISCOVER_SERVER".getBytes();

                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
                
                socket.send(packet);
                showLabel.setText("Searching for device");
                
                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);

                socket.setSoTimeout(3000);
                socket.receive(response);

                String msg = new String(response.getData(), 0, response.getLength());

                if(msg.equals("SERVER_HERE")) {
                    showLabel.setText("Connected - Waiting for files");
                    
                    return response.getAddress();
                }
            } catch(Exception e) {
                showLabel.setText("Retrying discovery");
            }
            
            try {Thread.sleep(1500);} catch(InterruptedException e) {}
        }
    }
    
    public void receiveFiles() {
        int port = 5000;
        
        findReceiverIp();
        
        String userHome = System.getProperty("user.home");
        
        try (ServerSocket server = new ServerSocket(port)) {
            
            while (true) {
                try (Socket socket = server.accept()) {
                    DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    
                    while(true) {
                        String command;
                        try {
                            command = dis.readUTF();
                        } catch(EOFException e) {
                            break; // client closed connection
                        }
                        
                        if("END".equals(command)) {
                            progress.setValue(100);
                            progress.setVisible(false);
                            showLabel.setText("Transfer complete");
                            
                            break;
                        }
                        switch (command) {
                            case "DIR" -> {
                                String path = dis.readUTF();
                                File dir = new File(userHome + File.separator + "Downloads" + File.separator + path);
                                dir.mkdirs();
                                showLabel.setText("Directory created: " + dir);
                            }
                            case "FILE" -> {
                                progress.setVisible(true);
                                progress.setValue(0);
                                
                                String path = dis.readUTF();
                                long size = dis.readLong();
                                File file = new File(userHome + File.separator + "Downloads" + File.separator + path);
                                file.getParentFile().mkdirs();
                                
                                try(FileOutputStream fos = new FileOutputStream(file)) {
                                    byte[] buffer = new byte[4096];
                                    
                                    long remaining = size;
                                    long received = 0;
                                    
                                    while (remaining > 0) {
                                        showLabel.setText("Receiving file: " + file.getName());
                                        int read = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                                        fos.write(buffer, 0, read);
                                        remaining -= read;
                                        received += read;
                                        
                                        int percent = (int) ((received * 100)/size);
                                        progress.setValue(percent);
                                    }
                                }
                                showLabel.setText("File saved: " + file);
                            }
                            
                            default -> {
                                // SINGLE FILE (no folder structure)
                                File file = new File(userHome + File.separator + "Downloads" + File.separator + command);
                                try (FileOutputStream fos = new FileOutputStream(file)) {
                                    byte[] buffer = new byte[4096];
                                    int read;
                                    
                                    while ((read = dis.read(buffer)) != -1) {
                                        showLabel.setText("Receiving file: " + file.getName());
                                        fos.write(buffer, 0, read);
                                    }
                                }
                                showLabel.setText("Single file saved: " + file);
                            }
                        }
                    }
                }
            }
        } catch(Exception e) {
        }
    }
    
    public Receiver() {
        setSize(500,300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);
        setLocationRelativeTo(null);
        setResizable(false);
        
        showLabel = new JLabel();
        showLabel.setBounds(100,100,350,20);
        
        progress = new JProgressBar();
        progress.setBounds(150,125,150,20);
        progress.setStringPainted(true);
        progress.setVisible(false);
        
        JButton backButton = new JButton("Back");
        backButton.setFocusPainted(false);
        backButton.setBounds(150,150,100,20);
        backButton.addActionListener(v -> {
            FileSender fileSender = new FileSender();
            fileSender.setVisible(true);
            dispose();
        });
        
        add(showLabel); add(progress);
        add(backButton);
    }
    
}