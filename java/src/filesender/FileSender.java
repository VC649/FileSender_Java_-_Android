package filesender;

import javax.swing.*;

public class FileSender extends JFrame {
    
    private void initWindow() {
        setSize(400,300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);
        setLocationRelativeTo(null);
        setResizable(false);
        
        JButton sendButton = new JButton("send");
        sendButton.setFocusPainted(false);
        sendButton.setBounds(130,110,120,20);
        sendButton.addActionListener(v -> {
            int x = getX(); int y = getY();
            
            Sender sender = new Sender();
            sender.setLocation(x, y);
            dispose();
        });
        
        JButton receiveButton = new JButton("receive");
        receiveButton.setFocusPainted(false);
        receiveButton.setBounds(130,135,120,20);
        receiveButton.addActionListener(v -> {
            Receiver receiver = new Receiver();
            receiver.setVisible(true);
        
            new Thread(() -> receiver.receiveFiles()).start();
            
            dispose();
        });
        
        add(sendButton); add(receiveButton);
    }
    
    public FileSender() {
        initWindow();
    }
    
    public static void main(String[] args) {
        FileSender sender = new FileSender();
        sender.setVisible(true);
    }
}