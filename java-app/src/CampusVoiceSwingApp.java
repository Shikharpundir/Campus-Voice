import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

public class CampusVoiceSwingApp extends JFrame {
    private static final long serialVersionUID = 1L;

    // Modern SaaS Color Palette (Slate & Indigo)
    private static final Color BG_CANVAS = new Color(241, 245, 249);         // Slate-100
    private static final Color CARD_BG = Color.WHITE;
    private static final Color SIDEBAR_BG = new Color(15, 23, 42);           // Slate-900
    private static final Color SIDEBAR_HOVER = new Color(30, 41, 59);        // Slate-800
    private static final Color SIDEBAR_TEXT = new Color(226, 232, 240);      // Slate-200
    private static final Color SIDEBAR_MUTED = new Color(100, 116, 139);     // Slate-500
    private static final Color BORDER_COLOR = new Color(226, 232, 240);      // Slate-200

    private static final Color PRIMARY_COLOR = new Color(79, 70, 229);       // Indigo-600
    private static final Color PRIMARY_HOVER = new Color(67, 56, 202);       // Indigo-700
    private static final Color PRIMARY_SOFT = new Color(238, 242, 255);      // Indigo-50

    private static final Color TEXT_MAIN = new Color(15, 23, 42);            // Slate-900
    private static final Color TEXT_SECONDARY = new Color(71, 85, 105);      // Slate-600
    private static final Color TEXT_MUTED = new Color(100, 116, 139);        // Slate-500

    private static final Color ACCENT_SUCCESS = new Color(16, 185, 129);     // Emerald-500
    private static final Color ACCENT_WARNING = new Color(245, 158, 11);     // Amber-500
    private static final Color ACCENT_DANGER = new Color(239, 68, 68);       // Red-500

    // Typography
    private static final Font FONT_HERO = new Font("Segoe UI", Font.BOLD, 28);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_KPI = new Font("Segoe UI", Font.BOLD, 24);

    private final ComplaintService complaintService = new ComplaintService();
    private final StaffReportService staffReportService = new StaffReportService();
    private final OSModuleService osModule = new OSModuleService();

    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel loginPanel = new JPanel(new GridBagLayout());
    private final JPanel dashboardPanel = new JPanel(new BorderLayout());

    // Table & Sorter
    private final DefaultTableModel complaintTableModel = new DefaultTableModel(
            new String[] {"ID", "Title", "Priority", "Effective Priority", "Status", "Department",
                    "Student", "Assigned / Routed To", "Age (days)", "Upvotes"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

    };
    private final JTable complaintTable = new JTable(complaintTableModel);
    private TableRowSorter<DefaultTableModel> complaintSorter;

    // Filter Controls
    private final JTextField searchField = new JTextField(18);
    private final JComboBox<String> statusFilterCombo = new JComboBox<>(
            new String[] {"All Statuses", "OPEN", "IN_PROGRESS", "RESOLVED", "REJECTED"});

    // KPI Labels
    private final JLabel kpiTotalVal = new JLabel("0");
    private final JLabel kpiActiveVal = new JLabel("0");
    private final JLabel kpiResolvedVal = new JLabel("0");
    private final JLabel kpiUrgentVal = new JLabel("0");

    // Live Preview & Console
    private final JTextArea quickPreviewArea = new JTextArea();
    private final JTextArea schedulerOutput = new JTextArea();

    // Header & Status Labels
    private final JLabel welcomeLabel = new JLabel("Welcome");
    private final JLabel roleBadgeLabel = new JLabel("GUEST");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel loginStatusLabel = new JLabel(" ");

    // Sidebar Navigation Buttons
    private JButton navSubmitComplaintBtn;
    private JButton navReportStaffBtn;
    private JButton navMyStaffReportsBtn;
    private JButton navViewStaffReportsBtn;
    private JButton navCreateStaffBtn;
    private JButton navListStaffBtn;
    private JButton navRunSchedulerBtn;
    private JButton navRunSyncDemoBtn;
    private JLabel sidebarAdminHeader;
    private JLabel sidebarStudentHeader;

    // Contextual Table Action Buttons
    private JButton updateStatusButton;
    private JButton rateResolutionButton;
    private JButton assignStaffButton;
    private JButton deleteComplaintButton;
    private JButton upvoteComplaintButton;

    private final UserSession session = new UserSession();
    private File currentPhoto;
    private LocalDate lastAgeRefreshDate = LocalDate.now();

    public CampusVoiceSwingApp() {
        super("Campus Voice — Grievance & Resolution Portal");
        setupModernSystemLaF();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1280, 820));
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

