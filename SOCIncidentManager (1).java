import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * SOC -- Security Operations Center
 * Application de gestion d'incidents de cybersecurite
 * Version 4.0 -- Robuste & Fonctionnel
 *
 * Ameliorations v4.0 :
 *   - Persistance automatique des incidents (save/load)
 *   - Recherche et filtrage dans l'historique
 *   - Tri dynamique des colonnes du tableau
 *   - Suppression / Edition d'incidents individuels
 *   - Export CSV + TXT
 *   - Import depuis fichier
 *   - Raccourcis clavier (Ctrl+Entree, Ctrl+R, Ctrl+F, Suppr)
 *   - Validation visuelle des champs
 *   - Tooltips informatifs
 *   - Notifications dans la barre de statut
 *   - Gestion d'erreurs complete
 *
 * Compilation : javac SOCIncidentManager.java
 * Execution   : java  SOCIncidentManager
 */
public class SOCIncidentManager extends JFrame {

    // =========================================================================
    // Palette -- Theme Violet / Vert Neon
    // =========================================================================
    private static final Color BG_DARK        = new Color(8,   10,  22);
    private static final Color BG_PANEL       = new Color(14,  16,  34);
    private static final Color BG_FIELD       = new Color(20,  22,  48);
    private static final Color BG_TAB_ACTIVE  = new Color(28,  20,  60);
    private static final Color BG_TAB_IDLE    = new Color(14,  14,  32);
    private static final Color BORDER_NORMAL  = new Color(60,  40, 110);
    private static final Color BORDER_FOCUS   = new Color(140,  80, 255);
    private static final Color BORDER_ERROR   = new Color(200,  40,  60);
    private static final Color ACCENT_PURPLE  = new Color(130,  60, 240);
    private static final Color ACCENT_VIOLET  = new Color(180, 100, 255);
    private static final Color ACCENT_GREEN   = new Color(0,   220, 130);
    private static final Color ACCENT_RED     = new Color(220,  50,  70);
    private static final Color ACCENT_ORANGE  = new Color(255, 150,   0);
    private static final Color ACCENT_YELLOW  = new Color(255, 220,  50);
    private static final Color ACCENT_CYAN    = new Color(0,   180, 220);
    private static final Color TEXT_PRIMARY   = new Color(220, 215, 255);
    private static final Color TEXT_MUTED     = new Color(110, 100, 160);
    private static final Color TEXT_LABEL     = new Color(170, 150, 230);
    private static final Color TABLE_ROW_A    = new Color(18,  16,  42);
    private static final Color TABLE_ROW_B    = new Color(22,  20,  50);
    private static final Color TABLE_HEADER   = new Color(35,  25,  75);
    private static final Color TABLE_SELECT   = new Color(80,  40, 160);

    // Couleurs de fond par niveau de criticite
    private static final Color BG_CRITIQUE    = new Color(30,  5,   8);
    private static final Color BG_MOYEN       = new Color(28,  20,  0);
    private static final Color BG_FAIBLE      = new Color(5,   22,  14);

    // Couleurs de grille par niveau de criticite
    private static final Color GRID_CRITIQUE  = new Color(180, 30,  40,  30);
    private static final Color GRID_MOYEN     = new Color(200, 130, 0,   30);
    private static final Color GRID_FAIBLE    = new Color(0,   160, 80,  30);
    private static final Color GRID_NORMAL    = new Color(50,  35,  90,  25);

    // =========================================================================
    // Chemin de persistance
    // =========================================================================
    private static final String DATA_DIR  = System.getProperty("user.home")
                                              + File.separator + ".soc";
    private static final String DATA_FILE = DATA_DIR + File.separator + "incidents.dat";

    // =========================================================================
    // Formateur de dates
    // =========================================================================
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // =========================================================================
    // Modele d'incident
    // =========================================================================
    static class Incident {
        String analyste, ip, type, criticite, symptomes, description, horodatage;

        Incident(String a, String ip, String t, String cr, String sy, String de, String ho) {
            this.analyste    = a;
            this.ip          = ip;
            this.type        = t;
            this.criticite   = cr;
            this.symptomes   = sy;
            this.description = de;
            this.horodatage  = ho;
        }

        /** Serialise l'incident en une seule ligne (delimiter = \u001E = record separator) */
        String serialize() {
            return join(horodatage, analyste, ip, type, criticite, symptomes, description);
        }

        /** Deserialise une ligne en Incident ; retourne null si invalide */
        static Incident deserialize(String line) {
            if (line == null || line.isEmpty()) return null;
            String[] parts = splitPreserve(line);
            if (parts.length != 7) return null;
            return new Incident(parts[1], parts[2], parts[3], parts[4], parts[5], parts[6], parts[0]);
        }

        private static final char SEP = '\u001E'; // ASCII Record Separator

