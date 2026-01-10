package filesender;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class Sender extends JFrame {
    
    JFileChooser chooser;
    JProgressBar progress;
    JLabel updateLabel;
    
    InetAddress receiverIp;
    
    public Sender() {
        updateLabel = new JLabel();
        updateLabel.setBounds(130,85,150,20);
        
        receiverIp = findReceiverIp();
        
        progressWindow();
    }
    
    private InetAddress findReceiverIp() {
        
        final int DISCOVERY_PORT = 5001;
        
        while (true) {
            try (DatagramSocket socket = new DatagramSocket()){
                socket.setBroadcast(true);

                byte[] sendData = "DISCOVER_SERVER".getBytes();

                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
                
                socket.send(packet);
                updateLabel.setText("Searching for device");
                
                byte[] buffer = new byte[1024];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);

                socket.setSoTimeout(3000);
                socket.receive(response);

                String msg = new String(response.getData(), 0, response.getLength());

                if(msg.equals("SERVER_HERE")) {
                    updateLabel.setText("Connected");
                    return response.getAddress();
                }
            } catch(Exception e) {
                updateLabel.setText("Retrying discovery...");
            }
            
            try {Thread.sleep(1500);} catch(InterruptedException e) {}
        }
    }
    
    private void progressWindow() {
        setSize(400,300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);
        setLocationRelativeTo(null);
        setResizable(false);
        
        JButton chooseButton = new JButton("choose files");
        chooseButton.setFocusPainted(false);
        chooseButton.setBounds(130,110,120,20);
        chooseButton.addActionListener(v -> {
            chooser = new JFileChooser();

            chooser.rescanCurrentDirectory();
            
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setMultiSelectionEnabled(true);
            
            int result = chooser.showOpenDialog(null);

            if (result == JFileChooser.APPROVE_OPTION) {
                new Thread(() -> sendFile()).start();
            } else {
                
            }
        });
        
        progress = new JProgressBar();
        progress.setBounds(115,140,150,20);
        progress.setStringPainted(true);
        progress.setVisible(false);
        
        JButton backButton = new JButton("Back");
        backButton.setFocusPainted(false);
        backButton.setBounds(130,165,100,20);
        backButton.addActionListener(v -> {
            FileSender fileSender = new FileSender();
            fileSender.setVisible(true);
            dispose();
        });
        add(updateLabel);
        add(chooseButton); add(progress);
        add(backButton);
        setVisible(true);
    }
    
    private void sendFile() {
        progress.setVisible(true);
        int port = 5000;
        List<File> files = Arrays.asList(chooser.getSelectedFiles());
        
        try (Socket socket = new Socket(receiverIp, port)){
            updateLabel.setText("Sending files");
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            
            // Send total number of items (files + folders)
            dos.writeInt(files.size());
            
            for(File f : files) {
                if(f.isFile()) {
                    // send FILE type
                    dos.writeInt(0);
                    
                    dos.writeUTF(f.getName());
                    dos.writeLong(f.length());
                    
                    try (FileInputStream fis = new FileInputStream(f)) {
                        byte[] buffer = new byte[4096];
                        
                        int bytesRead;
                        
                        long total = f.length();
                        long sent = 0;
                        
                        while((bytesRead = fis.read(buffer)) != -1) {
                            dos.write(buffer, 0, bytesRead);
                            
                            sent+=bytesRead;
                            
                            int p = (int) ((sent*100)/total);
                            SwingUtilities.invokeLater(() -> progress.setValue(p));
                        }
                    }
                } else if(f.isDirectory()) {
                    //Send FOLDER type
                    dos.writeInt(1);
                    
                    dos.writeUTF(f.getName());
                    File[] filesInside = f.listFiles();
                    if(filesInside == null)filesInside = new File[0];
                    
                    dos.writeInt(filesInside.length);
                    
                    for(File insideFile : filesInside) {
                        dos.writeUTF(insideFile.getName());
                        dos.writeLong(insideFile.length());
                        
                        try (FileInputStream fis = new FileInputStream(insideFile)) {
                            byte[] buffer = new byte[4096];
                            
                            int bytesRead;
                            
                            long total = insideFile.length();
                            long sent = 0;
                            
                            while((bytesRead = fis.read(buffer)) != -1) {
                                dos.write(buffer, 0, bytesRead);
                                
                                sent += bytesRead;
                                
                                int p = (int)((sent * 100) / total);
                                
                                SwingUtilities.invokeLater(() -> progress.setValue(p));
                            }
                        }
                    }
                }
            }
            updateLabel.setText("All files sent!");
        } catch(Exception e) {
            updateLabel.setText("Could not send files");
        }
    }
    
}