    // =========================================================================
    // LOGIN SCREEN (Split Modern Card)
    // =========================================================================
    private void buildLoginScreen() {
        loginPanel.setBackground(BG_CANVAS);

        RoundedCardPanel mainCard = new RoundedCardPanel(20, CARD_BG, BORDER_COLOR);
        mainCard.setLayout(new GridLayout(1, 2));
        mainCard.setPreferredSize(new Dimension(880, 520));

        // Left Brand Hero Panel
        JPanel brandPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, PRIMARY_COLOR, getWidth(), getHeight(), new Color(49, 46, 129));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth() + 20, getHeight(), 20, 20);
                g2.dispose();
            }
        };
        brandPanel.setOpaque(false);
        brandPanel.setLayout(new GridBagLayout());
        brandPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

        GridBagConstraints bgbc = new GridBagConstraints();
        bgbc.gridx = 0; bgbc.gridy = 0;
        bgbc.anchor = GridBagConstraints.WEST;
        bgbc.fill = GridBagConstraints.HORIZONTAL;
        bgbc.weightx = 1.0;

        JLabel badge = new JLabel("CAMPUS VOICE PORTAL");
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setForeground(new Color(199, 210, 254));
        brandPanel.add(badge, bgbc);

        bgbc.gridy = 1;
        bgbc.insets = new Insets(12, 0, 16, 0);
        JLabel heroTitle = new JLabel("<html>Transparent Campus<br>Issue Resolution.</html>");
        heroTitle.setFont(FONT_HERO);
        heroTitle.setForeground(Color.WHITE);
        brandPanel.add(heroTitle, bgbc);

        bgbc.gridy = 2;
        bgbc.insets = new Insets(0, 0, 28, 0);
        JLabel heroSub = new JLabel("<html>Submit grievances, track department progress in real time, and ensure accountability across campus administration.</html>");
        heroSub.setFont(FONT_MAIN);
        heroSub.setForeground(new Color(224, 231, 255));
        brandPanel.add(heroSub, bgbc);

        bgbc.gridy = 3;
        bgbc.insets = new Insets(8, 0, 0, 0);
        JLabel featureList = new JLabel("<html>"
                + "&#10003;&nbsp;&nbsp;Priority Aging &amp; SLA Tracking<br><br>"
                + "&#10003;&nbsp;&nbsp;Direct Department Routing<br><br>"
                + "&#10003;&nbsp;&nbsp;Verified Resolution Feedback"
                + "</html>");
        featureList.setFont(FONT_BOLD);
        featureList.setForeground(Color.WHITE);
        brandPanel.add(featureList, bgbc);

        // Right Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(CARD_BG);
        formPanel.setBorder(new EmptyBorder(36, 44, 36, 44));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        JLabel title = new JLabel("Sign in to your account");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_MAIN);
        gbc.gridy = 0;
        formPanel.add(title, gbc);

        JLabel subtitle = new JLabel("Enter your institutional credentials below");
        subtitle.setFont(FONT_MAIN);
        subtitle.setForeground(TEXT_MUTED);
        gbc.gridy = 1;
        gbc.insets = new Insets(2, 0, 20, 0);
        formPanel.add(subtitle, gbc);

        gbc.insets = new Insets(4, 0, 4, 0);
        JLabel emailLabel = new JLabel("Institutional Email");
        emailLabel.setFont(FONT_BOLD);
        emailLabel.setForeground(TEXT_MAIN);
        gbc.gridy = 2;
        formPanel.add(emailLabel, gbc);

        JTextField emailField = styleTextField(new JTextField(22));
        gbc.gridy = 3;
        formPanel.add(emailField, gbc);

        gbc.insets = new Insets(10, 0, 4, 0);
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setFont(FONT_BOLD);
        passwordLabel.setForeground(TEXT_MAIN);
        gbc.gridy = 4;
        formPanel.add(passwordLabel, gbc);

        gbc.insets = new Insets(4, 0, 4, 0);
        JPasswordField passwordField = (JPasswordField) styleTextField(new JPasswordField(22));
        gbc.gridy = 5;
        formPanel.add(passwordField, gbc);

        JButton signIn = createStyledButton("Sign In", PRIMARY_COLOR, Color.WHITE);
        JButton registerStudent = createStyledButton("Create Student Account", BG_CANVAS, TEXT_MAIN);
        JButton firstTimeStaff = createStyledButton("First-Time Staff Activation", BG_CANVAS, TEXT_SECONDARY);

        Runnable doLogin = () -> {
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
        };

        signIn.addActionListener(e -> doLogin.run());
        passwordField.addActionListener(e -> doLogin.run());
        registerStudent.addActionListener(e -> openStudentRegistrationDialog());
        firstTimeStaff.addActionListener(e -> openFirstTimeStaffSetup());

        gbc.gridy = 6;
        gbc.insets = new Insets(18, 0, 8, 0);
        formPanel.add(signIn, gbc);

        JPanel secondaryBtns = new JPanel(new GridLayout(1, 2, 8, 0));
        secondaryBtns.setOpaque(false);
        secondaryBtns.add(registerStudent);
        secondaryBtns.add(firstTimeStaff);
        gbc.gridy = 7;
        gbc.insets = new Insets(0, 0, 8, 0);
        formPanel.add(secondaryBtns, gbc);

        loginStatusLabel.setForeground(ACCENT_DANGER);
        loginStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginStatusLabel.setFont(FONT_SMALL);
        gbc.gridy = 8;
        formPanel.add(loginStatusLabel, gbc);

        mainCard.add(brandPanel);
        mainCard.add(formPanel);
        loginPanel.add(mainCard);
        cards.add(loginPanel, "login");
    }

    // =========================================================================
    // STRUCTURED SAAS DASHBOARD (Sidebar + Header + KPIs + Workspace)
    // =========================================================================
    private void buildDashboard() {
        dashboardPanel.setBackground(BG_CANVAS);

        // 1. Left Navigation Sidebar
        JPanel sidebar = buildSidebar();
        dashboardPanel.add(sidebar, BorderLayout.WEST);

        // 2. Right Main Container (Header + Workspace + Footer)
        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.setBackground(BG_CANVAS);

        // Top App Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(14, 24, 14, 24)
        ));

        JPanel titleGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleGroup.setOpaque(false);
        JLabel dashboardTitle = new JLabel("Complaint Management");
        dashboardTitle.setFont(FONT_TITLE);
        dashboardTitle.setForeground(TEXT_MAIN);

        roleBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        roleBadgeLabel.setForeground(PRIMARY_COLOR);
        roleBadgeLabel.setBackground(PRIMARY_SOFT);
        roleBadgeLabel.setOpaque(true);
        roleBadgeLabel.setBorder(new EmptyBorder(4, 10, 4, 10));

        titleGroup.add(dashboardTitle);
        titleGroup.add(roleBadgeLabel);

        JPanel userActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        userActions.setOpaque(false);
        welcomeLabel.setFont(FONT_BOLD);
        welcomeLabel.setForeground(TEXT_SECONDARY);
        JButton logout = createStyledButton("Sign Out", BG_CANVAS, TEXT_MAIN);
        logout.addActionListener(e -> {
            session.signOut();
            showLoginScreen();
        });
        userActions.add(welcomeLabel);
        userActions.add(logout);

        header.add(titleGroup, BorderLayout.WEST);
        header.add(userActions, BorderLayout.EAST);
        rightContainer.add(header, BorderLayout.NORTH);

        // Main Scrollable/Structured Workspace
        JPanel workspace = new JPanel(new BorderLayout(0, 16));
        workspace.setBackground(BG_CANVAS);
        workspace.setBorder(new EmptyBorder(18, 24, 16, 24));

        // Top KPI Row
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 16, 0));
        kpiGrid.setOpaque(false);
        kpiGrid.add(createKpiCard("Total Complaints", kpiTotalVal, PRIMARY_COLOR));
        kpiGrid.add(createKpiCard("Open / In Progress", kpiActiveVal, ACCENT_WARNING));
        kpiGrid.add(createKpiCard("Resolved", kpiResolvedVal, ACCENT_SUCCESS));
        kpiGrid.add(createKpiCard("Urgent Priority", kpiUrgentVal, ACCENT_DANGER));
        workspace.add(kpiGrid, BorderLayout.NORTH);

        // Center Card: Filter Bar + Action Strip + Table + Live Inspector
        RoundedCardPanel tableCard = new RoundedCardPanel(12, CARD_BG, BORDER_COLOR);
        tableCard.setLayout(new BorderLayout());

        // Toolbar inside Table Card (Search/Filter on left, Contextual Actions on right)
        JPanel tableToolbar = new JPanel(new BorderLayout(12, 8));
        tableToolbar.setOpaque(false);
        tableToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JPanel filterGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterGroup.setOpaque(false);
        styleTextField(searchField);
        searchField.setToolTipText("Search by report ID, type/title, department, or student...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyTableFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyTableFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyTableFilters(); }
        });

        statusFilterCombo.setFont(FONT_MAIN);
        statusFilterCombo.setBackground(CARD_BG);
        statusFilterCombo.addActionListener(e -> applyTableFilters());

        JButton refreshBtn = createStyledButton("Refresh", BG_CANVAS, TEXT_MAIN);
        refreshBtn.addActionListener(e -> refreshComplaintTable());

        filterGroup.add(new JLabel("Search:"));
        filterGroup.add(searchField);
        filterGroup.add(statusFilterCombo);
        filterGroup.add(refreshBtn);

        JPanel rowActionGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rowActionGroup.setOpaque(false);

        JButton detailsBtn = createStyledButton("Full Details", BG_CANVAS, TEXT_MAIN);
        detailsBtn.addActionListener(e -> showSelectedComplaintDetails());

        assignStaffButton = createStyledButton("Assign Staff", PRIMARY_SOFT, PRIMARY_COLOR);
        assignStaffButton.addActionListener(e -> openAssignmentDialog());

        updateStatusButton = createStyledButton("Update Status", PRIMARY_COLOR, Color.WHITE);
        updateStatusButton.addActionListener(e -> openStatusDialog());

        rateResolutionButton = createStyledButton("Rate Resolution", PRIMARY_SOFT, PRIMARY_COLOR);
        rateResolutionButton.addActionListener(e -> openFeedbackDialog());

        upvoteComplaintButton = createStyledButton("Upvote Issue", PRIMARY_SOFT, PRIMARY_COLOR);
        upvoteComplaintButton.addActionListener(e -> upvoteSelectedComplaint());

        deleteComplaintButton = createStyledButton("Delete", new Color(254, 226, 226), ACCENT_DANGER);
        deleteComplaintButton.addActionListener(e -> deleteSelectedComplaint());

        rowActionGroup.add(detailsBtn);
        rowActionGroup.add(assignStaffButton);
        rowActionGroup.add(updateStatusButton);
        rowActionGroup.add(rateResolutionButton);
        rowActionGroup.add(upvoteComplaintButton);
        rowActionGroup.add(deleteComplaintButton);

        tableToolbar.add(filterGroup, BorderLayout.WEST);
        tableToolbar.add(rowActionGroup, BorderLayout.EAST);
        tableCard.add(tableToolbar, BorderLayout.NORTH);

        // Configure Table
        configureComplaintTable();
        JScrollPane tableScroll = new JScrollPane(complaintTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getViewport().setBackground(CARD_BG);

        // Right Quick-Inspector Panel inside Table Card
        JPanel inspectorPanel = new JPanel(new BorderLayout(0, 8));
        inspectorPanel.setBackground(new Color(248, 250, 252));
        inspectorPanel.setPreferredSize(new Dimension(290, 0));
        inspectorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COLOR),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel inspectorTitle = new JLabel("Quick Inspector");
        inspectorTitle.setFont(FONT_SUBTITLE);
        inspectorTitle.setForeground(TEXT_MAIN);

        quickPreviewArea.setEditable(false);
        quickPreviewArea.setLineWrap(true);
        quickPreviewArea.setWrapStyleWord(true);
        quickPreviewArea.setFont(FONT_MAIN);
        quickPreviewArea.setForeground(TEXT_SECONDARY);
        quickPreviewArea.setBackground(new Color(248, 250, 252));
        quickPreviewArea.setText("Select any complaint row in the queue to inspect its summary, priority aging, and assignment status.");

        JScrollPane previewScroll = new JScrollPane(quickPreviewArea);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewScroll.getViewport().setBackground(new Color(248, 250, 252));

        inspectorPanel.add(inspectorTitle, BorderLayout.NORTH);
        inspectorPanel.add(previewScroll, BorderLayout.CENTER);

        JSplitPane tableSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, inspectorPanel);
        tableSplit.setResizeWeight(0.76);
        tableSplit.setDividerSize(1);
        tableSplit.setBorder(null);
        tableCard.add(tableSplit, BorderLayout.CENTER);

        // Bottom OS Module Console Card
        RoundedCardPanel consoleCard = new RoundedCardPanel(12, new Color(15, 23, 42), new Color(30, 41, 59));
        consoleCard.setLayout(new BorderLayout());
        consoleCard.setPreferredSize(new Dimension(0, 165));

        JPanel consoleHeader = new JPanel(new BorderLayout());
        consoleHeader.setOpaque(false);
        consoleHeader.setBorder(new EmptyBorder(8, 14, 4, 14));
        JLabel consoleTitle = new JLabel("OS SCHEDULER & CONCURRENCY MONITOR");
        consoleTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        consoleTitle.setForeground(new Color(148, 163, 184));
        consoleHeader.add(consoleTitle, BorderLayout.WEST);

        schedulerOutput.setEditable(false);
        schedulerOutput.setFont(new Font("Consolas", Font.PLAIN, 12));
        schedulerOutput.setBackground(new Color(15, 23, 42));
        schedulerOutput.setForeground(new Color(56, 189, 248));
        schedulerOutput.setBorder(new EmptyBorder(4, 14, 10, 14));
        schedulerOutput.setText("System idle. Ready to run Priority Aging Scheduler or Mutex/Race Concurrency Demo.");

        JScrollPane consoleScroll = new JScrollPane(schedulerOutput);
        consoleScroll.setBorder(BorderFactory.createEmptyBorder());
        consoleCard.add(consoleHeader, BorderLayout.NORTH);
        consoleCard.add(consoleScroll, BorderLayout.CENTER);

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, consoleCard);
        verticalSplit.setResizeWeight(0.75);
        verticalSplit.setDividerSize(8);
        verticalSplit.setOpaque(false);
        verticalSplit.setBorder(null);

        workspace.add(verticalSplit, BorderLayout.CENTER);
        rightContainer.add(workspace, BorderLayout.CENTER);

        // Footer Status Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(CARD_BG);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(8, 24, 8, 24)
        ));
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(FONT_SMALL);
        statusBar.add(statusLabel, BorderLayout.WEST);
        rightContainer.add(statusBar, BorderLayout.SOUTH);

        dashboardPanel.add(rightContainer, BorderLayout.CENTER);
        cards.add(dashboardPanel, "dashboard");
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(245, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(20, 14, 20, 14));

        // Brand Logo Header
        JLabel appLogo = new JLabel("Campus Voice");
        appLogo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        appLogo.setForeground(Color.WHITE);
        appLogo.setAlignmentX(Component.LEFT_ALIGNMENT);
        appLogo.setBorder(new EmptyBorder(0, 10, 4, 10));

        JLabel appTag = new JLabel("Grievance OS & Portal");
        appTag.setFont(FONT_SMALL);
        appTag.setForeground(SIDEBAR_MUTED);
        appTag.setAlignmentX(Component.LEFT_ALIGNMENT);
        appTag.setBorder(new EmptyBorder(0, 10, 20, 10));

        sidebar.add(appLogo);
        sidebar.add(appTag);

        // Student Actions Section
        sidebarStudentHeader = createSidebarSectionLabel("STUDENT ACTIONS");
        sidebar.add(sidebarStudentHeader);

        navSubmitComplaintBtn = createSidebarButton("+  New Complaint", true);
        navSubmitComplaintBtn.addActionListener(e -> openComplaintDialog());
        sidebar.add(navSubmitComplaintBtn);
        sidebar.add(Box.createVerticalStrut(6));

        navReportStaffBtn = createSidebarButton("!  Report Staff Misconduct", false);
        navReportStaffBtn.addActionListener(e -> openStaffReportDialog());
        sidebar.add(navReportStaffBtn);
        sidebar.add(Box.createVerticalStrut(6));

        navMyStaffReportsBtn = createSidebarButton("My Staff Reports", false);
        navMyStaffReportsBtn.addActionListener(e -> showStudentStaffReports());
        sidebar.add(navMyStaffReportsBtn);
        sidebar.add(Box.createVerticalStrut(16));

        // Admin Governance Section
        sidebarAdminHeader = createSidebarSectionLabel("ADMINISTRATION");
        sidebar.add(sidebarAdminHeader);

        navViewStaffReportsBtn = createSidebarButton("Review Staff Reports", false);
        navViewStaffReportsBtn.addActionListener(e -> showAdminStaffReports());
        sidebar.add(navViewStaffReportsBtn);
        sidebar.add(Box.createVerticalStrut(6));

        navCreateStaffBtn = createSidebarButton("+  Add Department Staff", false);
        navCreateStaffBtn.addActionListener(e -> openStaffRegistrationDialog());
        sidebar.add(navCreateStaffBtn);
        sidebar.add(Box.createVerticalStrut(6));

        navListStaffBtn = createSidebarButton("Staff Directory", false);
        navListStaffBtn.addActionListener(e -> showStaffAccounts());
        sidebar.add(navListStaffBtn);
        sidebar.add(Box.createVerticalStrut(16));

        // OS Kernel Simulation Section
        sidebar.add(createSidebarSectionLabel("OS KERNEL TOOLS"));

        navRunSchedulerBtn = createSidebarButton("Run Aging Scheduler", false);
        navRunSchedulerBtn.addActionListener(e -> runAgingScheduler());
        sidebar.add(navRunSchedulerBtn);
        sidebar.add(Box.createVerticalStrut(6));

        navRunSyncDemoBtn = createSidebarButton("Mutex / Buffer Demo", false);
        navRunSyncDemoBtn.addActionListener(e -> runSynchronizationDemo());
        sidebar.add(navRunSyncDemoBtn);

        sidebar.add(Box.createVerticalGlue());

        JLabel versionLabel = new JLabel("v2.0 • Enterprise Swing UI");
        versionLabel.setFont(FONT_SMALL);
        versionLabel.setForeground(SIDEBAR_MUTED);
        versionLabel.setBorder(new EmptyBorder(0, 10, 0, 10));
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(versionLabel);

        return sidebar;
    }

    private JLabel createSidebarSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(SIDEBAR_MUTED);
        label.setBorder(new EmptyBorder(6, 10, 6, 10));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton createSidebarButton(String text, boolean primaryAccent) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (primaryAccent) {
                    g2.setColor(getModel().isRollover() ? PRIMARY_HOVER : PRIMARY_COLOR);
                } else {
                    g2.setColor(getModel().isRollover() ? SIDEBAR_HOVER : SIDEBAR_BG);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(FONT_BOLD);
        btn.setForeground(primaryAccent ? Color.WHITE : SIDEBAR_TEXT);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(220, 38));
        btn.setPreferredSize(new Dimension(220, 38));
        btn.setBorder(new EmptyBorder(8, 12, 8, 12));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        return btn;
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        RoundedCardPanel card = new RoundedCardPanel(12, CARD_BG, BORDER_COLOR);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_BOLD);
        titleLbl.setForeground(TEXT_MUTED);

        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentColor);
                g2.fillOval(0, 2, 10, 10);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(12, 14));

        topRow.add(titleLbl, BorderLayout.WEST);
        topRow.add(dot, BorderLayout.EAST);

        valueLabel.setFont(FONT_KPI);
        valueLabel.setForeground(TEXT_MAIN);

        card.add(topRow, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void configureComplaintTable() {
        complaintTable.setRowHeight(40);
        complaintTable.setFont(FONT_MAIN);
        complaintTable.setShowVerticalLines(false);
        complaintTable.setGridColor(BORDER_COLOR);
        complaintTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        complaintTable.setSelectionBackground(PRIMARY_SOFT);
        complaintTable.setSelectionForeground(TEXT_MAIN);
        complaintTable.setFillsViewportHeight(true);

        complaintSorter = new TableRowSorter<>(complaintTableModel);
        complaintSorter.setComparator(3, (left, right) ->
                Integer.compare(prioritySortRank(left), prioritySortRank(right)));
        complaintSorter.setComparator(8, (left, right) ->
                Integer.compare(Integer.parseInt(left.toString()), Integer.parseInt(right.toString())));
        complaintSorter.setSortKeys(Arrays.asList(
                new javax.swing.RowSorter.SortKey(3, SortOrder.ASCENDING),
                new javax.swing.RowSorter.SortKey(8, SortOrder.DESCENDING)));
        complaintTable.setRowSorter(complaintSorter);

        JTableHeader tableHeader = complaintTable.getTableHeader();
        tableHeader.setBackground(new Color(248, 250, 252));
        tableHeader.setForeground(TEXT_SECONDARY);
        tableHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableHeader.setPreferredSize(new Dimension(0, 42));
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        PillBadgeRenderer badgeRenderer = new PillBadgeRenderer();
        complaintTable.getColumnModel().getColumn(2).setCellRenderer(badgeRenderer);
        complaintTable.getColumnModel().getColumn(3).setCellRenderer(badgeRenderer);
        complaintTable.getColumnModel().getColumn(4).setCellRenderer(badgeRenderer);

        // Live Quick Inspector update on row selection
        complaintTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateQuickInspector();
            }
        });

        complaintTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && complaintTable.getSelectedRow() >= 0) {
                    showSelectedComplaintDetails();
                }
            }
        });
    }

    private void updateQuickInspector() {
        int selected = complaintTable.getSelectedRow();
        if (selected < 0) {
            quickPreviewArea.setText("Select any complaint row in the queue to inspect its summary, priority aging, and assignment status.");
            return;
        }
        int row = complaintTable.convertRowIndexToModel(selected);
        int complaintId = Integer.parseInt(complaintTableModel.getValueAt(row, 0).toString());
        try {
            ComplaintService.ComplaintDetails d =
                    complaintService.getComplaintDetails(complaintId, session.currentUser());
            quickPreviewArea.setText(
                    "COMPLAINT #" + d.complaintId + "\n"
                    + d.title + "\n\n"
                    + "Status: " + d.status + "\n"
                    + "Department: " + d.department + "\n"
                    + "Location: " + d.location + "\n"
                    + "Assigned To: " + d.assignee + "\n"
                    + "Age: " + d.ageDays + " day(s)\n"
                    + "Upvotes: " + d.upvotes + "\n"
                    + "Attachments: " + d.attachments.size() + "\n\n"
                    + "Description:\n" + d.description
            );
            quickPreviewArea.setCaretPosition(0);
        } catch (Exception ex) {
            quickPreviewArea.setText("Unable to load preview: " + ex.getMessage());
        }
    }

    private void applyTableFilters() {
        if (complaintSorter == null) return;
        String query = searchField.getText().trim();
        String statusSelection = statusFilterCombo.getSelectedItem().toString();

        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        if (!query.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(query), 0, 1, 2, 5, 6, 7));
        }
        if (!"All Statuses".equals(statusSelection)) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(statusSelection) + "$", 4));
        }
        complaintSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    // =========================================================================
    // UI HELPERS & CUSTOM COMPONENTS
    // =========================================================================
    private JButton createStyledButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(background.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(background.equals(CARD_BG) || background.equals(BG_CANVAS)
                            ? new Color(226, 232, 240)
                            : background.equals(ACCENT_DANGER) ? new Color(220, 38, 38)
                            : background.equals(PRIMARY_SOFT) ? new Color(224, 231, 255) : PRIMARY_HOVER);
                } else {
                    g2.setColor(background);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (background.equals(CARD_BG) || background.equals(BG_CANVAS)) {
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
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        return field;
    }

    private static class RoundedCardPanel extends JPanel {
        private final int radius;
        private final Color bgColor;
        private final Color borderColor;

        RoundedCardPanel(int radius, Color bgColor, Color borderColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================================
    // SESSION & ROLE ROUTING
    // =========================================================================
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
        welcomeLabel.setText(currentUser.name);
        roleBadgeLabel.setText(currentUser.role
                + ("STAFF".equals(currentUser.role) ? " • " + currentUser.departmentName.toUpperCase() : ""));

        boolean admin = "ADMIN".equals(currentUser.role);
        boolean student = "STUDENT".equals(currentUser.role);
        boolean staff = "STAFF".equals(currentUser.role);

        // Sidebar visibility
        sidebarStudentHeader.setVisible(student);
        navSubmitComplaintBtn.setVisible(student);
        navReportStaffBtn.setVisible(student);
        navMyStaffReportsBtn.setVisible(student);

        sidebarAdminHeader.setVisible(admin);
        navViewStaffReportsBtn.setVisible(admin);
        navCreateStaffBtn.setVisible(admin);
        navListStaffBtn.setVisible(admin);
        navRunSchedulerBtn.setVisible(admin);

        // Table action bar visibility
        deleteComplaintButton.setVisible(admin || student);
        rateResolutionButton.setVisible(student);
        upvoteComplaintButton.setVisible(student);
        updateStatusButton.setVisible(admin || staff);
        assignStaffButton.setVisible(admin);

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

        int total = 0;
        int active = 0;
        int resolved = 0;
        int urgent = 0;

        try {
            List<String[]> rows = complaintService.listComplaintsFor(currentUser);
            for (String[] row : rows) {
                int age = Integer.parseInt(row[7]);
                String status = row[3];
                String effPriority = effectivePriorityName(row[2], status, age);

                total++;
                if ("RESOLVED".equals(status)) {
                    resolved++;
                } else if (!"REJECTED".equals(status)) {
                    active++;
                    if ("URGENT".equals(effPriority)) urgent++;
                }

                complaintTableModel.addRow(new Object[] {
                        row[0], row[1], row[2], effPriority,
                        status, row[5], row[4], row[6], row[7], row[8]
                });
            }

            kpiTotalVal.setText(String.valueOf(total));
            kpiActiveVal.setText(String.valueOf(active));
            kpiResolvedVal.setText(String.valueOf(resolved));
            kpiUrgentVal.setText(String.valueOf(urgent));

            updateQuickInspector();

            if (rows.isEmpty()) {
                setStatus("No complaints found.");
            }
        } catch (SQLException ex) {
            setStatus("Database error while loading complaints: " + ex.getMessage());
        }
    }

    // =========================================================================
    // EXISTING BUSINESS LOGIC & DIALOGS (Preserved Intact)
    // =========================================================================
    private void deleteSelectedComplaint() {
        int selected = complaintTable.getSelectedRow();
        if (selected < 0) {
            setStatus("Select a complaint to delete.");
            return;
        }
        int row = complaintTable.convertRowIndexToModel(selected);
        int complaintId = Integer.parseInt(complaintTableModel.getValueAt(row, 0).toString());
        String title = complaintTableModel.getValueAt(row, 1).toString();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Permanently delete complaint #" + complaintId + " - " + title + "?\n"
                        + "Its history, attachments, and feedback will also be deleted. "
                        + "Any linked staff report will remain without this complaint link.",
                "Delete Complaint", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            complaintService.deleteComplaint(complaintId, session.currentUser());
            refreshComplaintTable();
            setStatus("Complaint #" + complaintId + " deleted.");
        } catch (SQLException | IllegalArgumentException ex) {
            setStatus("Could not delete complaint: " + ex.getMessage());
        }
    }

    private void upvoteSelectedComplaint() {
        int selected = complaintTable.getSelectedRow();
        if (selected < 0) {
            setStatus("Select an open complaint to upvote.");
            return;
        }
        int row = complaintTable.convertRowIndexToModel(selected);
        int complaintId = Integer.parseInt(complaintTableModel.getValueAt(row, 0).toString());
        try {
            int votes = complaintService.upvoteComplaint(complaintId, session.currentUser());
            refreshComplaintTable();
            setStatus("Complaint #" + complaintId + " now has " + votes + " upvote(s)."
                    + (votes > 0 && votes % 5 == 0
                    ? " Priority promoted by one level." : ""));
        } catch (SQLException | IllegalArgumentException ex) {
            setStatus("Could not upvote complaint: " + ex.getMessage());
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
        openComplaintDialogForm();
    }

    private void openStaffReportDialog() {
        UserAccount student = session.currentUser();
        if (student == null || !"STUDENT".equals(student.role)) {
            setStatus("Only students can report staff members.");
            return;
        }

        List<String[]> staff;
        try {
            staff = staffReportService.listReportableStaff(student);
        } catch (SQLException ex) {
            setStatus("Unable to load staff members: " + ex.getMessage());
            return;
        }
        if (staff.isEmpty()) {
            setStatus("There are no active staff accounts to report.");
            return;
        }

        JComboBox<String> staffCombo = new JComboBox<>();
        for (String[] member : staff) staffCombo.addItem(member[1] + " (" + member[2] + ")");
        JComboBox<String> complaintCombo = new JComboBox<>();
        List<String[]> linkedComplaints = new ArrayList<>();
        Runnable refreshLinkedComplaints = () -> {
            complaintCombo.removeAllItems();
            complaintCombo.addItem("No linked complaint");
            linkedComplaints.clear();
            int index = staffCombo.getSelectedIndex();
            if (index < 0) return;
            try {
                linkedComplaints.addAll(staffReportService.listStudentComplaintsForStaff(
                        student, Integer.parseInt(staff.get(index)[0])));
                for (String[] complaint : linkedComplaints) {
                    complaintCombo.addItem("#" + complaint[0] + " - " + complaint[1]
                            + " [" + complaint[2] + "]");
                }
            } catch (SQLException ex) {
                setStatus("Unable to load your complaints for this staff member: " + ex.getMessage());
            }
        };
        staffCombo.addActionListener(e -> refreshLinkedComplaints.run());
        refreshLinkedComplaints.run();

        String[] reasonLabels = {
                "Ignoring complaints", "Unnecessary delays", "Fake resolution", "Misconduct"
        };
        String[] reasonCodes = {
                "IGNORED_COMPLAINT", "UNNECESSARY_DELAY", "FAKE_RESOLUTION", "MISCONDUCT"
        };
        JComboBox<String> reasonCombo = new JComboBox<>(reasonLabels);
        JTextArea description = new JTextArea(5, 32);
        description.setFont(FONT_MAIN);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        final File[] evidence = new File[1];
        JLabel evidenceLabel = new JLabel("No evidence image selected");
        evidenceLabel.setFont(FONT_SMALL);
        JButton chooseEvidence = createStyledButton("Choose Evidence Image...", CARD_BG, TEXT_MAIN);
        chooseEvidence.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Report Evidence");
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Images (*.jpg, *.png, *.gif, *.bmp)", "jpg", "jpeg", "png", "bmp", "gif"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                evidence[0] = chooser.getSelectedFile();
                evidenceLabel.setText(evidence[0].getName());
            }
        });
        JPanel evidencePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        evidencePanel.add(chooseEvidence);
        evidencePanel.add(evidenceLabel);
        Object[] fields = {
                "Staff member", staffCombo,
                "Related complaint (optional)", complaintCombo,
                "Reason", reasonCombo,
                "Description", new JScrollPane(description),
                "Evidence (optional, image up to 10 MB)", evidencePanel
        };
        int result = JOptionPane.showConfirmDialog(this, fields, "Report Staff Member",
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            int staffIndex = staffCombo.getSelectedIndex();
            if (staffIndex < 0) throw new IllegalArgumentException("Select a staff member.");
            Integer complaintId = null;
            int complaintIndex = complaintCombo.getSelectedIndex();
            if (complaintIndex > 0) {
                complaintId = Integer.parseInt(linkedComplaints.get(complaintIndex - 1)[0]);
            }
            byte[] evidenceData = null;
            String evidenceName = null;
            String evidenceMime = null;
            if (evidence[0] != null) {
                if (evidence[0].length() > 10 * 1024 * 1024) {
                    throw new IllegalArgumentException("Evidence image must be 10 MB or smaller.");
                }
                evidenceData = Files.readAllBytes(evidence[0].toPath());
                evidenceName = evidence[0].getName();
                evidenceMime = imageMimeType(evidence[0]);
            }
            int reportId = staffReportService.submitReport(student,
                    Integer.parseInt(staff.get(staffIndex)[0]), complaintId,
                    reasonCodes[reasonCombo.getSelectedIndex()], description.getText(),
                    evidenceName, evidenceMime, evidenceData);
            setStatus("Staff report submitted to the global administrator. Report ID: #" + reportId);
        } catch (SQLException | IOException ex) {
            setStatus("Staff report submission failed: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        }
    }

    private void showStudentStaffReports() {
        UserAccount student = session.currentUser();
        if (student == null || !"STUDENT".equals(student.role)) {
            setStatus("Only students can view their staff reports.");
            return;
        }
        try {
            List<String[]> reports = staffReportService.listOwnReports(student);
            List<String[]> studentRows = new ArrayList<>();
            for (String[] report : reports) {
                studentRows.add(new String[] {report[0], report[1], report[3], formatReportReason(report[4]),
                        report[5], report[7]});
            }
            DefaultTableModel model = readOnlyTableModel(
                    new String[] {"ID", "Submitted", "Staff", "Reason", "Status", "Admin update"}, studentRows);
            JTable table = new JTable(model);
            table.setRowHeight(28);
            table.getColumnModel().getColumn(5).setPreferredWidth(260);
            Object[] actions = {"View Selected", "Delete Selected", "Close"};
            int choice = JOptionPane.showOptionDialog(this, new JScrollPane(table),
                    "My Staff Reports", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, actions, actions[0]);
            if (choice == 0 && table.getSelectedRow() >= 0) {
                int row = table.convertRowIndexToModel(table.getSelectedRow());
                int reportId = Integer.parseInt(model.getValueAt(row, 0).toString());
                showStaffReportDetails(reportId, student, false);
            } else if (choice == 1 && table.getSelectedRow() >= 0) {
                int row = table.convertRowIndexToModel(table.getSelectedRow());
                deleteStaffReport(Integer.parseInt(model.getValueAt(row, 0).toString()), student);
            } else if (choice == 0 || choice == 1) {
                setStatus("Select a staff report first.");
            } else if (choice >= 0) {
                setStatus(reports.isEmpty() ? "You have not submitted any staff reports."
                        : "Showing " + reports.size() + " staff report(s). Admin updates appear in this list.");
            }
        } catch (SQLException ex) {
            setStatus("Unable to load your staff reports: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        }
    }

    private void showAdminStaffReports() {
        UserAccount admin = session.currentUser();
        if (admin == null || !"ADMIN".equals(admin.role)) {
            setStatus("Only the global administrator can review staff reports.");
            return;
        }
        try {
            List<String[]> reports = staffReportService.listReportsForAdmin(admin);
            List<String[]> adminRows = new ArrayList<>();
            for (String[] report : reports) {
                String[] row = report.clone();
                row[4] = formatReportReason(row[4]);
                adminRows.add(row);
            }
            DefaultTableModel model = readOnlyTableModel(
                    new String[] {"ID", "Submitted", "Student", "Staff", "Reason", "Status",
                            "Related complaint", "Student update"}, adminRows);
            JTable table = new JTable(model);
            table.setRowHeight(28);
            table.getColumnModel().getColumn(7).setPreferredWidth(260);
            Object[] actions = {"Investigate Selected", "Delete Selected", "Close"};
            int choice = JOptionPane.showOptionDialog(this, new JScrollPane(table),
                    "Admin Dashboard - Staff Reports", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null, actions, actions[0]);
            if (choice == 0 && table.getSelectedRow() >= 0) {
                int row = table.convertRowIndexToModel(table.getSelectedRow());
                int reportId = Integer.parseInt(model.getValueAt(row, 0).toString());
                showStaffReportDetails(reportId, admin, true);
            } else if (choice == 1 && table.getSelectedRow() >= 0) {
                int row = table.convertRowIndexToModel(table.getSelectedRow());
                deleteStaffReport(Integer.parseInt(model.getValueAt(row, 0).toString()), admin);
            } else if (choice == 0 || choice == 1) {
                setStatus("Select a staff report first.");
            } else if (choice >= 0) {
                setStatus(reports.isEmpty() ? "No staff reports have been filed."
                        : "Loaded " + reports.size() + " staff report(s) for the administrator.");
            }
        } catch (SQLException ex) {
            setStatus("Unable to load staff reports: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        }
    }

    private void deleteStaffReport(int reportId, UserAccount actor) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Permanently delete staff report #" + reportId + " and its evidence?",
                "Delete Staff Report", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            staffReportService.deleteReport(reportId, actor);
            setStatus("Staff report #" + reportId + " deleted.");
        } catch (SQLException | IllegalArgumentException ex) {
            setStatus("Could not delete staff report: " + ex.getMessage());
        }
    }

    private DefaultTableModel readOnlyTableModel(String[] columns, List<String[]> rows) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        for (String[] row : rows) model.addRow(row);
        return model;
    }

    private String formatReportReason(String reason) {
        switch (reason) {
            case "IGNORED_COMPLAINT": return "Ignoring complaints";
            case "UNNECESSARY_DELAY": return "Unnecessary delays";
            case "FAKE_RESOLUTION": return "Fake resolution";
            case "MISCONDUCT": return "Misconduct";
            default: return reason;
        }
    }

    private void showStaffReportDetails(int reportId, UserAccount viewer, boolean adminView) {
        try {
            StaffReportService.ReportDetails report =
                    staffReportService.getReportDetails(reportId, viewer);
            JTextArea reportText = new JTextArea(
                    "Report #" + report.reportId + " - " + report.reason + "\n"
                            + "Status: " + report.status + " | Submitted: " + report.createdAt + "\n"
                            + "Student: " + report.student + " | Staff member: " + report.staff + "\n"
                            + "Related complaint: " + (report.complaintId == null ? "None"
                            : "#" + report.complaintId + " - " + report.complaintTitle) + "\n\n"
                            + report.description);
            reportText.setEditable(false);
            reportText.setLineWrap(true);
            reportText.setWrapStyleWord(true);
            reportText.setRows(8);
            JPanel detailsPanel = new JPanel();
            detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
            detailsPanel.add(new JScrollPane(reportText));
            if (report.evidence != null) {
                JLabel evidencePreview = new JLabel("Evidence: " + report.evidence.fileName);
                evidencePreview.setHorizontalAlignment(SwingConstants.CENTER);
                evidencePreview.setPreferredSize(new Dimension(360, 240));
                showAttachmentPreview(report.evidence, evidencePreview);
                detailsPanel.add(evidencePreview);
            } else {
                detailsPanel.add(new JLabel("No evidence image attached."));
            }

            if (!adminView) {
                String response = report.adminResponse == null || report.adminResponse.trim().isEmpty()
                        ? "The administrator has not posted an update yet."
                        : "Administrator update: " + report.adminResponse;
                detailsPanel.add(new JLabel(response));
                JOptionPane.showMessageDialog(this, detailsPanel, "Staff Report Details",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JComboBox<String> statusCombo = new JComboBox<>(
                    new String[] {"SUBMITTED", "UNDER_REVIEW", "ACTION_TAKEN", "CLOSED"});
            statusCombo.setSelectedItem(report.status);
            JTextArea responseArea = new JTextArea(report.adminResponse == null ? "" : report.adminResponse, 3, 32);
            responseArea.setLineWrap(true);
            responseArea.setWrapStyleWord(true);
            detailsPanel.add(new JLabel("Report status"));
            detailsPanel.add(statusCombo);
            detailsPanel.add(new JLabel("Update for student (required; visible in My Staff Reports)"));
            detailsPanel.add(new JScrollPane(responseArea));
            Object[] options = report.complaintId == null
                    ? new Object[] {"Save Update", "Close"}
                    : new Object[] {"Save Update", "Reassign Complaint", "Close"};
            int choice = JOptionPane.showOptionDialog(this, detailsPanel, "Admin - Investigate Staff Report",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                staffReportService.updateReport(reportId, statusCombo.getSelectedItem().toString(),
                        responseArea.getText(), viewer);
                setStatus("Staff report updated; the student can see the administrator's update.");
            } else if (choice == 1 && report.complaintId != null) {
                reassignReportComplaint(report.complaintId, viewer);
            }
        } catch (SQLException ex) {
            setStatus("Unable to open staff report: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
        }
    }

    private void reassignReportComplaint(int complaintId, UserAccount admin) throws SQLException {
        List<String[]> staff = complaintService.listStaffForComplaint(complaintId, admin);
        if (staff.isEmpty()) {
            setStatus("No staff are available in the linked complaint's department.");
            return;
        }
        String[] labels = new String[staff.size()];
        for (int i = 0; i < staff.size(); i++) labels[i] = staff.get(i)[1] + " (" + staff.get(i)[2] + ")";
        int selected = JOptionPane.showOptionDialog(this, "Choose a new assignee for complaint #"
                        + complaintId, "Reassign Complaint", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        if (selected < 0) return;
        complaintService.assignComplaint(complaintId, Integer.parseInt(staff.get(selected)[0]), admin);
        setStatus("Complaint #" + complaintId + " reassigned from the staff report.");
        refreshComplaintTable();
    }

    private void openComplaintDialogForm() {
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
                            + "Upvotes: " + details.upvotes + "\n"
                            + "Department: " + details.department + " | Student: " + details.student + "\n"
                            + "Location: " + details.location + " | Assigned to: " + details.assignee + "\n"
                            + "Submitted: " + details.createdAt + " (" + details.ageDays + " days ago)\n\n"
                            + "Description:\n" + details.description
                            + (details.feedbackRating == null ? "" : "\n\nStudent rating: "
                                    + details.feedbackRating + "/5 stars"
                                    + (details.feedbackComment == null ? "" : "\nFeedback: " + details.feedbackComment)));
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

    private void openFeedbackDialog() {
        int selected = complaintTable.getSelectedRow();
        if (selected < 0) {
            setStatus("Select a resolved complaint to rate.");
            return;
        }
        int modelRow = complaintTable.convertRowIndexToModel(selected);
        int complaintId = Integer.parseInt(complaintTableModel.getValueAt(modelRow, 0).toString());
        UserAccount student = session.currentUser();
        try {
            ComplaintService.ComplaintDetails details =
                    complaintService.getComplaintDetails(complaintId, student);
            if (!"RESOLVED".equals(details.status)) {
                setStatus("You can rate a complaint only after it is resolved.");
                return;
            }

            ButtonGroup ratings = new ButtonGroup();
            JToggleButton[] stars = new JToggleButton[5];
            JLabel selectedRating = new JLabel("Select a rating from 1 to 5 stars.");
            JPanel ratingChoices = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            for (int i = 0; i < stars.length; i++) {
                final int rating = i + 1;
                JToggleButton star = new JToggleButton("\u2605");
                star.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 26));
                star.setForeground(new Color(245, 158, 11));
                star.setToolTipText(rating + (rating == 1 ? " star" : " stars"));
                star.getAccessibleContext().setAccessibleName(rating + (rating == 1 ? " star" : " stars"));
                star.addActionListener(e -> selectedRating.setText("Selected: " + rating + "/5 stars"));
                stars[i] = star;
                ratings.add(star);
                ratingChoices.add(star);
            }
            if (details.feedbackRating != null) {
                stars[details.feedbackRating - 1].setSelected(true);
                selectedRating.setText("Selected: " + details.feedbackRating + "/5 stars");
            }

            JTextArea feedbackComment = new JTextArea(5, 32);
            feedbackComment.setLineWrap(true);
            feedbackComment.setWrapStyleWord(true);
            if (details.feedbackComment != null) feedbackComment.setText(details.feedbackComment);
            JPanel feedbackPanel = new JPanel(new GridLayout(0, 1, 6, 6));
            JPanel ratingPanel = new JPanel(new BorderLayout(0, 4));
            ratingPanel.add(new JLabel("How was the resolution?"), BorderLayout.NORTH);
            ratingPanel.add(ratingChoices, BorderLayout.CENTER);
            ratingPanel.add(selectedRating, BorderLayout.SOUTH);
            feedbackPanel.add(ratingPanel);
            feedbackPanel.add(new JLabel("Optional comment (up to 1000 characters):"));
            feedbackPanel.add(new JScrollPane(feedbackComment));

            int result = JOptionPane.showConfirmDialog(this, feedbackPanel,
                    "Rate Resolution - #" + complaintId, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            if (ratings.getSelection() == null) {
                JOptionPane.showMessageDialog(this, "Select a star rating before saving.",
                        "Rating required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int rating = 0;
            for (int i = 0; i < stars.length; i++) {
                if (stars[i].isSelected()) rating = i + 1;
            }
            complaintService.submitResolutionFeedback(complaintId, student, rating, feedbackComment.getText());
            setStatus("Your resolution feedback was saved.");
        } catch (SQLException | IllegalArgumentException ex) {
            setStatus("Could not save resolution feedback: " + ex.getMessage());
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
        int selectedRow = complaintTable.getSelectedRow();
        String prefillId = selectedRow < 0 ? "" : complaintTableModel.getValueAt(
                complaintTable.convertRowIndexToModel(selectedRow), 0).toString();

        JTextField complaintIdField = styleTextField(new JTextField(prefillId));
        JComboBox<String> statusCombo = new JComboBox<>(new String[] {"IN_PROGRESS", "RESOLVED", "REJECTED"});
        JTextArea remarksArea = new JTextArea(4, 30);
        remarksArea.setFont(FONT_MAIN);
        remarksArea.setLineWrap(true);
        remarksArea.setWrapStyleWord(true);

        final File[] selectedAttachment = new File[1];
        JLabel attachmentLabel = new JLabel("No image selected");
        attachmentLabel.setFont(FONT_SMALL);
        attachmentLabel.setForeground(TEXT_MUTED);
        JButton attachmentButton = createStyledButton("Attach Image (optional)", CARD_BG, TEXT_MAIN);
        attachmentButton.addActionListener(e -> {
            selectedAttachment[0] = pickComplaintPhoto();
            attachmentLabel.setText(selectedAttachment[0] == null ? "No image selected" : selectedAttachment[0].getName());
        });
        JPanel attachmentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        attachmentPanel.add(attachmentButton);
        attachmentPanel.add(attachmentLabel);

        Object[] fields = {
                "Complaint ID", complaintIdField,
                "New Status", statusCombo,
                "Resolution Remarks", new JScrollPane(remarksArea),
                "Optional Evidence / Photo", attachmentPanel
        };
        int result = JOptionPane.showConfirmDialog(this, fields, "Update Complaint Status", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            int complaintId = Integer.parseInt(complaintIdField.getText().trim());
            byte[] attachmentData = null;
            String attachmentName = null;
            String attachmentMime = null;
            if (selectedAttachment[0] != null) {
                if (selectedAttachment[0].length() > 10 * 1024 * 1024) {
                    throw new IllegalArgumentException("Image must be 10 MB or smaller.");
                }
                attachmentData = Files.readAllBytes(selectedAttachment[0].toPath());
                attachmentName = selectedAttachment[0].getName();
                attachmentMime = imageMimeType(selectedAttachment[0]);
            }
            String oldStatus = complaintService.updateStatus(
                    complaintId,
                    statusCombo.getSelectedItem().toString(),
                    currentUser,
                    remarksArea.getText().trim(),
                    attachmentName, attachmentMime, attachmentData);
            setStatus("Complaint #" + complaintId + " changed from " + oldStatus + " to " + statusCombo.getSelectedItem());
            refreshComplaintTable();
        } catch (NumberFormatException ex) {
            setStatus("Please enter a valid complaint ID.");
        } catch (SQLException | IOException ex) {
            setStatus("Status update failed: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage());
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
        Object[] options = {"Mutex (safe)", "Race condition (unsafe)", "Bounded buffer", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "Two worker threads increment the same simulated task counter.\n"
                        + "The mutex option protects the shared counter; the race option does not.\n"
                        + "This demonstration does not change complaint data.",
                "Synchronization Demonstration", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice < 0 || choice == 3) return;

        String mode = choice == 0 ? "sync" : choice == 1 ? "race" : "buffer";
        String label = choice == 0 ? "MUTEX DEMO" : choice == 1 ? "RACE CONDITION DEMO"
                : "BOUNDED BUFFER DEMO";
        schedulerOutput.setText("Running " + label.toLowerCase() + "...\n");
        navRunSyncDemoBtn.setEnabled(false);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return osModule.runDemo(mode);
            }

            @Override
            protected void done() {
                navRunSyncDemoBtn.setEnabled(true);
                try {
                    schedulerOutput.setText(label + "\n"
                            + "This demo uses two threads to update a simulated shared counter. "
                            + "It does not modify Campus Voice complaints or database records.\n\n"
                            + get());
                    setStatus(choice == 0
                            ? "Mutex demo complete. Compare expected and actual counter values."
                            : choice == 1 ? "Race demo complete. Lost updates depend on thread timing."
                            : "Bounded buffer demo complete. Producer and consumer stayed synchronized.");
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
                g.setColor(active ? new Color(199, 210, 254) : PRIMARY_SOFT);
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

            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (!textVal.isEmpty()) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
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