        private static String join(String... fields) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) sb.append(SEP);
                sb.append(fields[i] == null ? "" : fields[i].replace("\n", "\u001F").replace(SEP, '\u001F'));
            }
            return sb.toString();
        }

        private static String[] splitPreserve(String line) {
            List<String> parts = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == SEP) {
                    parts.add(sb.toString().replace('\u001F', '\n'));
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
            parts.add(sb.toString().replace('\u001F', '\n'));
            return parts.toArray(new String[0]);
        }
    }

    // =========================================================================
    // Etat courant
    // =========================================================================
    private String currentCriticite = "none";

    // Mode edition : -1 = creation, >= 0 = index de l'incident en cours d'edition
    private int editingIndex = -1;

    // =========================================================================
    // Composants onglet Declaration
    // =========================================================================
    private JTextField        tfAnalyste;
    private JTextField        tfIP;
    private JComboBox<String> cbTypeIncident;
    private JRadioButton      rbFaible, rbMoyen, rbCritique;
    private ButtonGroup       bgCriticite;
    private JCheckBox         cbReseau, cbConnexion, cbFichiers, cbProcessus;
    private JTextArea         taDescription;
    private JButton           btnSignaler, btnReinitialiser, btnAnnulerEdit;
    private JLabel            lblEditMode;

    // =========================================================================
    // Composants onglet Historique
    // =========================================================================
    private DefaultTableModel tableModel;
    private JTable            tableHistorique;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField        tfRecherche;
    private JLabel            lblStatTotal, lblStatCritique, lblStatMoyen, lblStatFaible;
    private JButton           btnViderHisto, btnExporterCSV, btnExporterTXT, btnImporter;
    private JTextArea         taDetail;

    // =========================================================================
    // Composants globaux
    // =========================================================================
    private JLabel            lblDateTime;
    private JLabel            lblCompteur;
    private JLabel            lblStatut;   // barre de statut dynamique
    private JTabbedPane       tabbedPane;
    private JPanel            rootPanel;
    private int               compteurIncidents = 0;
    private List<Incident>    incidents = new ArrayList<>();

    // =========================================================================
    // Constructeur
    // =========================================================================
    public SOCIncidentManager() {
        super("SOC v4.0 -- Security Operations Center");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(820, 640));

        // Confirmation a la fermeture
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sauvegarderDonnees();
                dispose();
                System.exit(0);
            }
        });

        buildUI();
        chargerDonnees();
        startClock();
        installerRaccourcis();

        pack();
        setSize(880, 720);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // =========================================================================
    // Interface principale
    // =========================================================================
    private void buildUI() {
        rootPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Color bg   = getBgColor();
                Color grid = getGridColor();
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(grid);
                g2.setStroke(new BasicStroke(0.5f));
                for (int x = 0; x < getWidth();  x += 45) g2.drawLine(x, 0, x, getHeight());
                for (int y = 0; y < getHeight(); y += 45) g2.drawLine(0, y, getWidth(), y);
                g2.dispose();
            }
        };
        rootPanel.setOpaque(true);
        rootPanel.setBackground(BG_DARK);
        setContentPane(rootPanel);

        rootPanel.add(buildHeader(),  BorderLayout.NORTH);
        rootPanel.add(buildTabs(),    BorderLayout.CENTER);
        rootPanel.add(buildFooter(),  BorderLayout.SOUTH);
    }

    // =========================================================================
    // Couleurs dynamiques selon criticite
    // =========================================================================
    private Color getBgColor() {
        switch (currentCriticite) {
            case "Critique": return BG_CRITIQUE;
            case "Moyen":    return BG_MOYEN;
            case "Faible":   return BG_FAIBLE;
            default:         return BG_DARK;
        }
    }

    private Color getGridColor() {
        switch (currentCriticite) {
            case "Critique": return GRID_CRITIQUE;
            case "Moyen":    return GRID_MOYEN;
            case "Faible":   return GRID_FAIBLE;
            default:         return GRID_NORMAL;
        }
    }

    private Color getHeaderLineColor() {
        switch (currentCriticite) {
            case "Critique": return ACCENT_RED;
            case "Moyen":    return ACCENT_YELLOW;
            case "Faible":   return ACCENT_GREEN;
            default:         return ACCENT_PURPLE;
        }
    }

    // =========================================================================
    // En-tete global
    // =========================================================================
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color lineColor = getHeaderLineColor();
                Color glowColor = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 40);
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 10, 50),
                        getWidth(), getHeight(), new Color(10, 5, 30));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.setColor(glowColor);
                g2.setStroke(new BasicStroke(4f));
                g2.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(14, 24, 14, 24));
        header.setPreferredSize(new Dimension(880, 82));

        // --- Cote gauche : titre ---
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);

        JLabel lblSoc = new JLabel("[ SOC ]  SECURITY OPERATIONS CENTER  --  v4.0");
        lblSoc.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        lblSoc.setForeground(ACCENT_VIOLET);

        JLabel lblTitle = new JLabel("Gestion des Incidents de Cybersecurite");
        lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        lblTitle.setForeground(TEXT_PRIMARY);

        titlePanel.add(lblSoc);
        titlePanel.add(lblTitle);

        // --- Cote droit : horloge + compteur ---
        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 0, 3));
        rightPanel.setOpaque(false);

        lblDateTime = new JLabel("", SwingConstants.RIGHT);
        lblDateTime.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        lblDateTime.setForeground(TEXT_MUTED);

        lblCompteur = new JLabel("Total incidents : 0", SwingConstants.RIGHT);
        lblCompteur.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        lblCompteur.setForeground(ACCENT_GREEN);

        rightPanel.add(lblDateTime);
        rightPanel.add(lblCompteur);

        header.add(titlePanel, BorderLayout.CENTER);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    // =========================================================================
    // JTabbedPane principal
    // =========================================================================
    private JTabbedPane buildTabs() {
        tabbedPane = new JTabbedPane(JTabbedPane.TOP) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG_PANEL);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tabbedPane.setOpaque(true);
        tabbedPane.setBackground(BG_PANEL);
        tabbedPane.setForeground(TEXT_PRIMARY);
        tabbedPane.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));

        tabbedPane.addTab("  Declaration d'Incident  ", buildTabDeclaration());
        tabbedPane.addTab("  Historique & Horodatage  ", buildTabHistorique());

        tabbedPane.setBackgroundAt(0, BG_TAB_ACTIVE);
        tabbedPane.setBackgroundAt(1, BG_TAB_IDLE);
        tabbedPane.setForegroundAt(0, ACCENT_VIOLET);
        tabbedPane.setForegroundAt(1, TEXT_LABEL);

        tabbedPane.addChangeListener(e -> {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                boolean sel = (i == tabbedPane.getSelectedIndex());
                tabbedPane.setBackgroundAt(i, sel ? BG_TAB_ACTIVE : BG_TAB_IDLE);
                tabbedPane.setForegroundAt(i, sel ? ACCENT_VIOLET : TEXT_LABEL);
            }
            // Si on bascule sur l'onglet historique et qu'on etait en mode edition, annuler
            if (tabbedPane.getSelectedIndex() == 0 && editingIndex >= 0) {
                // on reste en mode edition sur l'onglet declaration, c'est OK
            }
        });

        tabbedPane.setBorder(BorderFactory.createLineBorder(BORDER_NORMAL, 1));
        return tabbedPane;
    }

    // =========================================================================
    // Onglet 1 : Declaration d'incident
    // =========================================================================
    private JPanel buildTabDeclaration() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBackground(BG_PANEL);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(14, 24, 8, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // --- Mode edition (masque par defaut) ---
        lblEditMode = new JLabel("");
        lblEditMode.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        lblEditMode.setForeground(ACCENT_CYAN);
        lblEditMode.setVisible(false);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 8, 0);
        form.add(lblEditMode, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
        row++;

        // Nom analyste
        addLabel(form, gbc, row, "Nom de l'analyste *", ACCENT_VIOLET,
                 "Nom complet de l'analyste ayant detecte l'incident");
        tfAnalyste = createTextField("Ex: Alice Martin");
        addField(form, gbc, row, tfAnalyste); row++;

        // Adresse IP
        addLabel(form, gbc, row, "Adresse IP source *", ACCENT_VIOLET,
                 "Adresse IPv4 du poste ou serveur source (ex: 192.168.1.10)");
        tfIP = createTextField("Ex: 192.168.1.10");
        addField(form, gbc, row, tfIP); row++;

        // Type d'incident
        addLabel(form, gbc, row, "Type d'incident", TEXT_LABEL,
                 "Categorie de l'incident detecte");
        String[] types = {"-- Selectionner --","Phishing","Malware","Intrusion",
                          "DDoS","Ransomware","Fuite de donnees","Access non autorise",
                          "Deni de service","Autre"};
        cbTypeIncident = createComboBox(types);
        addField(form, gbc, row, cbTypeIncident); row++;

        addSeparator(form, gbc, row); row++;

        // Criticite
        addLabel(form, gbc, row, "Niveau de criticite *", TEXT_LABEL,
                 "Selectionnez la severite de l'incident");
        gbc.gridx = 1; gbc.gridy = row;
        form.add(buildRadioPanel(), gbc); row++;

        addSeparator(form, gbc, row); row++;

        // Symptomes
        addLabel(form, gbc, row, "Symptomes detectes *", TEXT_LABEL,
                 "Cochez au moins un symptome observe");
        gbc.gridx = 1; gbc.gridy = row;
        form.add(buildCheckPanel(), gbc); row++;

        addSeparator(form, gbc, row); row++;

        // Description
        addLabel(form, gbc, row, "Description detaillee", TEXT_LABEL,
                 "Decrivez l'incident en detail (optionnel mais recommande)");
        taDescription = new JTextArea(5, 30);
        taDescription.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        taDescription.setForeground(TEXT_PRIMARY);
        taDescription.setBackground(BG_FIELD);
        taDescription.setCaretColor(ACCENT_VIOLET);
        taDescription.setLineWrap(true);
        taDescription.setWrapStyleWord(true);
        taDescription.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane scroll = new JScrollPane(taDescription);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_NORMAL, 1));
        scroll.getVerticalScrollBar().setBackground(BG_PANEL);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        gbc.gridx = 1; gbc.gridy = row;
        gbc.fill  = GridBagConstraints.BOTH;
        form.add(scroll, gbc);
        gbc.fill  = GridBagConstraints.HORIZONTAL; row++;

        // Boutons
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        gbc.insets = new Insets(14, 4, 4, 4);
        form.add(buildButtonPanel(), gbc);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getVerticalScrollBar().setBackground(BG_PANEL);

        wrapper.add(formScroll, BorderLayout.CENTER);
        return wrapper;
    }

    // =========================================================================
    // Onglet 2 : Historique & Horodatage
    // =========================================================================
    private JPanel buildTabHistorique() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // --- Barre de stats ---
        JPanel statsBar = new JPanel(new GridLayout(1, 4, 10, 0));
        statsBar.setOpaque(false);
        statsBar.setBorder(new EmptyBorder(0, 0, 10, 0));

        lblStatTotal    = buildStatCard("Total",     "0", ACCENT_VIOLET);
        lblStatCritique = buildStatCard("Critiques", "0", ACCENT_RED);
        lblStatMoyen    = buildStatCard("Moyens",    "0", ACCENT_ORANGE);
        lblStatFaible   = buildStatCard("Faibles",   "0", ACCENT_GREEN);

        statsBar.add(lblStatTotal.getParent());
        statsBar.add(lblStatCritique.getParent());
        statsBar.add(lblStatMoyen.getParent());
        statsBar.add(lblStatFaible.getParent());
        panel.add(statsBar, BorderLayout.NORTH);

        // --- Barre de recherche ---
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel lblSearch = new JLabel("  Rechercher :");
        lblSearch.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        lblSearch.setForeground(TEXT_LABEL);

        tfRecherche = new JTextField(30);
        tfRecherche.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tfRecherche.setForeground(TEXT_PRIMARY);
        tfRecherche.setBackground(BG_FIELD);
        tfRecherche.setCaretColor(ACCENT_VIOLET);
        tfRecherche.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER_NORMAL, 1),
                new EmptyBorder(5, 8, 5, 8)));
        tfRecherche.setToolTipText("Rechercher dans tous les champs (analyste, IP, type, criticite, symptomes)");

        // Filtrage en temps reel
        tfRecherche.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { appliquerFiltre(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { appliquerFiltre(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { appliquerFiltre(); }
        });

        JButton btnClearSearch = createSmallButton(" X ", BG_FIELD, TEXT_MUTED, BORDER_NORMAL);
        btnClearSearch.setToolTipText("Effacer la recherche (Ctrl+F)");
        btnClearSearch.addActionListener(e -> {
            tfRecherche.setText("");
            tfRecherche.requestFocus();
        });

        searchPanel.add(lblSearch, BorderLayout.WEST);
        searchPanel.add(tfRecherche, BorderLayout.CENTER);
        searchPanel.add(btnClearSearch, BorderLayout.EAST);
        panel.add(searchPanel, BorderLayout.NORTH);

        // --- Table historique ---
        String[] colonnes = {"#", "Horodatage", "Analyste", "IP", "Type", "Criticite", "Symptomes"};
        tableModel = new DefaultTableModel(colonnes, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Integer.class : String.class;
            }
        };

        tableHistorique = new JTable(tableModel) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (isRowSelected(row)) {
                    c.setBackground(TABLE_SELECT);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(row % 2 == 0 ? TABLE_ROW_A : TABLE_ROW_B);
                    if (col == 5) {
                        String val = (String) getValueAt(row, col);
                        if ("Critique".equals(val))       c.setForeground(ACCENT_RED);
                        else if ("Moyen".equals(val))     c.setForeground(ACCENT_YELLOW);
                        else                              c.setForeground(ACCENT_GREEN);
                    } else {
                        c.setForeground(TEXT_PRIMARY);
                    }
                }
                // Gras pour la criticite
                if (c instanceof JLabel && col == 5) {
                    ((JLabel) c).setFont(getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        };

        // Trie dynamique des colonnes
        rowSorter = new TableRowSorter<>(tableModel);
        rowSorter.setSortable(0, false); // colonne # non triable
        tableHistorique.setRowSorter(rowSorter);

        tableHistorique.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tableHistorique.setRowHeight(24);
        tableHistorique.setBackground(TABLE_ROW_A);
        tableHistorique.setForeground(TEXT_PRIMARY);
        tableHistorique.setGridColor(new Color(40, 30, 80));
        tableHistorique.setSelectionBackground(TABLE_SELECT);
        tableHistorique.setSelectionForeground(Color.WHITE);
        tableHistorique.setShowVerticalLines(true);
        tableHistorique.setShowHorizontalLines(true);
        tableHistorique.setIntercellSpacing(new Dimension(1, 1));
        tableHistorique.getTableHeader().setReorderingAllowed(false);
        tableHistorique.getTableHeader().setBackground(TABLE_HEADER);
        tableHistorique.getTableHeader().setForeground(ACCENT_VIOLET);
        tableHistorique.getTableHeader().setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        tableHistorique.getTableHeader().setBorder(
                BorderFactory.createLineBorder(BORDER_NORMAL, 1));
        tableHistorique.setToolTipText("Cliquez sur un en-tete pour trier | Clic droit pour modifier/supprimer");

        int[] widths = {35, 145, 120, 120, 100, 75, 200};
        for (int i = 0; i < widths.length && i < tableHistorique.getColumnCount(); i++)
            tableHistorique.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        // Clic sur une ligne -> detail
        tableHistorique.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) afficherDetail();
        });

        // Double-clic -> modifier
        tableHistorique.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    modifierIncidentSelection();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) montrerMenuContextuel(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) montrerMenuContextuel(e);
            }
        });

        JScrollPane tableScroll = new JScrollPane(tableHistorique);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER_NORMAL, 1));
        tableScroll.getViewport().setBackground(TABLE_ROW_A);
        tableScroll.setPreferredSize(new Dimension(760, 220));

        // --- Zone de detail ---
        taDetail = new JTextArea(7, 60);
        taDetail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        taDetail.setForeground(ACCENT_GREEN);
        taDetail.setBackground(new Color(10, 8, 28));
        taDetail.setEditable(false);
        taDetail.setBorder(new EmptyBorder(8, 10, 8, 10));
        taDetail.setText("  Selectionnez un incident dans le tableau pour afficher les details...");

        JScrollPane detailScroll = new JScrollPane(taDetail);
        detailScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_NORMAL, 1),
                "  Detail de l'incident  ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font(Font.MONOSPACED, Font.BOLD, 11), ACCENT_VIOLET));
        detailScroll.getViewport().setBackground(new Color(10, 8, 28));

        // --- Boutons de gestion ---
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnBar.setOpaque(false);

        btnImporter     = createButton("  Importer  ",   new Color(15, 25, 50), ACCENT_CYAN, ACCENT_CYAN);
        btnViderHisto   = createButton("  Vider  ",      new Color(60, 15, 15), ACCENT_RED, ACCENT_RED);
        btnExporterCSV  = createButton("  Export CSV  ",  new Color(15, 40, 15), ACCENT_GREEN, ACCENT_GREEN);
        btnExporterTXT  = createButton("  Export TXT  ",  new Color(30, 30, 15), ACCENT_YELLOW, ACCENT_YELLOW);

        btnImporter.setToolTipText("Importer des incidents depuis un fichier");
        btnViderHisto.setToolTipText("Effacer tout l'historique des incidents");
        btnExporterCSV.setToolTipText("Exporter l'historique au format CSV");
        btnExporterTXT.setToolTipText("Exporter l'historique au format texte");

        btnImporter.addActionListener(e -> importerHistorique());
        btnViderHisto.addActionListener(e -> viderHistorique());
        btnExporterCSV.addActionListener(e -> exporterCSV());
        btnExporterTXT.addActionListener(e -> exporterTXT());

        btnBar.add(btnImporter);
        btnBar.add(btnViderHisto);
        btnBar.add(btnExporterCSV);
        btnBar.add(btnExporterTXT);

        // --- Assemblage ---
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
        split.setDividerLocation(220);
        split.setBackground(BG_PANEL);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(6);

        // Panel intermediaire pour search + split
        JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(searchPanel, BorderLayout.NORTH);
        centerPanel.add(split, BorderLayout.CENTER);

        panel.add(statsBar,  BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(btnBar,     BorderLayout.SOUTH);
        return panel;
    }

    // =========================================================================
    // Menu contextuel (clic droit sur le tableau)
    // =========================================================================
    private void montrerMenuContextuel(MouseEvent e) {
        int row = tableHistorique.rowAtPoint(e.getPoint());
        if (row < 0) return;
        tableHistorique.setRowSelectionInterval(row, row);

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_FIELD);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_NORMAL, 1));

        JMenuItem miDetail  = new JMenuItem("Afficher le detail");
        JMenuItem miEdit    = new JMenuItem("Modifier cet incident");
        JMenuItem miDelete  = new JMenuItem("Supprimer cet incident");
        JMenuItem miCopyIP  = new JMenuItem("Copier l'adresse IP");

        for (JMenuItem mi : new JMenuItem[]{miDetail, miEdit, miDelete, miCopyIP}) {
            mi.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            mi.setForeground(TEXT_PRIMARY);
            mi.setBackground(BG_FIELD);
        }

        miDetail.addActionListener(ev -> afficherDetail());
        miEdit.addActionListener(ev -> modifierIncidentSelection());
        miDelete.addActionListener(ev -> supprimerIncidentSelection());
        miCopyIP.addActionListener(ev -> copierIPSelection());

        menu.add(miDetail);
        menu.addSeparator();
        menu.add(miEdit);
        menu.add(miDelete);
        menu.addSeparator();
        menu.add(miCopyIP);
        menu.show(tableHistorique, e.getX(), e.getY());
    }

    // =========================================================================
    // Carte de statistique
    // =========================================================================
    private JLabel buildStatCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout(0, 2)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_FIELD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        lblTitle.setForeground(TEXT_MUTED);

        JLabel lblValue = new JLabel(value, SwingConstants.CENTER);
        lblValue.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        lblValue.setForeground(color);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(lblValue, BorderLayout.CENTER);
        return lblValue;
    }

    // =========================================================================
    // Afficher detail de la ligne selectionnee
    // =========================================================================
    private void afficherDetail() {
        int viewRow = tableHistorique.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = tableHistorique.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= incidents.size()) return;

        Incident inc = incidents.get(modelRow);
        StringBuilder sb = new StringBuilder();
        sb.append("+--------------------------------------------------------------+\n");
        sb.append("  DETAIL DE L'INCIDENT #").append(modelRow + 1).append("\n");
        sb.append("+--------------------------------------------------------------+\n\n");
        sb.append("  Horodatage   : ").append(inc.horodatage).append("\n");
        sb.append("  Analyste     : ").append(inc.analyste).append("\n");
        sb.append("  Adresse IP   : ").append(inc.ip).append("\n");
        sb.append("  Type         : ").append(inc.type).append("\n");
        sb.append("  Criticite    : ").append(inc.criticite).append("\n\n");
        sb.append("  Symptomes    :\n").append(inc.symptomes);
        sb.append("  Description  :\n  ");
        sb.append(inc.description.isEmpty() ? "(Aucune description)" : inc.description);

        taDetail.setText(sb.toString());
        taDetail.setCaretPosition(0);
    }

    // =========================================================================
    // Supprimer l'incident selectionne
    // =========================================================================
    private void supprimerIncidentSelection() {
        int viewRow = tableHistorique.getSelectedRow();
        if (viewRow < 0) {
            afficherStatut("Aucun incident selectionne.", ACCENT_ORANGE);
            return;
        }
        int modelRow = tableHistorique.convertRowIndexToModel(viewRow);
        Incident inc = incidents.get(modelRow);

        int rep = JOptionPane.showConfirmDialog(this,
                "Supprimer l'incident #" + (modelRow + 1) + " ?\n\n"
              + "Analyste  : " + inc.analyste + "\n"
              + "IP        : " + inc.ip + "\n"
              + "Type      : " + inc.type + "\n"
              + "Criticite : " + inc.criticite,
                "Confirmer la suppression",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (rep == JOptionPane.YES_OPTION) {
            incidents.remove(modelRow);
            reconstruireTable();
            mettreAJourStats();
            sauvegarderDonnees();
            afficherStatut("Incident #" + (modelRow + 1) + " supprime.", ACCENT_RED);
        }
    }

    // =========================================================================
    // Copier l'IP de l'incident selectionne
    // =========================================================================
    private void copierIPSelection() {
        int viewRow = tableHistorique.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = tableHistorique.convertRowIndexToModel(viewRow);
        String ip = incidents.get(modelRow).ip;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                  .setContents(new StringSelection(ip), null);
            afficherStatut("IP copiee dans le presse-papiers : " + ip, ACCENT_GREEN);
        } catch (Exception ex) {
            afficherStatut("Impossible de copier l'IP.", ACCENT_RED);
        }
    }

    // =========================================================================
    // Modifier l'incident selectionne (remplir le formulaire)
    // =========================================================================
    private void modifierIncidentSelection() {
        int viewRow = tableHistorique.getSelectedRow();
        if (viewRow < 0) {
            afficherStatut("Aucun incident selectionne.", ACCENT_ORANGE);
            return;
        }
        int modelRow = tableHistorique.convertRowIndexToModel(viewRow);
        Incident inc = incidents.get(modelRow);

        // Passer en mode edition
        editingIndex = modelRow;

        // Remplir les champs
        tfAnalyste.setText(inc.analyste);
        tfIP.setText(inc.ip);

        // Selectionner le type
        for (int i = 0; i < cbTypeIncident.getItemCount(); i++) {
            if (cbTypeIncident.getItemAt(i).equals(inc.type)) {
                cbTypeIncident.setSelectedIndex(i);
                break;
            }
        }

        // Selectionner la criticite
        if ("Faible".equals(inc.criticite))       rbFaible.setSelected(true);
        else if ("Moyen".equals(inc.criticite))   rbMoyen.setSelected(true);
        else if ("Critique".equals(inc.criticite)) rbCritique.setSelected(true);

        // Cocher les symptomes
        cbReseau.setSelected(inc.symptomes.contains("Activite reseau suspecte"));
        cbConnexion.setSelected(inc.symptomes.contains("Tentatives de connexion echouees"));
        cbFichiers.setSelected(inc.symptomes.contains("Fichiers chiffres"));
        cbProcessus.setSelected(inc.symptomes.contains("Processus inconnus"));

        taDescription.setText(inc.description);

        // Mettre a jour l'UI
        onCriticiteChanged();
        updateEditModeUI();

        // Basculer vers l'onglet declaration
        tabbedPane.setSelectedIndex(0);
        tfAnalyste.requestFocus();

        afficherStatut("Modification de l'incident #" + (modelRow + 1) + " en cours...", ACCENT_CYAN);
    }

    // =========================================================================
    // Mettre a jour l'UI du mode edition
    // =========================================================================
    private void updateEditModeUI() {
        if (editingIndex >= 0) {
            lblEditMode.setText("  >> MODE EDITION -- Incident #" + (editingIndex + 1)
                              + "  |  Ctrl+S pour sauvegarder  |  Echap pour annuler  <<");
            lblEditMode.setVisible(true);
            btnSignaler.setText("  Mettre a jour  ");
            btnSignaler.setToolTipText("Sauvegarder les modifications (Ctrl+S)");
            btnAnnulerEdit.setVisible(true);
            btnReinitialiser.setToolTipText("Annuler la modification (Echap)");
        } else {
            lblEditMode.setVisible(false);
            btnSignaler.setText("  Signaler  ");
            btnSignaler.setToolTipText("Enregistrer l'incident (Ctrl+Entree)");
            btnAnnulerEdit.setVisible(false);
            btnReinitialiser.setToolTipText("Effacer tous les champs du formulaire (Ctrl+R)");
        }
    }

    // =========================================================================
    // Vider l'historique
    // =========================================================================
    private void viderHistorique() {
        if (incidents.isEmpty()) {
            afficherStatut("L'historique est deja vide.", ACCENT_ORANGE);
            return;
        }
        int rep = JOptionPane.showConfirmDialog(this,
                "Voulez-vous vraiment effacer tout l'historique ?\n"
              + "Cette action est irreversible.\n\n"
              + "Nombre d'incidents : " + incidents.size(),
                "Confirmer la suppression", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (rep == JOptionPane.YES_OPTION) {
            incidents.clear();
            tableModel.setRowCount(0);
            compteurIncidents = 0;
            lblCompteur.setText("Total incidents : 0");
            taDetail.setText("  Historique efface.");
            mettreAJourStats();
            sauvegarderDonnees();
            afficherStatut("Historique efface -- " + compteurIncidents + " incidents supprimes.", ACCENT_RED);
        }
    }

    // =========================================================================
    // Exporter en CSV
    // =========================================================================
    private void exporterCSV() {
        if (incidents.isEmpty()) {
            afficherStatut("Aucun incident a exporter.", ACCENT_ORANGE);
            JOptionPane.showMessageDialog(this, "Aucun incident a exporter.",
                    "Historique vide", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("historique_incidents.csv"));
        fc.setFileFilter(new FileNameExtensionFilter("Fichiers CSV", "csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            pw.println("N;Horodatage;Analyste;IP;Type;Criticite;Symptomes;Description");
            for (int i = 0; i < incidents.size(); i++) {
                Incident inc = incidents.get(i);
                pw.println((i + 1) + ";"
                    + csvEscape(inc.horodatage) + ";"
                    + csvEscape(inc.analyste) + ";"
                    + csvEscape(inc.ip) + ";"
                    + csvEscape(inc.type) + ";"
                    + csvEscape(inc.criticite) + ";"
                    + csvEscape(inc.symptomes.replace("\n", " | ").trim()) + ";"
                    + csvEscape(inc.description));
            }
            afficherStatut("Export CSV reussi : " + fc.getSelectedFile().getName(), ACCENT_GREEN);
            JOptionPane.showMessageDialog(this,
                    "Export CSV reussi !\n" + fc.getSelectedFile().getAbsolutePath(),
                    "Export reussi", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            afficherStatut("Erreur lors de l'export CSV.", ACCENT_RED);
            JOptionPane.showMessageDialog(this,
                    "Erreur lors de l'export : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // Exporter en TXT
    // =========================================================================
    private void exporterTXT() {
        if (incidents.isEmpty()) {
            afficherStatut("Aucun incident a exporter.", ACCENT_ORANGE);
            JOptionPane.showMessageDialog(this, "Aucun incident a exporter.",
                    "Historique vide", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("historique_incidents.txt"));
        fc.setFileFilter(new FileNameExtensionFilter("Fichiers texte", "txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            pw.println("=== RAPPORT SOC -- Historique des Incidents ===");
            pw.println("Exporte le : " + LocalDateTime.now().format(DTF));
            pw.println("==================================================");
            pw.println();
            for (int i = 0; i < incidents.size(); i++) {
                Incident inc = incidents.get(i);
                pw.println("INCIDENT #" + (i + 1));
                pw.println("  Horodatage  : " + inc.horodatage);
                pw.println("  Analyste    : " + inc.analyste);
                pw.println("  IP          : " + inc.ip);
                pw.println("  Type        : " + inc.type);
                pw.println("  Criticite   : " + inc.criticite);
                pw.println("  Symptomes   : " + inc.symptomes.replace("\n","").trim());
                pw.println("  Description : " + inc.description);
                pw.println("--------------------------------------------------");
            }
            afficherStatut("Export TXT reussi : " + fc.getSelectedFile().getName(), ACCENT_GREEN);
            JOptionPane.showMessageDialog(this,
                    "Export TXT reussi !\n" + fc.getSelectedFile().getAbsolutePath(),
                    "Export reussi", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            afficherStatut("Erreur lors de l'export TXT.", ACCENT_RED);
            JOptionPane.showMessageDialog(this,
                    "Erreur lors de l'export : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // Importer depuis fichier
    // =========================================================================
    private void importerHistorique() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Fichiers SOC (.dat)", "dat"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        int count = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                Incident inc = Incident.deserialize(line);
                if (inc != null) {
                    incidents.add(inc);
                    count++;
                }
            }
            if (count > 0) {
                compteurIncidents = incidents.size();
                lblCompteur.setText("Total incidents : " + compteurIncidents);
                reconstruireTable();
                mettreAJourStats();
                sauvegarderDonnees();
                afficherStatut(count + " incidents importes depuis " + file.getName(), ACCENT_GREEN);
                JOptionPane.showMessageDialog(this,
                        count + " incidents importes avec succes !",
                        "Import reussi", JOptionPane.INFORMATION_MESSAGE);
            } else {
                afficherStatut("Aucun incident valide dans le fichier.", ACCENT_ORANGE);
                JOptionPane.showMessageDialog(this,
                        "Le fichier ne contient aucun incident valide.",
                        "Import echoue", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            afficherStatut("Erreur lors de l'import.", ACCENT_RED);
            JOptionPane.showMessageDialog(this,
                    "Erreur lors de l'import : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // Echapper un champ CSV
    // =========================================================================
    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // =========================================================================
    // Mise a jour des statistiques
    // =========================================================================
    private void mettreAJourStats() {
        int total = incidents.size();
        int crit = 0, moyen = 0, faible = 0;
        for (Incident inc : incidents) {
            switch (inc.criticite) {
                case "Critique": crit++;   break;
                case "Moyen":    moyen++;  break;
                default:         faible++; break;
            }
        }
        lblStatTotal.setText(String.valueOf(total));
        lblStatCritique.setText(String.valueOf(crit));
        lblStatMoyen.setText(String.valueOf(moyen));
        lblStatFaible.setText(String.valueOf(faible));
    }

    // =========================================================================
    // Reconstruire le tableau a partir de la liste
    // =========================================================================
    private void reconstruireTable() {
        tableModel.setRowCount(0);
        for (int i = 0; i < incidents.size(); i++) {
            Incident inc = incidents.get(i);
            String sympCourt = inc.symptomes
                    .replace("  - ", "").replace("\n", " / ").trim();
            if (sympCourt.endsWith("/"))
                sympCourt = sympCourt.substring(0, sympCourt.length() - 1).trim();
            tableModel.addRow(new Object[]{
                i + 1, inc.horodatage, inc.analyste, inc.ip,
                inc.type, inc.criticite, sympCourt
            });
        }
    }

    // =========================================================================
    // Appliquer le filtre de recherche
    // =========================================================================
    private void appliquerFiltre() {
        String query = tfRecherche.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter(
                "(?i)" + java.util.regex.Pattern.quote(query)));
        }
    }

    // =========================================================================
    // RadioButtons criticite
    // =========================================================================
    private JPanel buildRadioPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 2));
        panel.setOpaque(false);
        bgCriticite = new ButtonGroup();
        rbFaible   = createRadioButton("Faible",   ACCENT_GREEN,  "Incident a faible impact");
        rbMoyen    = createRadioButton("Moyen",    ACCENT_YELLOW, "Incident a impact modere");
        rbCritique = createRadioButton("Critique", ACCENT_RED,    "Incident critique -- intervention immediate requise");
        bgCriticite.add(rbFaible);
        bgCriticite.add(rbMoyen);
        bgCriticite.add(rbCritique);
        ActionListener al = e -> onCriticiteChanged();
        rbFaible.addActionListener(al);
        rbMoyen.addActionListener(al);
        rbCritique.addActionListener(al);
        panel.add(rbFaible);
        panel.add(rbMoyen);
        panel.add(rbCritique);
        return panel;
    }

    // =========================================================================
    // CheckBoxes symptomes
    // =========================================================================
    private JPanel buildCheckPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 14, 4));
        panel.setOpaque(false);
        cbReseau    = createCheckBox("Activite reseau suspecte",
                                     "Traffic anormal ou connexion vers des adresses suspectes");
        cbConnexion = createCheckBox("Tentatives de connexion echouees",
                                     "Echecs d'authentification repetes");
        cbFichiers  = createCheckBox("Fichiers chiffres",
                                     "Fichiers modifies ou chiffres par un ransomware");
        cbProcessus = createCheckBox("Processus inconnus",
                                     "Processus non identifies en cours d'execution");
        panel.add(cbReseau);
        panel.add(cbConnexion);
        panel.add(cbFichiers);
        panel.add(cbProcessus);
        return panel;
    }

    // =========================================================================
    // Boutons declaration
    // =========================================================================
    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        panel.setOpaque(false);

        btnAnnulerEdit = createButton("  Annuler  ", BG_FIELD, TEXT_MUTED, BORDER_NORMAL);
        btnAnnulerEdit.setToolTipText("Annuler la modification et revenir au mode creation (Echap)");
        btnAnnulerEdit.setVisible(false);
        btnAnnulerEdit.addActionListener(e -> annulerEdition());

        btnReinitialiser = createButton("  Reinitialiser  ", BG_FIELD, TEXT_MUTED, BORDER_NORMAL);
        btnReinitialiser.setToolTipText("Effacer tous les champs du formulaire (Ctrl+R)");
        btnSignaler = createButton("  Signaler  ", ACCENT_PURPLE, Color.WHITE, ACCENT_VIOLET);
        btnSignaler.setToolTipText("Enregistrer l'incident (Ctrl+Entree)");

        btnReinitialiser.addActionListener(e -> {
            if (editingIndex >= 0) annulerEdition();
            else reinitialiser();
        });
        btnSignaler.addActionListener(e -> signalerOuMettreAJour());

        panel.add(btnAnnulerEdit);
        panel.add(btnReinitialiser);
        panel.add(btnSignaler);

        // btnAnnulerEdit est deja un champ de classe
        return panel;
    }

    // =========================================================================
    // Pied de page avec barre de statut
    // =========================================================================
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BORDER_NORMAL);
                g2.drawLine(0, 0, getWidth(), 0);
                g2.dispose();
            }
        };
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(6, 24, 8, 24));

        // Cote gauche : info + statut
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(false);

        JLabel lblInfo = new JLabel("SOC Platform v4.0  |  Confidentiel -- Usage interne uniquement");
        lblInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        lblInfo.setForeground(TEXT_MUTED);

        lblStatut = new JLabel(" Pret ");
        lblStatut.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        lblStatut.setForeground(ACCENT_GREEN);

        leftPanel.add(lblInfo, BorderLayout.NORTH);
        leftPanel.add(lblStatut, BorderLayout.SOUTH);

        // Cote droit : TLP + persistance
        JPanel rightPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        rightPanel.setOpaque(false);

        JLabel lblData = new JLabel("Auto-save : ON");
        lblData.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        lblData.setForeground(ACCENT_GREEN);
        lblData.setToolTipText("Les incidents sont sauvegardes automatiquement dans " + DATA_FILE);

        JLabel lblTlp = new JLabel("TLP:RED");
        lblTlp.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        lblTlp.setForeground(ACCENT_RED);

        rightPanel.add(lblData);
        rightPanel.add(lblTlp);

        footer.add(leftPanel,  BorderLayout.WEST);
        footer.add(rightPanel, BorderLayout.EAST);
        return footer;
    }

    // =========================================================================
    // Barre de statut -- notification temporaire
    // =========================================================================
    private void afficherStatut(String message, Color color) {
        if (lblStatut != null) {
            lblStatut.setText(" " + message);
            lblStatut.setForeground(color);
            // Revenir au statut par defaut apres 6 secondes
            Timer t = new Timer(6000, e -> {
                lblStatut.setText(" Pret ");
                lblStatut.setForeground(ACCENT_GREEN);
            });
            t.setRepeats(false);
            t.start();
        }
    }

    // =========================================================================
    // Horloge
    // =========================================================================
    private void startClock() {
        Timer timer = new Timer(1000, e -> {
            String now = LocalDateTime.now().format(DTF);
            if (lblDateTime != null) lblDateTime.setText(now);
        });
        timer.setInitialDelay(0);
        timer.start();
    }

    // =========================================================================
    // Raccourcis clavier globaux
    // =========================================================================
    private void installerRaccourcis() {
        // Ctrl+Entree : signaler / mettre a jour
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "signaler");

        // Ctrl+S : signaler / mettre a jour
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "signaler");
        getRootPane().getActionMap().put("signaler", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (tabbedPane.getSelectedIndex() == 0) signalerOuMettreAJour();
            }
        });

        // Ctrl+R : reinitialiser
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), "reinit");
        getRootPane().getActionMap().put("reinit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (editingIndex >= 0) annulerEdition();
                else reinitialiser();
            }
        });

        // Ctrl+F : focus recherche
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "recherche");
        getRootPane().getActionMap().put("recherche", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                tabbedPane.setSelectedIndex(1);
                tfRecherche.requestFocus();
                tfRecherche.selectAll();
            }
        });

        // Escape : annuler edition ou deselectionner
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "echap");
        getRootPane().getActionMap().put("echap", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (editingIndex >= 0) {
                    annulerEdition();
                } else if (tabbedPane.getSelectedIndex() == 1) {
                    tableHistorique.clearSelection();
                    tfRecherche.setText("");
                }
            }
        });

        // Suppr : supprimer incident selectionne
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "supprimer");
        getRootPane().getActionMap().put("supprimer", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (tabbedPane.getSelectedIndex() == 1 && tableHistorique.getSelectedRow() >= 0) {
                    supprimerIncidentSelection();
                }
            }
        });
    }

    // =========================================================================
    // Logique : Signaler ou Mettre a jour (selon mode)
    // =========================================================================
    private void signalerOuMettreAJour() {
        if (editingIndex >= 0) {
            mettreAJourIncident();
        } else {
            signalerIncident();
        }
    }

    // =========================================================================
    // Logique : Signaler un nouvel incident
    // =========================================================================
    private void signalerIncident() {
        // Validation
        if (!validerFormulaire()) return;

        // Construction
        String analyste    = tfAnalyste.getText().trim();
        String ip          = tfIP.getText().trim();
        String typeIncident = (cbTypeIncident.getSelectedIndex() == 0)
                ? "(Non specifie)" : cbTypeIncident.getSelectedItem().toString();
        String criticite   = rbFaible.isSelected() ? "Faible"
                           : rbMoyen.isSelected()  ? "Moyen" : "Critique";

        StringBuilder symptomes = new StringBuilder();
        if (cbReseau.isSelected())    symptomes.append("  - Activite reseau suspecte\n");
        if (cbConnexion.isSelected()) symptomes.append("  - Tentatives de connexion echouees\n");
        if (cbFichiers.isSelected())  symptomes.append("  - Fichiers chiffres\n");
        if (cbProcessus.isSelected()) symptomes.append("  - Processus inconnus\n");

        String description = taDescription.getText().trim();
        String horodatage  = LocalDateTime.now().format(DTF);

        Incident inc = new Incident(analyste, ip, typeIncident, criticite,
                                    symptomes.toString(), description, horodatage);
        incidents.add(inc);
        compteurIncidents++;

        ajouterLigneTable(inc, incidents.size());
        lblCompteur.setText("Total incidents : " + compteurIncidents);
        mettreAJourStats();
        sauvegarderDonnees();

        JOptionPane.showMessageDialog(this,
                "Incident #" + compteurIncidents + " enregistre avec succes !\n\n"
              + "Analyste  : " + analyste + "\n"
              + "IP        : " + ip + "\n"
              + "Type      : " + typeIncident + "\n"
              + "Criticite : " + criticite + "\n"
              + "Heure     : " + horodatage,
                "Incident signale", JOptionPane.INFORMATION_MESSAGE);

        afficherStatut("Incident #" + compteurIncidents + " enregistre -- " + criticite, getStatutColor(criticite));
        reinitialiser();
        tabbedPane.setSelectedIndex(1);
    }

    // =========================================================================
    // Logique : Mettre a jour un incident existant
    // =========================================================================
    private void mettreAJourIncident() {
        if (!validerFormulaire()) return;
        if (editingIndex < 0 || editingIndex >= incidents.size()) {
            annulerEdition();
            return;
        }

        String analyste    = tfAnalyste.getText().trim();
        String ip          = tfIP.getText().trim();
        String typeIncident = (cbTypeIncident.getSelectedIndex() == 0)
                ? "(Non specifie)" : cbTypeIncident.getSelectedItem().toString();
        String criticite   = rbFaible.isSelected() ? "Faible"
                           : rbMoyen.isSelected()  ? "Moyen" : "Critique";

        StringBuilder symptomes = new StringBuilder();
        if (cbReseau.isSelected())    symptomes.append("  - Activite reseau suspecte\n");
        if (cbConnexion.isSelected()) symptomes.append("  - Tentatives de connexion echouees\n");
        if (cbFichiers.isSelected())  symptomes.append("  - Fichiers chiffres\n");
        if (cbProcessus.isSelected()) symptomes.append("  - Processus inconnus\n");

        String description = taDescription.getText().trim();
        String horodatage  = incidents.get(editingIndex).horodatage; // conserver l'horodatage original

        Incident inc = new Incident(analyste, ip, typeIncident, criticite,
                                    symptomes.toString(), description, horodatage);
        incidents.set(editingIndex, inc);

        reconstruireTable();
        mettreAJourStats();
        sauvegarderDonnees();

        afficherStatut("Incident #" + (editingIndex + 1) + " mis a jour.", ACCENT_GREEN);
        JOptionPane.showMessageDialog(this,
                "Incident #" + (editingIndex + 1) + " mis a jour avec succes.",
                "Modification enregistree", JOptionPane.INFORMATION_MESSAGE);

        annulerEdition();
        tabbedPane.setSelectedIndex(1);
    }

    // =========================================================================
    // Annuler le mode edition
    // =========================================================================
    private void annulerEdition() {
        editingIndex = -1;
        reinitialiser();
        afficherStatut("Mode edition annule.", ACCENT_ORANGE);
    }

    // =========================================================================
    // Validation du formulaire
    // =========================================================================
    private boolean validerFormulaire() {
        // Reinitialiser les bordures
        resetFieldBorders();

        String analyste = tfAnalyste.getText().trim();
        String ip       = tfIP.getText().trim();

        // Nom analyste
        if (analyste.isEmpty()) {
            setFieldError(tfAnalyste, "Le nom de l'analyste est obligatoire.");
            tfAnalyste.requestFocus();
            return false;
        }
        if (analyste.length() < 2) {
            setFieldError(tfAnalyste, "Le nom doit contenir au moins 2 caracteres.");
            tfAnalyste.requestFocus();
            return false;
        }

        // IP
        if (ip.isEmpty()) {
            setFieldError(tfIP, "L'adresse IP est obligatoire.");
            tfIP.requestFocus();
            return false;
        }
        if (!isValidIP(ip)) {
            setFieldError(tfIP, "Format invalide. Ex: 192.168.1.10 (4 blocs 0-255)");
            tfIP.requestFocus();
            return false;
        }

        // Criticite
        if (bgCriticite.getSelection() == null) {
            JOptionPane.showMessageDialog(this,
                    "Veuillez selectionner un niveau de criticite.",
                    "Criticite manquante", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // Symptomes
        if (!cbReseau.isSelected() && !cbConnexion.isSelected()
                && !cbFichiers.isSelected() && !cbProcessus.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    "Veuillez cocher au moins un symptome detecte.",
                    "Symptome manquant", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        return true;
    }

    // =========================================================================
    // Validation visuelle des champs
    // =========================================================================
    private void setFieldError(JTextField tf, String message) {
        tf.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER_ERROR, 2),
                new EmptyBorder(6, 10, 6, 10)));
        tf.setToolTipText(message);
        afficherStatut("Erreur : " + message, ACCENT_RED);
    }

    private void resetFieldBorders() {
        resetFieldBorder(tfAnalyste);
        resetFieldBorder(tfIP);
    }

    private void resetFieldBorder(JTextField tf) {
        tf.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER_NORMAL, 1),
                new EmptyBorder(6, 10, 6, 10)));
    }

    // =========================================================================
    // Couleur de statut selon criticite
    // =========================================================================
    private Color getStatutColor(String criticite) {
        switch (criticite) {
            case "Critique": return ACCENT_RED;
            case "Moyen":    return ACCENT_YELLOW;
            case "Faible":   return ACCENT_GREEN;
            default:         return ACCENT_VIOLET;
        }
    }

    // =========================================================================
    // Ajouter une ligne au tableau
    // =========================================================================
    private void ajouterLigneTable(Incident inc, int num) {
        String sympCourt = inc.symptomes
                .replace("  - ", "").replace("\n", " / ").trim();
        if (sympCourt.endsWith("/"))
            sympCourt = sympCourt.substring(0, sympCourt.length() - 1).trim();
        tableModel.addRow(new Object[]{
            num, inc.horodatage, inc.analyste, inc.ip,
            inc.type, inc.criticite, sympCourt
        });
    }

    // =========================================================================
    // Logique : Reinitialiser
    // =========================================================================
    private void reinitialiser() {
        tfAnalyste.setText("");
        tfIP.setText("");
        cbTypeIncident.setSelectedIndex(0);
        bgCriticite.clearSelection();
        cbReseau.setSelected(false);
        cbConnexion.setSelected(false);
        cbFichiers.setSelected(false);
        cbProcessus.setSelected(false);
        taDescription.setText("");
        currentCriticite = "none";
        editingIndex = -1;
        resetFieldBorders();
        applyTheme();
        updateEditModeUI();
        tfAnalyste.requestFocus();
    }

    // =========================================================================
    // Changement de criticite -> fond dynamique
    // =========================================================================
    private void onCriticiteChanged() {
        if (rbCritique.isSelected())      currentCriticite = "Critique";
        else if (rbMoyen.isSelected())    currentCriticite = "Moyen";
        else if (rbFaible.isSelected())   currentCriticite = "Faible";
        else                              currentCriticite = "none";
        applyTheme();
    }

    private void applyTheme() {
        Color borderColor;
        switch (currentCriticite) {
            case "Critique": borderColor = ACCENT_RED;    break;
            case "Moyen":    borderColor = ACCENT_YELLOW; break;
            case "Faible":   borderColor = ACCENT_GREEN;  break;
            default:         borderColor = BORDER_NORMAL; break;
        }
        tabbedPane.setBorder(BorderFactory.createLineBorder(borderColor, 2));
        rootPanel.repaint();
        getContentPane().repaint();
    }

    // =========================================================================
    // Validation IP (robuste)
    // =========================================================================
    private boolean isValidIP(String ip) {
        if (ip == null || ip.trim().isEmpty()) return false;
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty()) return false;
            // Pas de zeros en debut (ex: "01", "001") sauf "0"
            if (part.length() > 1 && part.charAt(0) == '0') return false;
            try {
                int val = Integer.parseInt(part.trim());
                if (val < 0 || val > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Persistance : Sauvegarder
    // =========================================================================
    private void sauvegarderDonnees() {
        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("[SOC] Impossible de creer le repertoire : " + DATA_DIR);
                return;
            }
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(DATA_FILE), StandardCharsets.UTF_8))) {
                // En-tete
                pw.println("# SOC Incident Database v4.0");
                pw.println("# Fichier genere automatiquement -- ne pas modifier manuellement");
                pw.println("# Date de derniere sauvegarde : " + LocalDateTime.now().format(DTF));
                pw.println("# Nombre d'incidents : " + incidents.size());
                pw.println("#");
                for (Incident inc : incidents) {
                    pw.println(inc.serialize());
                }
            }
        } catch (Exception ex) {
            System.err.println("[SOC] Erreur de sauvegarde : " + ex.getMessage());
        }
    }

    // =========================================================================
    // Persistance : Charger
    // =========================================================================
    private void chargerDonnees() {
        File file = new File(DATA_FILE);
        if (!file.exists() || !file.canRead()) return;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int loaded = 0;
            while ((line = br.readLine()) != null) {
                // Ignorer les commentaires
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                Incident inc = Incident.deserialize(line);
                if (inc != null) {
                    incidents.add(inc);
                    loaded++;
                }
            }
            if (loaded > 0) {
                compteurIncidents = incidents.size();
                lblCompteur.setText("Total incidents : " + compteurIncidents);
                reconstruireTable();
                mettreAJourStats();
                afficherStatut(loaded + " incidents charges depuis la sauvegarde.", ACCENT_GREEN);
            }
        } catch (Exception ex) {
            System.err.println("[SOC] Erreur de chargement : " + ex.getMessage());
            afficherStatut("Erreur de chargement des donnees sauvegardees.", ACCENT_ORANGE);
        }
    }

    // =========================================================================
    // Fabriques de composants
    // =========================================================================
    private JTextField createTextField(final String placeholder) {
        JTextField tf = new JTextField(26) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(placeholder, 10,
                            (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g2.dispose();
                }
            }
        };
        tf.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        tf.setForeground(TEXT_PRIMARY);
        tf.setBackground(BG_FIELD);
        tf.setCaretColor(ACCENT_VIOLET);
        tf.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(BORDER_NORMAL, 1),
                new EmptyBorder(6, 10, 6, 10)));
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                tf.setBorder(new CompoundBorder(
                        BorderFactory.createLineBorder(BORDER_FOCUS, 1),
                        new EmptyBorder(6, 10, 6, 10)));
            }
            @Override public void focusLost(FocusEvent e) {
                resetFieldBorder(tf);
            }
        });
        return tf;
    }

    private JComboBox<String> createComboBox(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        cb.setForeground(TEXT_PRIMARY);
        cb.setBackground(BG_FIELD);
        cb.setBorder(BorderFactory.createLineBorder(BORDER_NORMAL, 1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? ACCENT_PURPLE : BG_FIELD);
                setForeground(isSelected ? Color.WHITE    : TEXT_PRIMARY);
                setBorder(new EmptyBorder(4, 10, 4, 10));
                return this;
            }
        });
        return cb;
    }

    private JRadioButton createRadioButton(String text, Color color, String tooltip) {
        JRadioButton rb = new JRadioButton(text);
        rb.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        rb.setForeground(color);
        rb.setOpaque(false);
        rb.setFocusPainted(false);
        rb.setToolTipText(tooltip);
        return rb;
    }

    private JCheckBox createCheckBox(String text, String tooltip) {
        JCheckBox cb = new JCheckBox(text);
        cb.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        cb.setForeground(TEXT_PRIMARY);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.setToolTipText(tooltip);
        cb.addItemListener(e -> cb.setForeground(cb.isSelected() ? ACCENT_VIOLET : TEXT_PRIMARY));
        return cb;
    }

    private JButton createButton(String text, Color bg, Color fg, Color border) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getModel().isPressed()  ? bg.darker()   :
                             getModel().isRollover() ? bg.brighter() : bg;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        btn.setForeground(fg);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(9, 20, 9, 20));
        return btn;
    }

    private JButton createSmallButton(String text, Color bg, Color fg, Color border) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Color fill = getModel().isPressed()  ? bg.darker()   :
                             getModel().isRollover() ? bg.brighter() : bg;
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        btn.setForeground(fg);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(5, 10, 5, 10));
        return btn;
    }

    // =========================================================================
    // Aides GridBag
    // =========================================================================
    private void addLabel(JPanel f, GridBagConstraints gbc, int row, String text, Color color, String tooltip) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        lbl.setForeground(color);
        lbl.setPreferredSize(new Dimension(200, 28));
        lbl.setToolTipText(tooltip);
        f.add(lbl, gbc);
    }

    private void addField(JPanel f, GridBagConstraints gbc, int row, JComponent field) {
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        f.add(field, gbc);
    }

    private void addSeparator(JPanel f, GridBagConstraints gbc, int row) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_NORMAL);
        f.add(sep, gbc);
        gbc.gridwidth = 1;
    }

    // =========================================================================
    // Main
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Look & Feel
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) {
                try {
                    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                } catch (Exception ex) { ex.printStackTrace(); }
            }

            // Couleurs globales pour les dialogues
            UIManager.put("OptionPane.background",         BG_PANEL);
            UIManager.put("Panel.background",              BG_PANEL);
            UIManager.put("OptionPane.messageForeground",  TEXT_PRIMARY);
            UIManager.put("OptionPane.messageFont",
                    new Font(Font.MONOSPACED, Font.PLAIN, 12));
            UIManager.put("OptionPane.buttonFont",
                    new Font(Font.MONOSPACED, Font.BOLD, 12));
            UIManager.put("TabbedPane.selected",   BG_TAB_ACTIVE);
            UIManager.put("TabbedPane.background", BG_TAB_IDLE);
            UIManager.put("TabbedPane.foreground", TEXT_PRIMARY);
            UIManager.put("Button.background",     BG_FIELD);
            UIManager.put("Button.foreground",     TEXT_PRIMARY);
            UIManager.put("Button.focus",          ACCENT_VIOLET);
            UIManager.put("ToggleButton.background", BG_FIELD);

            // Lancer l'application
            new SOCIncidentManager();
        });
    }
}
