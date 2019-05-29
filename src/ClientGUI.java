
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.*;
import javax.swing.text.DefaultCaret;


public class ClientGUI {

    JFrame frame = new JFrame("Chat");
    JPanel input = new JPanel();
    JTextField textField = new JTextField(60);
    JTextArea messageArea = new JTextArea(20, 60);
    DefaultCaret caret = (DefaultCaret) messageArea.getCaret();
    DefaultListModel clients = new DefaultListModel();
    JList users = new JList((DefaultListModel) clients);
    JButton button = new JButton("Send");
    JButton nickButton = new JButton("Change Nickname");
    JScrollPane usersListScroll = new JScrollPane(users);
    String nick;
    MySocket socket;

    public ClientGUI() {
        // si no es desactiva la edició, el usuari pot escriure a la finestra on s'imprimeixen missatges
        messageArea.setEditable(false);
        messageArea.setBackground(Color.LIGHT_GRAY);
        messageArea.setLineWrap(true);

        users.setLayoutOrientation(JList.VERTICAL);
        users.setBackground(Color.GRAY);


        button.setPreferredSize(new Dimension(75, 20));

        button.addActionListener(new Send());
        nickButton.setPreferredSize(new Dimension(150,20));
        nickButton.addActionListener(new ChangeNick());
        textField.addActionListener(new Send());

        input.add(textField, "Center");
        input.add(button, "East");
        input.add(nickButton,"East");
        input.setBackground(Color.DARK_GRAY);

        frame.getContentPane().add(input, "South");
        frame.getContentPane().add(new JScrollPane(messageArea), "Center");
        frame.getContentPane().add(usersListScroll, "West");

        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        frame.pack();

    }

    private String[] getIP() {
        // La ip que apareix per defecte es localhost
        return JOptionPane.showInputDialog(frame, "Enter server IP", "localhost:8080").split(":");
    }

    private String getNick() {
        // El nick per defecte es "Nick"
        return JOptionPane.showInputDialog(frame, "Write your nick:", "Nick");
    }

    private void run() throws IOException {

        String serverAddress[] = getIP();

        socket = new MySocket(serverAddress[0], Integer.parseInt(serverAddress[1]));

        //El cursor apareix a la finestra de text automàticament
        textField.requestFocus();

        while (true) {
            String line = socket.readLine();

            // Protocol de missatgeria instantànea inventat:
            //  El servidor envia un una string on el primer char es llegeix com a byte,
            //  que indica que ha de fer el client amb la resta del missatge.
            //      0x01 es una peticio de nick
            //      0x03 vol dir que el string que el segueix és un missatge que cal imprimir
            //      0x04 nou usuari s'ha unit
            //      0x05 usuari ha marxat, cal esborrarlo
            System.out.println(line);
            switch (line.charAt(0)) {
                case '\01':
                    nick = getNick();

                    socket.println('\u0001' +" " + nick);
                    break;

                case '\03':
                    messageArea.append(line.substring(1) + "\n");
                    break;
                case '\04':
                    clients.addElement(line.substring(1));
                    System.out.println(clients);
                    break;
                case '\05':
                    clients.removeElement(line.substring(1));
                    break;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ClientGUI client = new ClientGUI();

        // El programa tanca quan apretem la x
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        client.run();
    }

    public class Send implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String msg = textField.getText();
            if (!msg.isEmpty()) {
                socket.println(msg);

                textField.setText("");
            }
        }
    }

    public class ChangeNick implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {

            String nick = getNick();
            socket.println('\u0001' +" " + nick);

        }
    }

}
