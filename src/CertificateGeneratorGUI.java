import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CertificateGeneratorGUI extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/1543237954506465341/0anK5YKySOrgMKz2J9kDMX5O8B4HbwutjnuUxVpEGmmnXhPhpNuqCZnWycsNeIwvMaQI";

    private JComboBox<String> templateDropdown;
    private JList<CheckListItem> personnelList;
    private DefaultListModel<CheckListItem> listModel;
    private JLabel rankLabel, idLabel;
    private JTextField rankField, idField;
    private JCheckBox selectAllCheckBox;
    private JTextArea reasonArea;
    private JButton generateButton, openDbButton;
    private JLabel statusLabel;

    private static class CheckListItem {
        private final DatabaseHelper.Personnel personnel;
        private boolean isSelected;

        public CheckListItem(DatabaseHelper.Personnel personnel) {
            this.personnel = personnel;
            this.isSelected = false;
        }

        public DatabaseHelper.Personnel getPersonnel() {
            return personnel;
        }

        public boolean isSelected() {
            return isSelected;
        }

        public void setSelected(boolean isSelected) {
            this.isSelected = isSelected;
        }
    }

    private static class CheckListRenderer extends JCheckBox implements ListCellRenderer<CheckListItem> {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<? extends CheckListItem> list, CheckListItem value, int index, boolean isSelected, boolean cellHasFocus) {
            setEnabled(list.isEnabled());
            setSelected(value.isSelected());
            setFont(list.getFont());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            setText(value.getPersonnel().getName());
            return this;
        }
    }

    public CertificateGeneratorGUI() {
        setTitle("Roblox Certificate Generator");
        setSize(560, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 0. Template Dropdown
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Certificate Template:"), gbc);
        gbc.gridx = 1;
        String[] templates = {
            "PNP - Certificate of Recognition",
            "PNP - Certificate of Achievement",
            "PNP - Certificate of Appreciation",
            "PNP - Certificate of Commendation",
            "Gov Office - Certificate of Appreciation",
            "Gov Office - General Certificate"
        };
        templateDropdown = new JComboBox<>(templates);
        add(templateDropdown, gbc);

        // 1. Select Personnel List (Checkboxes)
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("Select Personnel:"), gbc);
        gbc.gridx = 1;

        listModel = new DefaultListModel<>();
        personnelList = new JList<>(listModel);
        personnelList.setCellRenderer(new CheckListRenderer());
        personnelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        personnelList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = personnelList.locationToIndex(event.getPoint());
                if (index >= 0) {
                    CheckListItem item = listModel.getElementAt(index);
                    item.setSelected(!item.isSelected());
                    personnelList.repaint(personnelList.getCellBounds(index, index));
                    updateFieldsFromSelection();
                }
            }
        });

        JScrollPane listScrollPane = new JScrollPane(personnelList);
        listScrollPane.setPreferredSize(new Dimension(220, 110));

        JPanel listPanel = new JPanel(new BorderLayout(5, 5));
        listPanel.add(listScrollPane, BorderLayout.CENTER);

        selectAllCheckBox = new JCheckBox("Select All");
        listPanel.add(selectAllCheckBox, BorderLayout.SOUTH);
        add(listPanel, gbc);

        // 2. Separate Rank/Position Field
        gbc.gridx = 0; gbc.gridy = 2;
        rankLabel = new JLabel("Rank:");
        add(rankLabel, gbc);
        gbc.gridx = 1;
        rankField = new JTextField(20);
        rankField.setEditable(false);
        add(rankField, gbc);

        // 3. Separate Badge/ID Field
        gbc.gridx = 0; gbc.gridy = 3;
        idLabel = new JLabel("Badge / Serial No:");
        add(idLabel, gbc);
        gbc.gridx = 1;
        idField = new JTextField(20);
        idField.setEditable(false);
        add(idField, gbc);

        // 4. Reason Input
        gbc.gridx = 0; gbc.gridy = 4;
        add(new JLabel("Reason / Message:"), gbc);
        gbc.gridx = 1;
        reasonArea = new JTextArea(3, 20);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        add(new JScrollPane(reasonArea), gbc);

        // 5. Action Buttons
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        generateButton = new JButton("Generate & Send");
        add(generateButton, gbc);

        gbc.gridy = 6;
        openDbButton = new JButton("⚙️ Open Roster Database Manager");
        add(openDbButton, gbc);

        // 6. Status Label
        gbc.gridy = 7;
        statusLabel = new JLabel("Check personnel, then click Generate.", SwingConstants.CENTER);
        statusLabel.setForeground(Color.GRAY);
        add(statusLabel, gbc);

        // Listeners
        templateDropdown.addActionListener(e -> loadPersonnelByTemplate());

        selectAllCheckBox.addActionListener(e -> {
            boolean selectState = selectAllCheckBox.isSelected();
            for (int i = 0; i < listModel.getSize(); i++) {
                listModel.getElementAt(i).setSelected(selectState);
            }
            personnelList.repaint();
            updateFieldsFromSelection();
        });

        generateButton.addActionListener(e -> processBulkCertificates());
        openDbButton.addActionListener(e -> new DatabaseManagerGUI(this::loadPersonnelByTemplate).setVisible(true));

        // Initial Load
        loadPersonnelByTemplate();
    }

    private void loadPersonnelByTemplate() {
        listModel.clear();
        String selectedTemplate = (String) templateDropdown.getSelectedItem();

        if (selectedTemplate != null && selectedTemplate.startsWith("Gov Office")) {
            rankLabel.setText("Position / Title:");
            idLabel.setText("ID / Serial No:");
            for (DatabaseHelper.Personnel p : DatabaseHelper.getAllGovMembers()) {
                listModel.addElement(new CheckListItem(p));
            }
        } else {
            rankLabel.setText("Rank:");
            idLabel.setText("Badge / Serial No:");
            for (DatabaseHelper.Personnel p : DatabaseHelper.getAllPNPOfficers()) {
                listModel.addElement(new CheckListItem(p));
            }
        }
        if (selectAllCheckBox != null) {
            selectAllCheckBox.setSelected(false);
        }
        updateFieldsFromSelection();
    }

    private void updateFieldsFromSelection() {
        List<DatabaseHelper.Personnel> selected = getSelectedPersonnel();
        if (selected.size() == 1) {
            DatabaseHelper.Personnel p = selected.get(0);
            rankField.setText(p.getTitle());
            idField.setText(p.getIdNo());
        } else if (selected.size() > 1) {
            rankField.setText("(" + selected.size() + " Personnel Selected)");
            idField.setText("Multiple Selected");
        } else {
            rankField.setText("");
            idField.setText("");
        }
    }

    private List<DatabaseHelper.Personnel> getSelectedPersonnel() {
        List<DatabaseHelper.Personnel> selected = new ArrayList<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            CheckListItem item = listModel.getElementAt(i);
            if (item.isSelected()) {
                selected.add(item.getPersonnel());
            }
        }
        return selected;
    }

    private String resolveTemplateFileName(String templateName) {
        if (templateName == null) return "certif.png";

        switch (templateName) {
            case "PNP - Certificate of Recognition":
                return "certifi.png";
            case "PNP - Certificate of Achievement":
                return "certif.png";
            case "PNP - Certificate of Appreciation":
                return "certific.png";
            case "PNP - Certificate of Commendation":
                return "certifica.png";
            case "Gov Office - Certificate of Appreciation":
            case "Gov Office - General Certificate":
                return "govcert.png";
            default:
                return "govcerti.png";
        }
    }

    private void processBulkCertificates() {
        List<DatabaseHelper.Personnel> selectedPersonnel = getSelectedPersonnel();
        String selectedTemplate = (String) templateDropdown.getSelectedItem();
        String reason = reasonArea.getText().trim();

        if (selectedPersonnel.isEmpty() || reason.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please check at least one person and enter a message.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isGov = selectedTemplate != null && selectedTemplate.startsWith("Gov Office");
        String templateFileName = resolveTemplateFileName(selectedTemplate);
        String idPrefix = isGov ? "ID NO: " : "BADGE NO: ";

        generateButton.setEnabled(false);
        statusLabel.setForeground(Color.BLUE);

        new Thread(() -> {
            int total = selectedPersonnel.size();
            int successCount = 0;

            for (int i = 0; i < total; i++) {
                DatabaseHelper.Personnel personnel = selectedPersonnel.get(i);
                int currentNum = i + 1;

                SwingUtilities.invokeLater(() -> statusLabel.setText("Processing " + currentNum + " of " + total + ": " + personnel.getName()));

                try {
                    String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));

                    String discordTitle = personnel.getTitle() + " " + personnel.getName();
                    String certImageName = isGov ? personnel.getName() : discordTitle;

                    // Generate unique reference number and save record into MySQL
                    String refNum = DatabaseHelper.generateUniqueRefCode();
                    DatabaseHelper.saveCertificateRecord(refNum, discordTitle, personnel.getIdNo(), selectedTemplate, date);

                    File templateFile = new File(templateFileName);
                    if (!templateFile.exists()) {
                        File fallbackFile = new File("certif.png");
                        if (fallbackFile.exists()) {
                            templateFile = fallbackFile;
                        } else {
                            throw new IOException("Template file missing: " + templateFileName);
                        }
                    }

                    BufferedImage image = ImageIO.read(templateFile);
                    Graphics2D g2d = image.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    FontMetrics metrics;

                    if (isGov) {
                        // --- GOV OFFICE CANVAS LAYOUT ---
                        Font refFont = new Font("Arial", Font.BOLD, 20);
                        g2d.setFont(refFont); 
                        g2d.setColor(new Color(120, 120, 120));
                        String refText = "NO: " + refNum; 
                        metrics = g2d.getFontMetrics(refFont);
                        g2d.drawString(refText, image.getWidth() - metrics.stringWidth(refText) - 160, 140);

                        Font nameFont = new Font("Georgia", Font.BOLD, 52);
                        g2d.setFont(nameFont); 
                        g2d.setColor(new Color(26, 26, 26));
                        String capsNameText = certImageName.toUpperCase(); 
                        metrics = g2d.getFontMetrics(nameFont);
                        g2d.drawString(capsNameText, (image.getWidth() - metrics.stringWidth(capsNameText)) / 2, 640);

                        Font idFont = new Font("Georgia", Font.BOLD, 22);
                        g2d.setFont(idFont); 
                        g2d.setColor(new Color(90, 90, 90));
                        String idText = idPrefix + personnel.getIdNo(); 
                        metrics = g2d.getFontMetrics(idFont);
                        g2d.drawString(idText, (image.getWidth() - metrics.stringWidth(idText)) / 2, 710);

                        Font reasonFont = new Font("Georgia", Font.PLAIN, 46);
                        g2d.setFont(reasonFont); 
                        g2d.setColor(new Color(40, 40, 40));
                        metrics = g2d.getFontMetrics(reasonFont);
                        int maxWidth = 1350;
                        int startY = 770;
                        int lineHeight = metrics.getHeight() + 8;

                        String capsReason = reason.toUpperCase();
                        String[] words = capsReason.split(" ");
                        StringBuilder currentLine = new StringBuilder();

                        for (String word : words) {
                            if (metrics.stringWidth(currentLine + " " + word) < maxWidth) {
                                if (currentLine.length() > 0) currentLine.append(" ");
                                currentLine.append(word);
                            } else {
                                g2d.drawString(currentLine.toString(), (image.getWidth() - metrics.stringWidth(currentLine.toString())) / 2, startY);
                                startY += lineHeight;
                                currentLine = new StringBuilder(word);
                            }
                        }
                        if (currentLine.length() > 0) {
                            g2d.drawString(currentLine.toString(), (image.getWidth() - metrics.stringWidth(currentLine.toString())) / 2, startY);
                        }

                        Font dateFont = new Font("Georgia", Font.ITALIC, 24);
                        g2d.setFont(dateFont); 
                        g2d.setColor(new Color(60, 60, 60));
                        String dateText = "Issued on " + date; 
                        metrics = g2d.getFontMetrics(dateFont);
                        g2d.drawString(dateText, image.getWidth() - metrics.stringWidth(dateText) - 300, 1000);

                    } else {
                        // --- STANDARD PNP CANVAS LAYOUT ---
                        Font refFont = new Font("Arial", Font.BOLD, 18);
                        g2d.setFont(refFont); 
                        g2d.setColor(new Color(100, 100, 100));
                        String refText = "NO: " + refNum; 
                        metrics = g2d.getFontMetrics(refFont);
                        g2d.drawString(refText, image.getWidth() - metrics.stringWidth(refText) - 100, 90);

                        Font nameFont = new Font("Georgia", Font.BOLD, 52);
                        g2d.setFont(nameFont); 
                        g2d.setColor(new Color(26, 26, 26));
                        String capsNameText = certImageName.toUpperCase(); 
                        metrics = g2d.getFontMetrics(nameFont);
                        g2d.drawString(capsNameText, (image.getWidth() - metrics.stringWidth(capsNameText)) / 2, 550);

                        Font badgeFont = new Font("Georgia", Font.BOLD, 22);
                        g2d.setFont(badgeFont); 
                        g2d.setColor(new Color(80, 80, 80));
                        String badgeText = idPrefix + personnel.getIdNo(); 
                        metrics = g2d.getFontMetrics(badgeFont);
                        g2d.drawString(badgeText, (image.getWidth() - metrics.stringWidth(badgeText)) / 2, 600);

                        Font reasonFont = new Font("Georgia", Font.PLAIN, 52);
                        g2d.setFont(reasonFont); 
                        g2d.setColor(new Color(40, 40, 40));
                        metrics = g2d.getFontMetrics(reasonFont);
                        int maxWidth = image.getWidth() - 300, startY = 670, lineHeight = metrics.getHeight() + 8;

                        String capsReason = reason.toUpperCase();
                        String[] words = capsReason.split(" ");
                        StringBuilder currentLine = new StringBuilder();

                        for (String word : words) {
                            if (metrics.stringWidth(currentLine + " " + word) < maxWidth) {
                                if (currentLine.length() > 0) currentLine.append(" ");
                                currentLine.append(word);
                            } else {
                                g2d.drawString(currentLine.toString(), (image.getWidth() - metrics.stringWidth(currentLine.toString())) / 2, startY);
                                startY += lineHeight;
                                currentLine = new StringBuilder(word);
                            }
                        }
                        if (currentLine.length() > 0) {
                            g2d.drawString(currentLine.toString(), (image.getWidth() - metrics.stringWidth(currentLine.toString())) / 2, startY);
                        }

                        Font dateFont = new Font("Georgia", Font.ITALIC, 26);
                        g2d.setFont(dateFont); 
                        g2d.setColor(new Color(26, 26, 26));
                        String dateText = "Issued on " + date; 
                        metrics = g2d.getFontMetrics(dateFont);
                        g2d.drawString(dateText, image.getWidth() - metrics.stringWidth(dateText) - 250, 1020);
                    }

                    g2d.dispose();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    byte[] imageBytes = baos.toByteArray();

                    sendToDiscord(discordTitle.toUpperCase(), personnel.getIdNo(), reason, date, refNum, selectedTemplate, imageBytes);
                    successCount++;

                    Thread.sleep(500);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            final int finalSuccessCount = successCount;
            SwingUtilities.invokeLater(() -> {
                statusLabel.setForeground(new Color(0, 128, 0));
                statusLabel.setText("Completed! Sent " + finalSuccessCount + " of " + total + " certificates.");
                reasonArea.setText("");
                selectAllCheckBox.setSelected(false);
                for (int i = 0; i < listModel.getSize(); i++) {
                    listModel.getElementAt(i).setSelected(false);
                }
                personnelList.repaint();
                updateFieldsFromSelection();
                generateButton.setEnabled(true);
            });
        }).start();
    }

    private void sendToDiscord(String fullTitle, String badge, String reason, String date, String refNum, String templateType, byte[] imageBytes)
            throws IOException, InterruptedException {

        String boundary = "----JavaBoundary" + UUID.randomUUID().toString();
        String messageText = String.format("📄 **New Certificate Generated!**\n**Template:** %s\n**Personnel:** %s\n**ID/Badge No:** `%s`\n**Reason:** %s\n**Date:** %s\n**Ref Code:** `%s`", 
                templateType, fullTitle, badge, reason, date, refNum);

        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bodyStream.write("Content-Disposition: form-data; name=\"content\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        bodyStream.write((messageText + "\r\n").getBytes(StandardCharsets.UTF_8));

        bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bodyStream.write(String.format("Content-Disposition: form-data; name=\"file\"; filename=\"%s_Certificate.png\"\r\n", fullTitle.replace(" ", "_")).getBytes(StandardCharsets.UTF_8));
        bodyStream.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        bodyStream.write(imageBytes);
        bodyStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        bodyStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DISCORD_WEBHOOK_URL))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyStream.toByteArray()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Discord webhook status code " + response.statusCode());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CertificateGeneratorGUI().setVisible(true));
    }
}