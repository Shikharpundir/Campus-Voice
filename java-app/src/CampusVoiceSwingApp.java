import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.JDialog;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.SortOrder;

public class CampusVoiceSwingApp extends JFrame {
    private static final long serialVersionUID = 1L;

    // Palette & Theme Constants
    private static final Color BG_LIGHT = new Color(248, 250, 252);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color PRIMARY_COLOR = new Color(79, 70, 229);      // Indigo-600
    private static final Color PRIMARY_HOVER = new Color(67, 56, 202);      // Indigo-700
    private static final Color TEXT_MAIN = new Color(30, 41, 59);           // Slate-800
    private static final Color TEXT_MUTED = new Color(100, 116, 139);       // Slate-500
    private static final Color ACCENT_SUCCESS = new Color(16, 185, 129);    // Emerald-500
    private static final Color ACCENT_DANGER = new Color(239, 68, 68);      // Red-500

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);

    private final ComplaintService complaintService = new ComplaintService();
    private final OSModuleService osModule = new OSModuleService();
    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel loginPanel = new JPanel(new GridBagLayout());
    private final JPanel dashboardPanel = new JPanel(new BorderLayout());

    private final DefaultTableModel complaintTableModel = new DefaultTableModel(
            new String[] {"ID", "Title", "Priority", "Effective Priority", "Status", "Department",
                    "Student", "Assigned / Routed To", "Age (days)"}, 0);
    private final JTable complaintTable = new JTable(complaintTableModel);
    private final JTextArea schedulerOutput = new JTextArea();
    private final JLabel welcomeLabel = new JLabel("Welcome");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel loginStatusLabel = new JLabel(" ");

    private JButton updateStatusButton;
    private JButton assignStaffButton;
    private JButton createStaffButton;
    private JButton listStaffButton;
    private JButton submitComplaintButton;
    private JButton runSchedulerButton;
    private JButton runSyncDemoButton;

    private final UserSession session = new UserSession();
    private File currentPhoto;
    private LocalDate lastAgeRefreshDate = LocalDate.now();

    public CampusVoiceSwingApp() {
        super("Campus Voice");
        setupModernSystemLaF();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1150, 750));
        setLocationRelativeTo(null);

        buildLoginScreen();
        buildDashboard();
        add(cards);

        showLoginScreen();
        setVisible(true);

        new Timer(60000, e -> {
            LocalDate today = LocalDate.now();
            if (!today.equals(lastAgeRefreshDate)) {
                lastAgeRefreshDate = today;
                if (session.currentUser() != null) refreshComplaintTable();
            }
        }).start();
    }

    private void setupModernSystemLaF() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        UIManager.put("Label.font", FONT_MAIN);
        UIManager.put("Button.font", FONT_MAIN);
        UIManager.put("TextField.font", FONT_MAIN);
        UIManager.put("Table.font", FONT_MAIN);
        UIManager.put("TableHeader.font", FONT_BOLD);
    }

    private void buildLoginScreen() {
        loginPanel.setBackground(BG_LIGHT);

        // Card container
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(40, 50, 40, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Campus Voice");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_MAIN);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        card.add(title, gbc);

        JLabel subtitle = new JLabel("Students, department staff, and administrators sign in here");
        subtitle.setFont(FONT_MAIN);
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 6, 24, 6);
        card.add(subtitle, gbc);

        gbc.insets = new Insets(6, 6, 6, 6);
        JLabel emailLabel = new JLabel("Institutional Email");
        emailLabel.setForeground(TEXT_MAIN);
        gbc.gridy = 2; gbc.gridwidth = 2;
        card.add(emailLabel, gbc);

        JTextField emailField = styleTextField(new JTextField(24));
        gbc.gridy = 3;
        card.add(emailField, gbc);

        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setForeground(TEXT_MAIN);
        gbc.gridy = 4;
        card.add(passwordLabel, gbc);

        JPasswordField passwordField = (JPasswordField) styleTextField(new JPasswordField(24));
        gbc.gridy = 5;
        card.add(passwordField, gbc);

        JButton signIn = createStyledButton("Sign In", PRIMARY_COLOR, Color.WHITE);
        JButton registerStudent = createStyledButton("Register as Student",
                new Color(241, 245, 249), TEXT_MAIN);
        JButton firstTimeStaff = createStyledButton("First-Time Staff Setup",
                new Color(241, 245, 249), TEXT_MAIN);

        signIn.addActionListener(e -> {
            try {
                String email = emailField.getText().trim();
                char[] password = passwordField.getPassword();
                UserAccount account;
                try {
                    account = complaintService.authenticate(email, password);
                } finally {
                    Arrays.fill(password, '\0');
                    passwordField.setText("");
                }
                if (account == null) {
                    setStatus("Invalid email or password.");
                    return;
                }
                session.signIn(account);
                showDashboardForUser();
            } catch (SQLException ex) {
                setStatus("Database error: " + ex.getMessage());
            }
        });

        gbc.gridy = 6;
        gbc.insets = new Insets(16, 6, 6, 6);
        JPanel signInActions = new JPanel(new GridLayout(3, 1, 0, 8));
        signInActions.setOpaque(false);
        signInActions.add(signIn);
        signInActions.add(registerStudent);
        signInActions.add(firstTimeStaff);
        card.add(signInActions, gbc);
        registerStudent.addActionListener(e -> openStudentRegistrationDialog());
        firstTimeStaff.addActionListener(e -> openFirstTimeStaffSetup());

        loginStatusLabel.setForeground(ACCENT_DANGER);
        loginStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginStatusLabel.setFont(FONT_SMALL);
        gbc.gridy = 7;
        card.add(loginStatusLabel, gbc);

        loginPanel.add(card);
        cards.add(loginPanel, "login");
    }

    private void buildDashboard() {
        dashboardPanel.setBackground(BG_LIGHT);

        // Header Panel
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(16, 24, 16, 24)
        ));

        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        welcomeLabel.setForeground(TEXT_MAIN);
        header.add(welcomeLabel, BorderLayout.WEST);

        JButton logout = createStyledButton("Logout", new Color(241, 245, 249), TEXT_MAIN);
        logout.addActionListener(e -> {
            session.signOut();
            showLoginScreen();
        });
        header.add(logout, BorderLayout.EAST);

        // Toolbar Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        controls.setBackground(BG_LIGHT);
        controls.setBorder(new EmptyBorder(0, 14, 0, 14));

        JButton refresh = createStyledButton("Refresh", CARD_BG, TEXT_MAIN);
        refresh.addActionListener(e -> refreshComplaintTable());

        submitComplaintButton = createStyledButton("+ Submit Complaint", PRIMARY_COLOR, Color.WHITE);
        submitComplaintButton.addActionListener(e -> openComplaintDialog());

        JButton details = createStyledButton("View Details", CARD_BG, TEXT_MAIN);
        details.addActionListener(e -> showSelectedComplaintDetails());

        updateStatusButton = createStyledButton("Update Status", CARD_BG, TEXT_MAIN);
        updateStatusButton.addActionListener(e -> openStatusDialog());

        assignStaffButton = createStyledButton("Assign Staff", CARD_BG, TEXT_MAIN);
        assignStaffButton.addActionListener(e -> openAssignmentDialog());

        createStaffButton = createStyledButton("Add Department Staff", CARD_BG, TEXT_MAIN);
        createStaffButton.addActionListener(e -> openStaffRegistrationDialog());

        listStaffButton = createStyledButton("View Staff Accounts", CARD_BG, TEXT_MAIN);
        listStaffButton.addActionListener(e -> showStaffAccounts());

        runSchedulerButton = createStyledButton("Run Aging Scheduler", CARD_BG, PRIMARY_COLOR);
        runSchedulerButton.addActionListener(e -> runAgingScheduler());

        runSyncDemoButton = createStyledButton("Run Mutex / Race Demo", CARD_BG, TEXT_MAIN);
        runSyncDemoButton.addActionListener(e -> runSynchronizationDemo());

        controls.add(refresh);
        controls.add(submitComplaintButton);
        controls.add(details);
        controls.add(updateStatusButton);
        controls.add(assignStaffButton);
        controls.add(createStaffButton);
        controls.add(listStaffButton);
        controls.add(runSchedulerButton);

        // Table Customization
        complaintTable.setRowHeight(38);
        complaintTable.setFont(FONT_MAIN);
        complaintTable.setShowVerticalLines(false);
        complaintTable.setGridColor(BORDER_COLOR);
        complaintTable.setSelectionBackground(new Color(238, 242, 255));
        complaintTable.setSelectionForeground(TEXT_MAIN);
        complaintTable.setAutoCreateRowSorter(false);
        TableRowSorter<DefaultTableModel> complaintSorter = new TableRowSorter<>(complaintTableModel);
        complaintSorter.setComparator(3, (left, right) ->
                Integer.compare(prioritySortRank(left), prioritySortRank(right)));
        complaintSorter.setComparator(8, (left, right) ->
                Integer.compare(Integer.parseInt(left.toString()), Integer.parseInt(right.toString())));
        complaintSorter.setSortKeys(Arrays.asList(
                new javax.swing.RowSorter.SortKey(3, SortOrder.ASCENDING),
                new javax.swing.RowSorter.SortKey(8, SortOrder.DESCENDING)));
        complaintTable.setRowSorter(complaintSorter);
        complaintTable.setFillsViewportHeight(true);

        JTableHeader tableHeader = complaintTable.getTableHeader();
        tableHeader.setBackground(CARD_BG);
        tableHeader.setForeground(TEXT_MUTED);
        tableHeader.setFont(FONT_HEADER);
        tableHeader.setPreferredSize(new Dimension(0, 40));
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        // Pill renderer for Priority and Status columns
        PillBadgeRenderer badgeRenderer = new PillBadgeRenderer();
        complaintTable.getColumnModel().getColumn(2).setCellRenderer(badgeRenderer);
        complaintTable.getColumnModel().getColumn(3).setCellRenderer(badgeRenderer);
        complaintTable.getColumnModel().getColumn(4).setCellRenderer(badgeRenderer);

        complaintTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && complaintTable.getSelectedRow() >= 0) {
                    showSelectedComplaintDetails();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(complaintTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        tableScroll.getViewport().setBackground(CARD_BG);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setOpaque(false);
        tablePanel.setBorder(new EmptyBorder(0, 24, 12, 24));
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        // Scheduler Console Area
        schedulerOutput.setEditable(false);
        schedulerOutput.setRows(6);
        schedulerOutput.setFont(new Font("Consolas", Font.PLAIN, 12));
        schedulerOutput.setBackground(new Color(15, 23, 42));
        schedulerOutput.setForeground(new Color(241, 245, 249));
        schedulerOutput.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane consoleScroll = new JScrollPane(schedulerOutput);
        consoleScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setOpaque(false);
        outputPanel.setBorder(new EmptyBorder(0, 24, 16, 24));
        JLabel schedulerLabel = new JLabel("OS Module Output");
        schedulerLabel.setFont(FONT_HEADER);
        schedulerLabel.setForeground(TEXT_MAIN);
        schedulerLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        JPanel outputHeader = new JPanel(new BorderLayout());
        outputHeader.setOpaque(false);
        outputHeader.add(schedulerLabel, BorderLayout.WEST);
        outputHeader.add(runSyncDemoButton, BorderLayout.EAST);
        outputPanel.add(outputHeader, BorderLayout.NORTH);
        outputPanel.add(consoleScroll, BorderLayout.CENTER);

        JPanel mainBody = new JPanel(new BorderLayout());
        mainBody.setOpaque(false);
        mainBody.add(tablePanel, BorderLayout.CENTER);
        mainBody.add(outputPanel, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(controls, BorderLayout.NORTH);
        content.add(mainBody, BorderLayout.CENTER);

        // Status Bar Footer
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(CARD_BG);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(8, 24, 8, 24)
        ));
        statusLabel.setForeground(TEXT_MUTED);
        statusBar.add(statusLabel, BorderLayout.WEST);

        dashboardPanel.add(header, BorderLayout.NORTH);
        dashboardPanel.add(content, BorderLayout.CENTER);
        dashboardPanel.add(statusBar, BorderLayout.SOUTH);

        cards.add(dashboardPanel, "dashboard");
    }

    private JButton createStyledButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(background.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(background.equals(CARD_BG) ? new Color(241, 245, 249) : PRIMARY_HOVER);
                } else {
                    g2.setColor(background);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (background.equals(CARD_BG) || background.equals(new Color(241, 245, 249))) {
                    g2.setColor(BORDER_COLOR);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setForeground(foreground);
        button.setFont(FONT_BOLD);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(8, 14, 8, 14));
        return button;
    }

    private JTextField styleTextField(JTextField field) {
        field.setFont(FONT_MAIN);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        return field;
    }

    private void showLoginScreen() {
        ((CardLayout) cards.getLayout()).show(cards, "login");
        setStatus("Ready");
    }

    private void showDashboardForUser() {
        UserAccount currentUser = session.currentUser();
        if (currentUser == null) {
            showLoginScreen();
            return;
        }
        welcomeLabel.setText("Welcome back, " + currentUser.name + "  •  " + currentUser.role
                + ("STAFF".equals(currentUser.role) ? " (" + currentUser.departmentName + ")" : ""));
        boolean admin = "ADMIN".equals(currentUser.role);
        boolean student = "STUDENT".equals(currentUser.role);
        boolean staff = "STAFF".equals(currentUser.role);
        submitComplaintButton.setVisible(student);
        runSchedulerButton.setVisible(admin);
        updateStatusButton.setVisible(admin || staff);
        assignStaffButton.setVisible(admin);
        createStaffButton.setVisible(admin);
        listStaffButton.setVisible(admin);
        refreshComplaintTable();
        ((CardLayout) cards.getLayout()).show(cards, "dashboard");
        setStatus(admin ? "Global administrator view active."
                : staff ? "Staff portal active. Showing complaints assigned to you."
                : "Student view active.");
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
        loginStatusLabel.setText(text);
    }

    private void refreshComplaintTable() {
        complaintTableModel.setRowCount(0);
        UserAccount currentUser = session.currentUser();
        if (currentUser == null) return;
        try {
            List<String[]> rows = complaintService.listComplaintsFor(currentUser);
            for (String[] row : rows) {
                complaintTableModel.addRow(new Object[] {
                        row[0], row[1], row[2], effectivePriorityName(row[2], row[3], Integer.parseInt(row[7])),
                        row[3], row[5], row[4], row[6], row[7]
                });
            }
            if (rows.isEmpty()) {
                setStatus("No complaints found.");
            }
        } catch (SQLException ex) {
            setStatus("Database error while loading complaints: " + ex.getMessage());
        }
    }

    private String effectivePriorityName(String priority, String status, int ageDays) {
        if ("RESOLVED".equals(status) || "REJECTED".equals(status)) return priority;
        int basePriority;
        switch (priority) {
            case "URGENT": basePriority = 1; break;
            case "HIGH": basePriority = 2; break;
            case "MEDIUM": basePriority = 3; break;
            default: basePriority = 4;
        }
        int effective = ComplaintService.SchedulingTask.applyAging(basePriority, ageDays);
        switch (effective) {
            case 1: return "URGENT";
            case 2: return "HIGH";
            case 3: return "MEDIUM";
            default: return "LOW";
        }
    }

    private static int prioritySortRank(Object value) {
        if (value == null) return Integer.MAX_VALUE;
        switch (value.toString()) {
            case "URGENT": return 1;
            case "HIGH": return 2;
            case "MEDIUM": return 3;
            case "LOW": return 4;
            default: return Integer.MAX_VALUE;
        }
    }

    private void openStaffRegistrationDialog() {
        UserAccount admin = session.currentUser();
        if (admin == null || !"ADMIN".equals(admin.role)) {
            setStatus("Only the global administrator can create staff accounts.");
            return;
        }
        JTextField nameField = styleTextField(new JTextField());
        JTextField emailField = styleTextField(new JTextField());
        Object[] fields = {
                "Full Name", nameField,
                "Email Address", emailField
        };
        int result = JOptionPane.showConfirmDialog(this, fields, "Create Staff Login",
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            complaintService.registerStaff(admin, nameField.getText(), emailField.getText());
            setStatus("Staff login created. The staff member must complete first-time setup.");
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        } catch (SQLException ex) {
            setStatus("Staff account creation failed: " + ex.getMessage());
        }
    }

    private void openStudentRegistrationDialog() {
        JTextField nameField = styleTextField(new JTextField());
        JTextField emailField = styleTextField(new JTextField());
        JPasswordField passwordField = (JPasswordField) styleTextField(new JPasswordField());
        JPasswordField confirmPasswordField = (JPasswordField) styleTextField(new JPasswordField());
        Object[] fields = {
                "Full Name", nameField,
                "Institutional Email", emailField,
                "Password (minimum 8 characters)", passwordField,
                "Confirm Password", confirmPasswordField
        };
        int result = JOptionPane.showConfirmDialog(this, fields, "Register Student Account",
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        char[] password = passwordField.getPassword();
        char[] confirmedPassword = confirmPasswordField.getPassword();
        try {
            if (!Arrays.equals(password, confirmedPassword)) {
                throw new IllegalArgumentException("The passwords do not match.");
            }
            complaintService.registerStudent(nameField.getText(), emailField.getText(), password);
            setStatus("Student account created. You can now sign in.");
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        } catch (SQLException ex) {
            setStatus("Student registration failed: " + ex.getMessage());
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmedPassword, '\0');
            passwordField.setText("");
            confirmPasswordField.setText("");
        }
    }

    private void showStaffAccounts() {
        UserAccount admin = session.currentUser();
        if (admin == null || !"ADMIN".equals(admin.role)) {
            setStatus("Only the global administrator can view staff accounts.");
            return;
        }
        try {
            List<String[]> staff = complaintService.listStaffAccounts(admin);
            DefaultTableModel model = new DefaultTableModel(
                    new String[] {"Name", "Email", "Department", "Account Status"}, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            for (String[] member : staff) {
                model.addRow(member);
            }
            JTable staffTable = new JTable(model);
            staffTable.setRowHeight(28);
            JScrollPane scrollPane = new JScrollPane(staffTable);
            scrollPane.setPreferredSize(new Dimension(620, 280));
            JOptionPane.showMessageDialog(this, scrollPane,
                    "Department Staff Accounts", JOptionPane.INFORMATION_MESSAGE);
            setStatus(staff.isEmpty() ? "No staff accounts have been created yet."
                    : "Showing " + staff.size() + " staff account(s).");
        } catch (SQLException ex) {
            setStatus("Unable to load staff accounts: " + ex.getMessage());
        }
    }

    private void openFirstTimeStaffSetup() {
        JTextField nameField = styleTextField(new JTextField());
        JTextField emailField = styleTextField(new JTextField());
        Object[] identityFields = {"Full Name", nameField, "Email Address", emailField};
        int identityResult = JOptionPane.showConfirmDialog(this, identityFields,
                "Verify Staff Account", JOptionPane.OK_CANCEL_OPTION);
        if (identityResult != JOptionPane.OK_OPTION) return;

        UserAccount pendingStaff;
        try {
            pendingStaff = complaintService.findPendingStaff(nameField.getText(), emailField.getText());
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
            return;
        } catch (SQLException ex) {
            setStatus("Unable to verify staff account: " + ex.getMessage());
            return;
        }
        if (pendingStaff == null) {
            setStatus("No pending staff login matches that name and email. Check with the administrator.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Staff account found for " + pendingStaff.name
                        + ". Continue to choose a department and create a password?",
                "Staff Account Verified", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        completeFirstTimeStaffSetup(pendingStaff, emailField.getText());
    }

    private void completeFirstTimeStaffSetup(UserAccount pendingStaff, String email) {
        List<String[]> departments;
        try {
            departments = complaintService.listDepartments();
        } catch (SQLException ex) {
            setStatus("Unable to load departments: " + ex.getMessage());
            return;
        }
        if (departments.isEmpty()) {
            setStatus("No departments are configured. Contact the administrator.");
            return;
        }
        JComboBox<String> departmentCombo = new JComboBox<>();
        for (String[] department : departments) departmentCombo.addItem(department[1]);
        JPasswordField passwordField = (JPasswordField) styleTextField(new JPasswordField());
        JPasswordField confirmPasswordField = (JPasswordField) styleTextField(new JPasswordField());
        Object[] fields = {
                "Department", departmentCombo,
                "Create Password (minimum 8 characters)", passwordField,
                "Confirm Password", confirmPasswordField
        };
        int result = JOptionPane.showConfirmDialog(this, fields,
                "Complete Staff Setup - " + pendingStaff.name, JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        char[] password = passwordField.getPassword();
        char[] confirmedPassword = confirmPasswordField.getPassword();
        try {
            if (!Arrays.equals(password, confirmedPassword)) {
                throw new IllegalArgumentException("The passwords do not match.");
            }
            int departmentId = Integer.parseInt(departments.get(departmentCombo.getSelectedIndex())[0]);
            UserAccount account = complaintService.activateStaffAccount(
                    pendingStaff, email, password, departmentId);
            session.signIn(account);
            showDashboardForUser();
            setStatus("Staff setup complete. Department saved: " + account.departmentName);
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        } catch (SQLException ex) {
            setStatus("Staff setup failed: " + ex.getMessage());
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmedPassword, '\0');
            passwordField.setText("");
            confirmPasswordField.setText("");
        }
    }

    private void openComplaintDialog() {
        UserAccount currentUser = session.currentUser();
        if (currentUser == null || !"STUDENT".equals(currentUser.role)) {
            setStatus("Please sign in first.");
            return;
        }

        JTextField titleField = styleTextField(new JTextField());
        JTextArea descriptionArea = new JTextArea(5, 30);
        descriptionArea.setFont(FONT_MAIN);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JTextField locationField = styleTextField(new JTextField());
        JComboBox<String> priorityCombo = new JComboBox<>(new String[] {"LOW", "MEDIUM", "HIGH", "URGENT"});
        List<String[]> departments;
        try {
            departments = complaintService.listDepartments();
        } catch (SQLException ex) {
            setStatus("Unable to load departments: " + ex.getMessage());
            return;
        }
        JComboBox<String> departmentCombo = new JComboBox<>();
        for (String[] department : departments) {
            departmentCombo.addItem(department[1]);
        }

        currentPhoto = null;
        JLabel photoLabel = new JLabel("No photo selected");
        photoLabel.setFont(FONT_SMALL);
        photoLabel.setForeground(TEXT_MUTED);

        JButton photoButton = createStyledButton("Choose Image...", CARD_BG, TEXT_MAIN);
        photoButton.addActionListener(e -> {
            currentPhoto = pickComplaintPhoto();
            photoLabel.setText(currentPhoto == null ? "No photo selected" : currentPhoto.getName());
        });

        JPanel photoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        photoPanel.add(photoButton);
        photoPanel.add(photoLabel);

        Object[] fields = {
                "Title", titleField,
                "Description", new JScrollPane(descriptionArea),
                "Location", locationField,
                "Department", departmentCombo,
                "Initial Priority", priorityCombo,
                "Evidence / Photo", photoPanel
        };

        int result = JOptionPane.showConfirmDialog(this, fields, "Submit Complaint", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String title = titleField.getText().trim();
        String description = descriptionArea.getText().trim();
        String location = locationField.getText().trim();
        if (title.isEmpty() || description.isEmpty() || location.isEmpty()) {
            setStatus("Please fill in all mandatory complaint fields.");
            return;
        }

        try {
            int departmentId = resolveDepartmentId(departmentCombo.getSelectedItem());
            byte[] attachmentData = null;
            String attachmentName = null;
            String attachmentMime = null;
            if (currentPhoto != null) {
                if (currentPhoto.length() > 10 * 1024 * 1024) {
                    throw new IllegalArgumentException("Image must be 10 MB or smaller.");
                }
                attachmentData = Files.readAllBytes(currentPhoto.toPath());
                attachmentName = currentPhoto.getName();
                attachmentMime = imageMimeType(currentPhoto);
            }
            int complaintId = complaintService.submitComplaint(currentUser.userId, departmentId, title, description,
                    location, priorityCombo.getSelectedItem().toString().toUpperCase(),
                    attachmentName, attachmentMime, attachmentData);
            currentPhoto = null;
            setStatus("Complaint submitted successfully. ID: #" + complaintId);
            refreshComplaintTable();
        } catch (SQLException | IOException ex) {
            setStatus("Complaint submission failed: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        }
    }

    private int resolveDepartmentId(Object selectedDepartment) throws SQLException {
        for (String[] dept : complaintService.listDepartments()) {
            if (dept[1].equals(selectedDepartment.toString())) {
                return Integer.parseInt(dept[0]);
            }
        }
        throw new IllegalArgumentException("Selected department was not found.");
    }

    private File pickComplaintPhoto() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new java.net.URI("microsoft.windows.camera:"));
            } catch (IOException | java.net.URISyntaxException | UnsupportedOperationException ex) {
                // Silently fallback to standard file chooser
            }
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Complaint Attachment");
        chooser.setFileFilter(new FileNameExtensionFilter("Images (*.jpg, *.png, *.gif)", "jpg", "jpeg", "png", "bmp", "gif"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private String imageMimeType(File image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(image)) {
            if (input == null) throw new IOException("Unable to read the selected image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("The selected file is not a supported image format.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                switch (reader.getFormatName().toLowerCase()) {
                    case "jpg":
                    case "jpeg": return "image/jpeg";
                    case "png": return "image/png";
                    case "gif": return "image/gif";
                    case "bmp": return "image/bmp";
                    default: throw new IllegalArgumentException("Unsupported image format.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private void showSelectedComplaintDetails() {
        int selected = complaintTable.getSelectedRow();
        if (selected < 0) {
            setStatus("Select a complaint to view its details.");
            return;
        }
        int modelRow = complaintTable.convertRowIndexToModel(selected);
        int complaintId = Integer.parseInt(complaintTableModel.getValueAt(modelRow, 0).toString());
        try {
            ComplaintService.ComplaintDetails details =
                    complaintService.getComplaintDetails(complaintId, session.currentUser());

            JTextArea text = new JTextArea(
                    "#" + details.complaintId + ": " + details.title + "\n"
                            + "----------------------------------------------------------------------\n"
                            + "Status: " + details.status + " | Base Priority: " + details.priority
                            + " | Effective Priority: " + effectivePriorityName(details.priority, details.status, details.ageDays) + "\n"
                            + "Department: " + details.department + " | Student: " + details.student + "\n"
                            + "Location: " + details.location + " | Assigned to: " + details.assignee + "\n"
                            + "Submitted: " + details.createdAt + " (" + details.ageDays + " days ago)\n\n"
                            + "Description:\n" + details.description);
            text.setEditable(false);
            text.setFont(FONT_MAIN);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setBorder(new EmptyBorder(8, 8, 8, 8));

            JLabel preview = new JLabel("No image attached", SwingConstants.CENTER);
            preview.setPreferredSize(new Dimension(600, 320));
            preview.setBackground(new Color(241, 245, 249));
            preview.setOpaque(true);
            preview.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

            JComboBox<String> attachmentSelector = new JComboBox<>();
            for (ComplaintService.Attachment attachment : details.attachments) {
                attachmentSelector.addItem(attachment.fileName);
            }
            if (!details.attachments.isEmpty()) {
                attachmentSelector.addActionListener(e -> {
                    int index = attachmentSelector.getSelectedIndex();
                    if (index >= 0) showAttachmentPreview(details.attachments.get(index), preview);
                });
            }

            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(new JScrollPane(text), BorderLayout.NORTH);
            if (details.attachments.isEmpty()) {
                panel.add(preview, BorderLayout.CENTER);
            } else {
                JPanel imagePanel = new JPanel(new BorderLayout(0, 6));
                imagePanel.add(attachmentSelector, BorderLayout.NORTH);
                imagePanel.add(preview, BorderLayout.CENTER);
                panel.add(imagePanel, BorderLayout.CENTER);
                showAttachmentPreview(details.attachments.get(0), preview);
            }
            panel.setPreferredSize(new Dimension(640, 560));
            JOptionPane.showMessageDialog(this, panel, "Complaint Details - #" + details.complaintId, JOptionPane.PLAIN_MESSAGE);
        } catch (SQLException | IllegalArgumentException ex) {
            setStatus("Could not load complaint details: " + ex.getMessage());
        }
    }

    private void showAttachmentPreview(ComplaintService.Attachment attachment, JLabel preview) {
        try {
            java.awt.image.BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(attachment.data));
            if (image == null) throw new IOException("Attachment could not be rendered.");
            int width = preview.getPreferredSize().width;
            int height = preview.getPreferredSize().height;
            double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
            int scaledWidth = Math.max(1, (int) (image.getWidth() * scale));
            int scaledHeight = Math.max(1, (int) (image.getHeight() * scale));
            Image scaled = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            preview.setText("");
            preview.setIcon(new ImageIcon(scaled));
        } catch (IOException ex) {
            preview.setIcon(null);
            preview.setText("Image preview unavailable: " + ex.getMessage());
        }
    }

    private void openAssignmentDialog() {
        UserAccount currentUser = session.currentUser();
        if (currentUser == null || !"ADMIN".equals(currentUser.role)) {
            setStatus("Only the global administrator can assign complaints.");
            return;
        }
        int selectedRow = complaintTable.getSelectedRow();
        String initialComplaintId = selectedRow < 0 ? "" : complaintTableModel.getValueAt(
                complaintTable.convertRowIndexToModel(selectedRow), 0).toString();
        String enteredId = JOptionPane.showInputDialog(this, "Complaint ID to assign", initialComplaintId);
        if (enteredId == null) return;
        int complaintId;
        try {
            complaintId = Integer.parseInt(enteredId.trim());
        } catch (NumberFormatException ex) {
            setStatus("Enter a valid numerical complaint ID.");
            return;
        }
        List<String[]> staff;
        try {
            staff = complaintService.listStaffForComplaint(complaintId, currentUser);
        } catch (SQLException ex) {
            setStatus("Unable to load staff accounts: " + ex.getMessage());
            return;
        }
        if (staff.isEmpty()) {
            setStatus("No staff accounts are configured for this complaint's department.");
            return;
        }
        JComboBox<String> staffCombo = new JComboBox<>();
        for (String[] member : staff) {
            staffCombo.addItem(member[1] + " (" + member[2] + ") - " + member[3]);
        }
        int result = JOptionPane.showConfirmDialog(this, staffCombo, "Assign Complaint #" + complaintId,
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            int staffId = Integer.parseInt(staff.get(staffCombo.getSelectedIndex())[0]);
            complaintService.assignComplaint(complaintId, staffId, currentUser);
            refreshComplaintTable();
            setStatus("Complaint #" + complaintId + " assigned successfully.");
        } catch (SQLException | IllegalArgumentException ex) {
            setStatus("Assignment failed: " + ex.getMessage());
        }
    }

    private void openStatusDialog() {
        UserAccount currentUser = session.currentUser();
        if (currentUser == null
                || (!"ADMIN".equals(currentUser.role) && !"STAFF".equals(currentUser.role))) {
            setStatus("Only administrators or staff assigned to the complaint can update its status.");
            return;
        }
        JTextField complaintIdField = styleTextField(new JTextField());
        JComboBox<String> statusCombo = new JComboBox<>(new String[] {"IN_PROGRESS", "RESOLVED", "REJECTED"});
        JTextArea remarksArea = new JTextArea(4, 30);
        remarksArea.setFont(FONT_MAIN);
        remarksArea.setLineWrap(true);
        remarksArea.setWrapStyleWord(true);

        Object[] fields = {
                "Complaint ID", complaintIdField,
                "New Status", statusCombo,
                "Resolution Remarks", new JScrollPane(remarksArea)
        };
        int result = JOptionPane.showConfirmDialog(this, fields, "Update Complaint Status", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            int complaintId = Integer.parseInt(complaintIdField.getText().trim());
            String oldStatus = complaintService.updateStatus(
                    complaintId,
                    statusCombo.getSelectedItem().toString(),
                    currentUser,
                    remarksArea.getText().trim());
            setStatus("Complaint #" + complaintId + " changed from " + oldStatus + " to " + statusCombo.getSelectedItem());
            refreshComplaintTable();
        } catch (NumberFormatException ex) {
            setStatus("Please enter a valid complaint ID.");
        } catch (SQLException | IllegalArgumentException ex) {
            setStatus("Status update failed: " + ex.getMessage());
        }
    }

    private void runAgingScheduler() {
        try {
            UserAccount admin = session.requireRole("ADMIN");
            List<ComplaintService.SchedulingTask> tasks =
                    complaintService.loadOpenComplaintsForScheduling(admin);
            if (tasks.isEmpty()) {
                schedulerOutput.setText("No open complaints available for the aging scheduler.");
                return;
            }
            String input = OSModuleService.buildSchedulerInput(tasks);
            String output = osModule.runScheduler("priority", input);
            schedulerOutput.setText(output);
            SchedulerReport report = SchedulerReport.parse(output);
            showSchedulerVisualization(tasks, report);
            setStatus("Aging scheduler processed " + tasks.size() + " task(s).");
        } catch (SQLException | IOException | InterruptedException ex) {
            schedulerOutput.setText("Scheduling failed:\n" + ex.getMessage());
            setStatus("Scheduler error: " + ex.getMessage());
        }
    }

    private void runSynchronizationDemo() {
        Object[] options = {"Mutex (safe)", "Race condition (unsafe)", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "Two worker threads increment the same simulated task counter.\n"
                        + "The mutex option protects the shared counter; the race option does not.\n"
                        + "This demonstration does not change complaint data.",
                "Synchronization Demonstration", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice < 0 || choice == 2) return;

        String mode = choice == 0 ? "sync" : "race";
        String label = choice == 0 ? "MUTEX DEMO" : "RACE CONDITION DEMO";
        schedulerOutput.setText("Running " + label.toLowerCase() + "...\n");
        runSyncDemoButton.setEnabled(false);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return osModule.runDemo(mode);
            }

            @Override
            protected void done() {
                runSyncDemoButton.setEnabled(true);
                try {
                    schedulerOutput.setText(label + "\n"
                            + "This demo uses two threads to update a simulated shared counter. "
                            + "It does not modify Campus Voice complaints or database records.\n\n"
                            + get());
                    setStatus(choice == 0
                            ? "Mutex demo complete. Compare expected and actual counter values."
                            : "Race demo complete. Lost updates depend on thread timing.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    schedulerOutput.setText(label + " was interrupted.");
                    setStatus("Synchronization demo was interrupted.");
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    String message = cause == null ? ex.getMessage() : cause.getMessage();
                    schedulerOutput.setText(label + " failed:\n" + message);
                    setStatus("Synchronization demo failed: " + message);
                }
            }
        }.execute();
    }

    private void showSchedulerVisualization(List<ComplaintService.SchedulingTask> tasks,
                                            SchedulerReport report) {
        JDialog dialog = new JDialog(this, "Priority Scheduler - OS Simulation", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(900, 600));
        dialog.setSize(1050, 680);
        dialog.setLocationRelativeTo(this);

        JLabel progressLabel = new JLabel("Preparing scheduler...");
        progressLabel.setFont(FONT_BOLD);
        JTextArea queueArea = new JTextArea("Ready queue: -", 2, 70);
        queueArea.setFont(FONT_MAIN);
        queueArea.setLineWrap(true);
        queueArea.setWrapStyleWord(true);
        queueArea.setEditable(false);
        queueArea.setOpaque(false);

        SchedulerTimeline timeline = new SchedulerTimeline(report);
        JScrollPane timelineScroll = new JScrollPane(timeline);
        timelineScroll.setBorder(BorderFactory.createTitledBorder("CPU timeline (simulation time)"));
        timelineScroll.setPreferredSize(new Dimension(950, 135));

        DefaultTableModel model = new DefaultTableModel(
                new String[] {"Complaint", "CPU Start", "Completion", "Waiting", "Turnaround", "State"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (SchedulerMetrics metrics : report.metrics.values()) {
            model.addRow(new Object[] {"#" + metrics.complaintId, metrics.start,
                    metrics.completion, metrics.waiting, metrics.turnaround, "NEW"});
        }
        JTable metricsTable = new JTable(model);
        metricsTable.setRowHeight(26);
        JScrollPane metricsScroll = new JScrollPane(metricsTable);
        metricsScroll.setBorder(BorderFactory.createTitledBorder("Complaint processing details"));

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel summary = new JPanel(new BorderLayout(4, 4));
        summary.add(progressLabel, BorderLayout.NORTH);
        summary.add(queueArea, BorderLayout.CENTER);
        content.add(summary, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(timelineScroll, BorderLayout.NORTH);
        center.add(metricsScroll, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);
        JLabel note = new JLabel("This is a non-preemptive OS scheduling simulation; it does not run or control real operating-system processes.");
        note.setForeground(TEXT_MUTED);
        content.add(note, BorderLayout.SOUTH);
        dialog.setContentPane(content);

        List<SchedulerMetrics> orderedMetrics = new ArrayList<>(report.metrics.values());
        orderedMetrics.sort(Comparator.comparingInt(metrics -> metrics.start));
        Map<Integer, ComplaintService.SchedulingTask> taskByPid = new HashMap<>();
        for (ComplaintService.SchedulingTask task : tasks) {
            taskByPid.put(task.pid, task);
        }

        final int[] slotIndex = {0};
        Timer playback = new Timer(1100, event -> {
            if (slotIndex[0] >= report.slots.size()) {
                progressLabel.setText("Simulation complete at time " + report.endTime + ".");
                queueArea.setText("Ready queue: empty");
                timeline.setCurrentSlot(report.slots.size());
                timelineScroll.getViewport().setViewPosition(new Point(
                        Math.max(0, report.slots.size() * SchedulerTimeline.SLOT_WIDTH
                                - timelineScroll.getViewport().getWidth()), 0));
                updateSchedulerStates(model, orderedMetrics, null, report.endTime);
                ((Timer) event.getSource()).stop();
                return;
            }
            SchedulerSlot slot = report.slots.get(slotIndex[0]);
            ComplaintService.SchedulingTask runningTask = taskByPid.get(slot.pid);
            String runningText = slot.pid < 0 ? "CPU idle"
                    : "CPU running complaint #" + (runningTask == null ? slot.pid : runningTask.complaintId)
                            + " (time " + slot.start + " to " + slot.end + ")";
            progressLabel.setText(runningText);

            List<ComplaintService.SchedulingTask> ready = new ArrayList<>();
            for (ComplaintService.SchedulingTask task : tasks) {
                SchedulerMetrics metrics = report.metrics.get(task.pid);
                if (metrics != null && task.pid != slot.pid
                        && task.arrivalDay <= slot.start && metrics.start >= slot.start) {
                    ready.add(task);
                }
            }
            ready.sort(Comparator
                    .comparingInt((ComplaintService.SchedulingTask task) ->
                            effectivePriorityAt(task, slot.start))
                    .thenComparingInt(task -> task.arrivalDay)
                    .thenComparingInt(task -> task.pid));
            if (ready.isEmpty()) {
                queueArea.setText("Ready queue: empty");
            } else {
                StringBuilder queue = new StringBuilder("Ready queue: ");
                for (ComplaintService.SchedulingTask task : ready) {
                    if (queue.charAt(queue.length() - 1) != ' ') queue.append("  |  ");
                    queue.append("C").append(task.complaintId)
                            .append(" (priority ")
                            .append(priorityName(effectivePriorityAt(task, slot.start)))
                            .append(")");
                }
                queueArea.setText(queue.toString());
            }
            timeline.setCurrentSlot(slotIndex[0]);
            int scrollX = Math.max(0, slotIndex[0] * SchedulerTimeline.SLOT_WIDTH
                    - timelineScroll.getViewport().getWidth() + SchedulerTimeline.SLOT_WIDTH);
            timelineScroll.getViewport().setViewPosition(new Point(scrollX, 0));
            updateSchedulerStates(model, orderedMetrics, slot, slot.start);
            slotIndex[0]++;
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                playback.stop();
            }
        });
        playback.start();
        dialog.setVisible(true);
    }

    private static void updateSchedulerStates(DefaultTableModel model,
                                              List<SchedulerMetrics> metrics,
                                              SchedulerSlot activeSlot, int time) {
        for (int row = 0; row < metrics.size(); row++) {
            SchedulerMetrics item = metrics.get(row);
            String state = item.completion <= time ? "COMPLETED"
                    : activeSlot != null && item.pid == activeSlot.pid ? "RUNNING"
                    : item.arrival <= time ? "READY" : "NEW";
            model.setValueAt(state, row, 5);
        }
    }

    private static int effectivePriorityAt(ComplaintService.SchedulingTask task, int time) {
        int waitingDays = task.agingDays + time - task.arrivalDay;
        return Math.max(1, task.priorityNumber - Math.max(0, waitingDays / 3));
    }

    private static String priorityName(int priority) {
        switch (priority) {
            case 1: return "URGENT";
            case 2: return "HIGH";
            case 3: return "MEDIUM";
            default: return "LOW";
        }
    }

    private static final class SchedulerSlot {
        final int pid;
        final int start;
        final int end;

        SchedulerSlot(int pid, int start, int end) {
            this.pid = pid;
            this.start = start;
            this.end = end;
        }
    }

    private static final class SchedulerMetrics {
        final int pid;
        final int complaintId;
        final int arrival;
        final int start;
        final int completion;
        final int waiting;
        final int turnaround;

        SchedulerMetrics(int pid, int complaintId, int arrival, int start, int completion,
                         int turnaround, int waiting) {
            this.pid = pid;
            this.complaintId = complaintId;
            this.arrival = arrival;
            this.start = start;
            this.completion = completion;
            this.turnaround = turnaround;
            this.waiting = waiting;
        }
    }

    private static final class SchedulerReport {
        private static final Pattern PID_PATTERN = Pattern.compile("P(\\d+)");
        private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
        private static final Pattern METRICS_PATTERN = Pattern.compile(
                "^P(\\d+)\\s+C(\\d+)\\s+(\\d+)\\s+\\d+\\s+\\d+\\s+\\d+\\s+(-?\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+\\S+\\s*$");
        final List<SchedulerSlot> slots = new ArrayList<>();
        final Map<Integer, SchedulerMetrics> metrics = new HashMap<>();
        int endTime;

        static SchedulerReport parse(String output) throws IOException {
            SchedulerReport report = new SchedulerReport();
            String[] lines = output.split("\\R");
            int header = -1;
            int gantt = -1;
            for (int i = 0; i < lines.length; i++) {
                if (header < 0 && lines[i].contains("PID") && lines[i].contains("Complaint")
                        && lines[i].contains("Completion")) {
                    header = i;
                }
                if (lines[i].trim().equals("Gantt chart:")) {
                    gantt = i;
                    break;
                }
            }
            if (header < 0 || gantt < 0 || gantt + 2 >= lines.length) {
                throw new IOException("The OS scheduler returned output in an unrecognized format.");
            }
            Matcher rowMatcher = METRICS_PATTERN.matcher("");
            for (int i = header + 1; i < gantt; i++) {
                rowMatcher.reset(lines[i]);
                if (rowMatcher.matches()) {
                    int pid = Integer.parseInt(rowMatcher.group(1));
                    report.metrics.put(pid, new SchedulerMetrics(pid,
                            Integer.parseInt(rowMatcher.group(2)),
                            Integer.parseInt(rowMatcher.group(3)),
                            Integer.parseInt(rowMatcher.group(4)),
                            Integer.parseInt(rowMatcher.group(5)),
                            Integer.parseInt(rowMatcher.group(6)),
                            Integer.parseInt(rowMatcher.group(7))));
                }
            }
            Matcher slotMatcher = PID_PATTERN.matcher("");
            Matcher labelMatcher = Pattern.compile("\\|\\s*(P\\d+|IDLE)\\s*").matcher(lines[gantt + 1]);
            while (labelMatcher.find()) {
                String label = labelMatcher.group(1);
                int pid = -1;
                if (!"IDLE".equals(label)) {
                    slotMatcher.reset(label);
                    if (!slotMatcher.matches()) continue;
                    pid = Integer.parseInt(slotMatcher.group(1));
                }
                report.slots.add(new SchedulerSlot(pid, 0, 0));
            }
            List<Integer> times = new ArrayList<>();
            Matcher timeMatcher = NUMBER_PATTERN.matcher(lines[gantt + 2]);
            while (timeMatcher.find()) times.add(Integer.parseInt(timeMatcher.group()));
            if (report.slots.isEmpty() || times.size() != report.slots.size() + 1) {
                throw new IOException("The OS scheduler returned an invalid CPU timeline.");
            }
            for (int i = 0; i < report.slots.size(); i++) {
                SchedulerSlot slot = report.slots.get(i);
                report.slots.set(i, new SchedulerSlot(slot.pid, times.get(i), times.get(i + 1)));
            }
            report.endTime = times.get(times.size() - 1);
            if (report.metrics.isEmpty()) {
                throw new IOException("The OS scheduler did not return complaint processing metrics.");
            }
            return report;
        }
    }

    private static final class SchedulerTimeline extends JPanel {
        private static final long serialVersionUID = 1L;
        private final SchedulerReport report;
        private int currentSlot = -1;
        static final int SLOT_WIDTH = 130;

        SchedulerTimeline(SchedulerReport report) {
            this.report = report;
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(Math.max(700, report.slots.size() * SLOT_WIDTH + 30), 90));
        }

        void setCurrentSlot(int slot) {
            currentSlot = slot;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setFont(FONT_MAIN);
            int x = 14;
            int y = 18;
            for (int i = 0; i < report.slots.size(); i++) {
                SchedulerSlot slot = report.slots.get(i);
                boolean active = i == currentSlot;
                g.setColor(active ? new Color(199, 210, 254) : new Color(238, 242, 255));
                g.fillRoundRect(x, y, SLOT_WIDTH - 8, 34, 8, 8);
                g.setColor(active ? PRIMARY_COLOR : BORDER_COLOR);
                g.drawRoundRect(x, y, SLOT_WIDTH - 8, 34, 8, 8);
                String label = slot.pid < 0 ? "IDLE" : "P" + slot.pid;
                g.setColor(TEXT_MAIN);
                g.drawString(label, x + 12, y + 15);
                g.setColor(TEXT_MUTED);
                g.drawString(slot.start + " - " + slot.end, x + 12, y + 29);
                x += SLOT_WIDTH;
            }
            g.dispose();
        }
    }

    // Custom cell renderer to draw priority & status badges
    private static class PillBadgeRenderer extends DefaultTableCellRenderer {
        private String textVal = "";
        private Color bgColor = Color.LIGHT_GRAY;
        private Color fgColor = Color.DARK_GRAY;

        public PillBadgeRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            textVal = (value != null) ? value.toString() : "";
            determinePillColors(textVal);

            // Match background to table row selection
            setBackground(isSelected ? table.getSelectionBackground() : CARD_BG);
            return this;
        }

        private void determinePillColors(String val) {
            switch (val) {
                case "URGENT":
                    bgColor = new Color(254, 226, 226); fgColor = new Color(185, 28, 28); break;
                case "HIGH":
                    bgColor = new Color(254, 243, 199); fgColor = new Color(180, 83, 9); break;
                case "MEDIUM":
                    bgColor = new Color(224, 242, 254); fgColor = new Color(3, 105, 161); break;
                case "LOW":
                    bgColor = new Color(241, 245, 249); fgColor = new Color(71, 85, 105); break;
                case "RESOLVED":
                    bgColor = new Color(209, 250, 229); fgColor = new Color(4, 120, 87); break;
                case "IN_PROGRESS":
                    bgColor = new Color(254, 243, 199); fgColor = new Color(180, 83, 9); break;
                case "REJECTED":
                    bgColor = new Color(243, 244, 246); fgColor = new Color(107, 114, 128); break;
                default:
                    bgColor = new Color(241, 245, 249); fgColor = TEXT_MAIN;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw table background
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Draw pill
            if (!textVal.isEmpty()) {
                g2.setFont(FONT_BOLD);
                int textWidth = g2.getFontMetrics().stringWidth(textVal);
                int pillWidth = textWidth + 18;
                int pillHeight = 22;
                int x = (getWidth() - pillWidth) / 2;
                int y = (getHeight() - pillHeight) / 2;

                g2.setColor(bgColor);
                g2.fillRoundRect(x, y, pillWidth, pillHeight, 12, 12);

                g2.setColor(fgColor);
                g2.drawString(textVal, x + 9, y + 15);
            }
            g2.dispose();
        }
    }
}