import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class DatabaseManagerGUI extends JFrame {
	
    private static final long serialVersionUID = 1L;
	
    private JTable pnpTable, govTable, certTable;
    private DefaultTableModel pnpModel, govModel, certModel;
    private JTextField pnpBadge, pnpName, pnpRank;
    private JTextField govId, govName, govPos;
    private String selectedPnpOldBadge = null;
    private final Runnable onDatabaseUpdatedCallback;

    public DatabaseManagerGUI(Runnable onDatabaseUpdatedCallback) {
        this.onDatabaseUpdatedCallback = onDatabaseUpdatedCallback;

        setTitle("Database Roster & Certificate Manager");
        setSize(780, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB 1: PNP Officers (officers table)
        JPanel pnpPanel = new JPanel(new BorderLayout());
        pnpModel = new DefaultTableModel(new String[]{"Badge No", "Name", "Rank"}, 0);
        pnpTable = new JTable(pnpModel);
        pnpPanel.add(new JScrollPane(pnpTable), BorderLayout.CENTER);

        JPanel pnpInput = new JPanel(new GridLayout(4, 3, 5, 5));
        pnpInput.add(new JLabel("Badge No:")); pnpBadge = new JTextField(); pnpInput.add(pnpBadge);
        JButton savePnp = new JButton("Save New Officer"); pnpInput.add(savePnp);

        pnpInput.add(new JLabel("Name:")); pnpName = new JTextField(); pnpInput.add(pnpName);
        JButton updatePnp = new JButton("Update Selected Officer"); pnpInput.add(updatePnp);

        pnpInput.add(new JLabel("Rank:")); pnpRank = new JTextField(); pnpInput.add(pnpRank);
        JButton delPnp = new JButton("Delete Selected"); pnpInput.add(delPnp);

        pnpPanel.add(pnpInput, BorderLayout.SOUTH);

        // TAB 2: Gov Members (government_members table)
        JPanel govPanel = new JPanel(new BorderLayout());
        govModel = new DefaultTableModel(new String[]{"ID No", "Name", "Position"}, 0);
        govTable = new JTable(govModel);
        govPanel.add(new JScrollPane(govTable), BorderLayout.CENTER);

        JPanel govInput = new JPanel(new GridLayout(4, 2, 5, 5));
        govInput.add(new JLabel("ID No:")); govId = new JTextField(); govInput.add(govId);
        govInput.add(new JLabel("Name:")); govName = new JTextField(); govInput.add(govName);
        govInput.add(new JLabel("Position:")); govPos = new JTextField(); govInput.add(govPos);
        JButton saveGov = new JButton("Save Gov Member"); JButton delGov = new JButton("Delete Selected");
        govInput.add(saveGov); govInput.add(delGov);
        govPanel.add(govInput, BorderLayout.SOUTH);

        // TAB 3: Issued Certificates Log (certificates table)
        JPanel certPanel = new JPanel(new BorderLayout());
        certModel = new DefaultTableModel(new String[]{"Ref Code", "Personnel Name", "ID/Badge", "Template", "Date"}, 0);
        certTable = new JTable(certModel);
        certPanel.add(new JScrollPane(certTable), BorderLayout.CENTER);

        JPanel certInput = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton delCert = new JButton("Delete Selected Certificate");
        certInput.add(delCert);
        certPanel.add(certInput, BorderLayout.SOUTH);

        tabbedPane.addTab("PNP Personnel", pnpPanel);
        tabbedPane.addTab("Government Personnel", govPanel);
        tabbedPane.addTab("Issued Certificates Log", certPanel);
        add(tabbedPane);

        // Row Selection Auto-fill (PNP)
        pnpTable.getSelectionModel().addListSelectionListener(e -> {
            int row = pnpTable.getSelectedRow();
            if (row >= 0) {
                selectedPnpOldBadge = (String) pnpModel.getValueAt(row, 0);
                pnpBadge.setText(selectedPnpOldBadge);
                pnpName.setText((String) pnpModel.getValueAt(row, 1));
                pnpRank.setText((String) pnpModel.getValueAt(row, 2));
            }
        });

        // Row Selection Auto-fill (Gov)
        govTable.getSelectionModel().addListSelectionListener(e -> {
            int row = govTable.getSelectedRow();
            if (row >= 0) {
                govId.setText((String) govModel.getValueAt(row, 0));
                govName.setText((String) govModel.getValueAt(row, 1));
                govPos.setText((String) govModel.getValueAt(row, 2));
            }
        });

        // PNP Listeners
        savePnp.addActionListener(e -> {
            if (DatabaseHelper.savePNPOfficer(pnpBadge.getText(), pnpName.getText(), pnpRank.getText())) {
                refreshTables(); 
                clearInputs(); 
                notifyCallback();
            }
        });

        updatePnp.addActionListener(e -> {
            if (selectedPnpOldBadge != null && DatabaseHelper.updatePNPOfficer(selectedPnpOldBadge, pnpBadge.getText(), pnpName.getText(), pnpRank.getText())) {
                refreshTables(); 
                clearInputs(); 
                notifyCallback();
            } else {
                JOptionPane.showMessageDialog(this, "Please select an officer from the table to edit.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        delPnp.addActionListener(e -> {
            int row = pnpTable.getSelectedRow();
            if (row >= 0 && DatabaseHelper.deletePNPOfficer((String) pnpModel.getValueAt(row, 0))) {
                refreshTables(); 
                clearInputs(); 
                notifyCallback();
            }
        });

        // Gov Listeners
        saveGov.addActionListener(e -> {
            if (DatabaseHelper.saveGovMember(govId.getText(), govName.getText(), govPos.getText())) {
                refreshTables(); 
                clearInputs(); 
                notifyCallback();
            }
        });
        delGov.addActionListener(e -> {
            int row = govTable.getSelectedRow();
            if (row >= 0 && DatabaseHelper.deleteGovMember((String) govModel.getValueAt(row, 0))) {
                refreshTables(); 
                clearInputs(); 
                notifyCallback();
            }
        });

        // Cert Listener
        delCert.addActionListener(e -> {
            int row = certTable.getSelectedRow();
            if (row >= 0) {
                String refCode = (String) certModel.getValueAt(row, 0);
                if (DatabaseHelper.deleteCertificateRecord(refCode)) {
                    refreshTables();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a certificate row to delete.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        refreshTables();
    }

    private void notifyCallback() {
        if (onDatabaseUpdatedCallback != null) {
            onDatabaseUpdatedCallback.run();
        }
    }

    private void refreshTables() {
        pnpModel.setRowCount(0);
        for (DatabaseHelper.Personnel p : DatabaseHelper.getAllPNPOfficers()) {
            pnpModel.addRow(new Object[]{p.getIdNo(), p.getName(), p.getTitle()});
        }
        govModel.setRowCount(0);
        for (DatabaseHelper.Personnel p : DatabaseHelper.getAllGovMembers()) {
            govModel.addRow(new Object[]{p.getIdNo(), p.getName(), p.getTitle()});
        }
        certModel.setRowCount(0);
        for (String[] row : DatabaseHelper.getAllCertificates()) {
            certModel.addRow(row);
        }
    }

    private void clearInputs() {
        selectedPnpOldBadge = null;
        pnpBadge.setText(""); pnpName.setText(""); pnpRank.setText("");
        govId.setText(""); govName.setText(""); govPos.setText("");
    }
}