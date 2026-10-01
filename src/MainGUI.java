/*
 * ==========================================================================
 *  MACHINE MAINTENANCE SCHEDULER  (Case Study No. 139)
 * --------------------------------------------------------------------------
 *  How to run:
 *      javac MachineMaintenanceScheduler.java
 *      java MachineMaintenanceScheduler
 *
 *  Java concepts used (as required by the problem statement):
 *      1. OOP          -> abstract class, inheritance, encapsulation
 *      2. Interfaces   -> Identifiable, RecordManager
 *      3. HashMap      -> fast lookup of Machines and Technicians by ID
 *      4. ArrayList    -> dynamic lists of Maintenance Tasks and Repair Records
 *      5. Exceptions   -> MaintenanceException for invalid input / rule breaks
 *      5b. File I/O    -> all data is saved to 'maintenance_data.txt' automatically
 *      6. GUI          -> Java Swing
 *
 *  Modules:
 *      Machines | Technicians | Maintenance Tasks | Repairs | History | Summary
 * ==========================================================================
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* ======================================================================
 *  MAIN CLASS - starts the application
 * ====================================================================== */
public class MainGUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Use the nicer "Nimbus" look if available
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if (info.getName().equals("Nimbus")) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) { }
            new MainFrame().setVisible(true);
        });
    }
}

/* ======================================================================
 *  1. CUSTOM EXCEPTION
 *     Thrown whenever the user enters invalid data or breaks a business rule.
 * ====================================================================== */
class MaintenanceException extends Exception {
    MaintenanceException(String message) {
        super(message);
    }
}

/* ======================================================================
 *  2. INTERFACES  (reusable contracts)
 * ====================================================================== */

// Anything that has a unique ID
interface Identifiable {
    String getId();
}

// Contract for any class that manages a collection of records
interface RecordManager<T extends Identifiable> {
    void add(T item) throws MaintenanceException;      // create
    void update(T item) throws MaintenanceException;   // update
    T find(String id);                                 // fast lookup by ID
    List<T> getAll();                                  // list everything
    List<T> search(String keyword);                    // search
}

/* ======================================================================
 *  3. MODEL CLASSES  (OOP: abstract class + inheritance + encapsulation)
 * ====================================================================== */

// Parent class: common data of Machine and Technician (ID + name)
abstract class BaseRecord implements Identifiable {
    private final String id;     // private = encapsulation
    private final String name;

    BaseRecord(String id, String name) throws MaintenanceException {
        // Validation: ID and name must not be empty
        if (id == null || id.trim().isEmpty())
            throw new MaintenanceException("ID cannot be empty.");
        if (name == null || name.trim().isEmpty())
            throw new MaintenanceException("Name cannot be empty.");
        this.id = id.trim().toUpperCase();
        this.name = name.trim();
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    // Two records are equal if their IDs are equal (helps combo boxes keep selection)
    @Override public boolean equals(Object o) {
        return o instanceof BaseRecord && ((BaseRecord) o).id.equals(id);
    }
    @Override public int hashCode() { return id.hashCode(); }

    // Text shown inside drop-down lists
    @Override public String toString() { return id + " - " + name; }
}

// Machine = BaseRecord + location + status
class Machine extends BaseRecord {
    private final String location;
    private String status;   // Active / Under Repair / Out of Service

    Machine(String id, String name, String location, String status) throws MaintenanceException {
        super(id, name);
        if (location == null || location.trim().isEmpty())
            throw new MaintenanceException("Location cannot be empty.");
        this.location = location.trim();
        this.status = status;
    }

    public String getLocation() { return location; }
    public String getStatus()   { return status; }
    public void setStatus(String status) { this.status = status; }
}

// Technician = BaseRecord + phone + specialization
class Technician extends BaseRecord {
    private final String phone;
    private final String specialization;

    Technician(String id, String name, String phone, String specialization) throws MaintenanceException {
        super(id, name);
        if (phone == null || !phone.trim().matches("\\d{10}"))
            throw new MaintenanceException("Phone number must be exactly 10 digits.");
        if (specialization == null || specialization.trim().isEmpty())
            throw new MaintenanceException("Specialization cannot be empty.");
        this.phone = phone.trim();
        this.specialization = specialization.trim();
    }

    public String getPhone()          { return phone; }
    public String getSpecialization() { return specialization; }
}

// A scheduled preventive-maintenance (service) task
class MaintenanceTask implements Identifiable {
    private final String id;
    private final String machineId;
    private final String technicianId;
    private final String description;
    private final LocalDate dueDate;
    private boolean completed = false;
    private LocalDate completedDate;

    MaintenanceTask(String id, String machineId, String technicianId, String description, LocalDate dueDate) {
        this.id = id;
        this.machineId = machineId;
        this.technicianId = technicianId;
        this.description = description;
        this.dueDate = dueDate;
    }

    public String getId()           { return id; }
    public String getMachineId()    { return machineId; }
    public String getTechnicianId() { return technicianId; }
    public String getDescription()  { return description; }
    public LocalDate getDueDate()   { return dueDate; }
    public boolean isCompleted()    { return completed; }
    public LocalDate getCompletedDate() { return completedDate; }

    public void markCompleted() {
        completed = true;
        completedDate = LocalDate.now();
    }

    // Used when loading from the text file
    public void restoreCompleted(LocalDate date) {
        completed = true;
        completedDate = date;
    }

    // Status is calculated automatically from the due date
    public String getStatus() {
        if (completed) return "Completed";
        if (dueDate.isBefore(LocalDate.now())) return "Overdue";
        return "Scheduled";
    }

    // Days remaining until the due date (negative = late)
    public long getDaysLeft() {
        return ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }
}

// A breakdown + its repair
class RepairRecord implements Identifiable {
    private final String id;
    private final String machineId;
    private final String issue;
    private final LocalDate breakdownDate;
    private String technicianId;
    private String repairDetails;
    private LocalDate repairDate;
    private boolean repaired = false;

    RepairRecord(String id, String machineId, String issue, LocalDate breakdownDate) {
        this.id = id;
        this.machineId = machineId;
        this.issue = issue;
        this.breakdownDate = breakdownDate;
    }

    public String getId()            { return id; }
    public String getMachineId()     { return machineId; }
    public String getIssue()         { return issue; }
    public LocalDate getBreakdownDate() { return breakdownDate; }
    public String getTechnicianId()  { return technicianId; }
    public String getRepairDetails() { return repairDetails; }
    public LocalDate getRepairDate() { return repairDate; }
    public boolean isRepaired()      { return repaired; }
    public String getStatus()        { return repaired ? "Repaired" : "Open"; }

    // Called when the machine has been repaired
    public void completeRepair(String technicianId, String details, LocalDate date) {
        this.technicianId = technicianId;
        this.repairDetails = details;
        this.repairDate = date;
        this.repaired = true;
    }
}

/* ======================================================================
 *  4. MANAGERS - use HashMap for fast key-based lookup
 * ====================================================================== */

// Generic manager that stores records in a HashMap (key = ID)
abstract class MapManager<T extends BaseRecord> implements RecordManager<T> {

    protected final HashMap<String, T> records = new HashMap<>();

    public void add(T item) throws MaintenanceException {
        if (records.containsKey(item.getId()))
            throw new MaintenanceException("ID '" + item.getId() + "' already exists. Use a unique ID.");
        records.put(item.getId(), item);
    }

    public void update(T item) throws MaintenanceException {
        if (!records.containsKey(item.getId()))
            throw new MaintenanceException("No record found with ID '" + item.getId() + "' to update.");
        records.put(item.getId(), item);
    }

    // HashMap gives us instant lookup by ID
    public T find(String id) {
        return id == null ? null : records.get(id.trim().toUpperCase());
    }

    public List<T> getAll() {
        List<T> list = new ArrayList<>(records.values());
        list.sort(Comparator.comparing(BaseRecord::getId));   // keep tidy order
        return list;
    }

    public List<T> search(String keyword) {
        String k = keyword.trim().toLowerCase();
        List<T> result = new ArrayList<>();
        for (T item : getAll()) {
            if (item.getId().toLowerCase().contains(k)
                    || item.getName().toLowerCase().contains(k)
                    || matchesExtra(item, k)) {
                result.add(item);
            }
        }
        return result;
    }

    // Each child class says which extra fields can be searched
    protected abstract boolean matchesExtra(T item, String keyword);
}

class MachineManager extends MapManager<Machine> {
    protected boolean matchesExtra(Machine m, String k) {
        return m.getLocation().toLowerCase().contains(k) || m.getStatus().toLowerCase().contains(k);
    }
}

class TechnicianManager extends MapManager<Technician> {
    protected boolean matchesExtra(Technician t, String k) {
        return t.getPhone().contains(k) || t.getSpecialization().toLowerCase().contains(k);
    }
}

/* ======================================================================
 *  5. SERVICE - all business rules live here (no GUI code)
 * ====================================================================== */
class MaintenanceService {

    final MachineManager machines = new MachineManager();
    final TechnicianManager technicians = new TechnicianManager();

    // ArrayLists: dynamic collections of records
    final ArrayList<MaintenanceTask> tasks = new ArrayList<>();
    final ArrayList<RepairRecord> repairs = new ArrayList<>();

    private int taskCounter = 1;     // used for auto IDs: T001, T002...
    private int repairCounter = 1;   // R001, R002...

    // ---------- helper checks ----------
    private Machine requireMachine(String id) throws MaintenanceException {
        Machine m = machines.find(id);
        if (m == null) throw new MaintenanceException("Please select a valid machine (add one in the Machines tab first).");
        return m;
    }

    private Technician requireTechnician(String id) throws MaintenanceException {
        Technician t = technicians.find(id);
        if (t == null) throw new MaintenanceException("Please select a valid technician (add one in the Technicians tab first).");
        return t;
    }

    private void requireText(String text, String fieldName) throws MaintenanceException {
        if (text == null || text.trim().isEmpty())
            throw new MaintenanceException(fieldName + " cannot be empty.");
    }

    // ---------- Maintenance tasks ----------
    public void scheduleTask(String machineId, String techId, String description, LocalDate due)
            throws MaintenanceException {
        Machine m = requireMachine(machineId);
        requireTechnician(techId);
        requireText(description, "Task description");
        if (due.isBefore(LocalDate.now()))
            throw new MaintenanceException("Due date cannot be in the past.");
        if (m.getStatus().equals("Out of Service"))
            throw new MaintenanceException("Cannot schedule maintenance for a machine that is Out of Service.");

        String id = String.format("T%03d", taskCounter++);
        tasks.add(new MaintenanceTask(id, m.getId(), techId, description.trim(), due));
    }

    public void completeTask(String taskId) throws MaintenanceException {
        for (MaintenanceTask t : tasks) {
            if (t.getId().equals(taskId)) {
                if (t.isCompleted()) throw new MaintenanceException("This task is already completed.");
                t.markCompleted();
                return;
            }
        }
        throw new MaintenanceException("Task not found.");
    }

    // ---------- Breakdown & repair ----------
    public void reportBreakdown(String machineId, String issue, LocalDate date) throws MaintenanceException {
        Machine m = requireMachine(machineId);
        requireText(issue, "Breakdown issue");
        if (date.isAfter(LocalDate.now()))
            throw new MaintenanceException("Breakdown date cannot be in the future.");

        String id = String.format("R%03d", repairCounter++);
        repairs.add(new RepairRecord(id, m.getId(), issue.trim(), date));
        m.setStatus("Under Repair");     // machine status changes automatically
    }

    public void completeRepair(String repairId, String techId, String details, LocalDate date)
            throws MaintenanceException {
        RepairRecord r = null;
        for (RepairRecord x : repairs) if (x.getId().equals(repairId)) r = x;
        if (r == null) throw new MaintenanceException("Please select a breakdown from the table first.");
        if (r.isRepaired()) throw new MaintenanceException("This breakdown is already repaired.");

        requireTechnician(techId);
        requireText(details, "Repair details");
        if (date.isBefore(r.getBreakdownDate()))
            throw new MaintenanceException("Repair date cannot be before the breakdown date.");
        if (date.isAfter(LocalDate.now()))
            throw new MaintenanceException("Repair date cannot be in the future.");

        r.completeRepair(techId, details.trim(), date);

        // If the machine has no other open breakdowns, make it Active again
        Machine m = machines.find(r.getMachineId());
        boolean stillOpen = false;
        for (RepairRecord x : repairs) {
            if (x.getMachineId().equals(m.getId()) && !x.isRepaired()) stillOpen = true;
        }
        if (!stillOpen && m.getStatus().equals("Under Repair")) m.setStatus("Active");
    }

    // ---------- Maintenance history ----------
    // Each row: {date, machine, type, details, technician, status}
    public List<Object[]> getHistory(String machineIdOrNull) {
        List<Object[]> rows = new ArrayList<>();

        for (MaintenanceTask t : tasks) {
            if (machineIdOrNull != null && !t.getMachineId().equals(machineIdOrNull)) continue;
            LocalDate date = t.isCompleted() ? t.getCompletedDate() : t.getDueDate();
            rows.add(new Object[]{date, machines.find(t.getMachineId()), "Scheduled Maintenance",
                    t.getDescription(), technicians.find(t.getTechnicianId()), t.getStatus()});
        }

        for (RepairRecord r : repairs) {
            if (machineIdOrNull != null && !r.getMachineId().equals(machineIdOrNull)) continue;
            String details = r.getIssue() + (r.isRepaired() ? "  >>  Fix: " + r.getRepairDetails() : "");
            Object tech = r.isRepaired() ? technicians.find(r.getTechnicianId()) : "-";
            LocalDate date = r.isRepaired() ? r.getRepairDate() : r.getBreakdownDate();
            rows.add(new Object[]{date, machines.find(r.getMachineId()), "Breakdown / Repair",
                    details, tech, r.getStatus()});
        }

        // newest first
        rows.sort((a, b) -> ((LocalDate) b[0]).compareTo((LocalDate) a[0]));
        return rows;
    }

    // ---------- Saving and loading (text file) ----------
    // Every record is stored as one line, fields separated by "|"
    //   MACHINE|id|name|location|status
    //   TECH|id|name|phone|specialization
    //   TASK|id|machineId|techId|description|dueDate|completed|completedDate
    //   REPAIR|id|machineId|issue|breakdownDate|repaired|techId|details|repairDate

    // Makes sure a text value cannot break our file format
    private String clean(String s) {
        return s == null ? "" : s.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }

    public void saveToFile(String path) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            for (Machine m : machines.getAll()) {
                out.println("MACHINE|" + clean(m.getId()) + "|" + clean(m.getName()) + "|"
                        + clean(m.getLocation()) + "|" + clean(m.getStatus()));
            }
            for (Technician t : technicians.getAll()) {
                out.println("TECH|" + clean(t.getId()) + "|" + clean(t.getName()) + "|"
                        + clean(t.getPhone()) + "|" + clean(t.getSpecialization()));
            }
            for (MaintenanceTask t : tasks) {
                out.println("TASK|" + t.getId() + "|" + t.getMachineId() + "|" + t.getTechnicianId() + "|"
                        + clean(t.getDescription()) + "|" + t.getDueDate() + "|" + t.isCompleted() + "|"
                        + (t.getCompletedDate() == null ? "" : t.getCompletedDate()));
            }
            for (RepairRecord r : repairs) {
                out.println("REPAIR|" + r.getId() + "|" + r.getMachineId() + "|" + clean(r.getIssue()) + "|"
                        + r.getBreakdownDate() + "|" + r.isRepaired() + "|"
                        + (r.getTechnicianId() == null ? "" : r.getTechnicianId()) + "|"
                        + clean(r.getRepairDetails()) + "|"
                        + (r.getRepairDate() == null ? "" : r.getRepairDate()));
            }
        }
    }

    public void loadFromFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) return;          // first run: no file yet, nothing to load

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split("\\|", -1);   // split the line at every "|"
                try {
                    switch (p[0]) {
                        case "MACHINE":
                            machines.add(new Machine(p[1], p[2], p[3], p[4]));
                            break;
                        case "TECH":
                            technicians.add(new Technician(p[1], p[2], p[3], p[4]));
                            break;
                        case "TASK": {
                            MaintenanceTask t = new MaintenanceTask(p[1], p[2], p[3], p[4], LocalDate.parse(p[5]));
                            if (p[6].equals("true")) t.restoreCompleted(LocalDate.parse(p[7]));
                            tasks.add(t);
                            break;
                        }
                        case "REPAIR": {
                            RepairRecord r = new RepairRecord(p[1], p[2], p[3], LocalDate.parse(p[4]));
                            if (p[5].equals("true")) r.completeRepair(p[6], p[7], LocalDate.parse(p[8]));
                            repairs.add(r);
                            break;
                        }
                        default:
                            break;
                    }
                } catch (Exception ex) {
                    // A damaged line is skipped so the rest of the data still loads
                }
            }
        }

        // Continue the auto-ID numbering after the loaded records
        taskCounter = tasks.size() + 1;
        repairCounter = repairs.size() + 1;
    }

    // ---------- Summary numbers ----------
    public Map<String, Integer> getSummary() {
        int active = 0, underRepair = 0, outOfService = 0;
        for (Machine m : machines.getAll()) {
            if (m.getStatus().equals("Active")) active++;
            else if (m.getStatus().equals("Under Repair")) underRepair++;
            else outOfService++;
        }
        int scheduled = 0, overdue = 0, completed = 0;
        for (MaintenanceTask t : tasks) {
            if (t.getStatus().equals("Scheduled")) scheduled++;
            else if (t.getStatus().equals("Overdue")) overdue++;
            else completed++;
        }
        int openBreakdowns = 0;
        for (RepairRecord r : repairs) if (!r.isRepaired()) openBreakdowns++;

        Map<String, Integer> s = new LinkedHashMap<>();   // keeps the order we insert
        s.put("Total Machines", machines.getAll().size());
        s.put("Active Machines", active);
        s.put("Under Repair", underRepair);
        s.put("Out of Service", outOfService);
        s.put("Technicians", technicians.getAll().size());
        s.put("Scheduled Tasks", scheduled);
        s.put("Overdue Tasks", overdue);
        s.put("Completed Tasks", completed);
        s.put("Open Breakdowns", openBreakdowns);
        return s;
    }
}

/* ======================================================================
 *  6. GUI - the main window (Swing)
 * ====================================================================== */
class MainFrame extends JFrame {

    // The service holds all data and rules
    private final MaintenanceService service = new MaintenanceService();

    // Colours used across the app
    private static final Color DARK  = new Color(33, 47, 61);
    private static final Color BLUE  = new Color(41, 128, 185);
    private static final Color GREEN = new Color(39, 174, 96);
    private static final Color RED   = new Color(192, 57, 43);
    private static final Color GREY  = new Color(127, 140, 141);
    private static final Color LIGHT = new Color(244, 246, 248);

    // File where all data is saved (created next to the program)
    private static final String DATA_FILE = "maintenance_data.txt";

    private static final String[] MACHINE_STATUS = {"Active", "Under Repair", "Out of Service"};

    // Used to stop listeners from reacting while we reload screens
    private boolean refreshing = false;

    // ---- Machines tab components ----
    private JTextField mId, mName, mLocation, mSearch;
    private JComboBox<String> mStatus;
    private DefaultTableModel mModel;
    private JTable mTable;

    // ---- Technicians tab components ----
    private JTextField tId, tName, tPhone, tSpec, tSearch;
    private DefaultTableModel tModel;
    private JTable tTable;

    // ---- Tasks tab components ----
    private JComboBox<Machine> kMachine;
    private JComboBox<Technician> kTech;
    private JTextField kDesc, kDue;
    private JComboBox<String> kFilter;
    private DefaultTableModel kModel;
    private JTable kTable;

    // ---- Repairs tab components ----
    private JComboBox<Machine> rMachine;
    private JTextField rIssue, rBreakDate, rDetails, rRepairDate;
    private JComboBox<Technician> rTech;
    private DefaultTableModel rModel;
    private JTable rTable;

    // ---- History tab components ----
    private JComboBox<Object> hMachine;
    private DefaultTableModel hModel;

    // ---- Summary tab components ----
    private final Map<String, JLabel> summaryLabels = new LinkedHashMap<>();

    MainFrame() {
        setTitle("Machine Maintenance Scheduler");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top banner
        JLabel banner = new JLabel("  Machine Maintenance Scheduler");
        banner.setFont(new Font("SansSerif", Font.BOLD, 24));
        banner.setForeground(Color.WHITE);
        banner.setOpaque(true);
        banner.setBackground(DARK);
        banner.setBorder(new EmptyBorder(14, 10, 14, 10));
        add(banner, BorderLayout.NORTH);

        // Tabs - one for each module
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabs.addTab("Machines", buildMachinesTab());
        tabs.addTab("Technicians", buildTechniciansTab());
        tabs.addTab("Maintenance Tasks", buildTasksTab());
        tabs.addTab("Repairs", buildRepairsTab());
        tabs.addTab("History", buildHistoryTab());
        tabs.addTab("Summary", buildSummaryTab());
        tabs.addChangeListener(e -> refreshAll());   // refresh data whenever a tab is opened
        add(tabs, BorderLayout.CENTER);

        // Load previously saved data (if the file exists)
        try {
            service.loadFromFile(DATA_FILE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not read " + DATA_FILE + ": " + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }

        refreshAll();
    }

    /* ------------------------------------------------------------------
     *  TAB 1: MACHINES  (create, update, search, status management)
     * ------------------------------------------------------------------ */
    private JPanel buildMachinesTab() {
        mId = new JTextField(10);
        mName = new JTextField(15);
        mLocation = new JTextField(15);
        mStatus = new JComboBox<>(MACHINE_STATUS);
        mSearch = new JTextField(20);

        JPanel form = formPanel("Machine Details");
        addField(form, 0, 0, "Machine ID:", mId);
        addField(form, 1, 0, "Name:", mName);
        addField(form, 0, 1, "Location:", mLocation);
        addField(form, 1, 1, "Status:", mStatus);

        JButton addBtn = button("Add Machine", GREEN);
        JButton updateBtn = button("Update Machine", BLUE);
        JButton clearBtn = button("Clear", GREY);

        addBtn.addActionListener(e -> safely(() -> {
            service.machines.add(new Machine(mId.getText(), mName.getText(), mLocation.getText(),
                    (String) mStatus.getSelectedItem()));
            clearMachineForm();
            refreshAll();
            info("Machine added successfully.");
        }));

        updateBtn.addActionListener(e -> safely(() -> {
            service.machines.update(new Machine(mId.getText(), mName.getText(), mLocation.getText(),
                    (String) mStatus.getSelectedItem()));
            clearMachineForm();
            refreshAll();
            info("Machine updated successfully.");
        }));

        clearBtn.addActionListener(e -> clearMachineForm());

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(buttonRow(addBtn, updateBtn, clearBtn), BorderLayout.SOUTH);

        // Table + search bar
        mModel = newModel("Machine ID", "Name", "Location", "Status");
        mTable = newTable(mModel);
        colorStatusColumn(mTable, 3);

        // Clicking a row copies it into the form (so it can be updated)
        mTable.getSelectionModel().addListSelectionListener(e -> {
            int row = mTable.getSelectedRow();
            if (refreshing || e.getValueIsAdjusting() || row < 0) return;
            mId.setText(str(mModel.getValueAt(row, 0)));
            mName.setText(str(mModel.getValueAt(row, 1)));
            mLocation.setText(str(mModel.getValueAt(row, 2)));
            mStatus.setSelectedItem(str(mModel.getValueAt(row, 3)));
        });

        JButton searchBtn = button("Search", BLUE);
        JButton showAllBtn = button("Show All", GREY);
        searchBtn.addActionListener(e -> fillMachineTable(service.machines.search(mSearch.getText())));
        showAllBtn.addActionListener(e -> { mSearch.setText(""); refreshAll(); });

        return tabLayout(top, searchBar("Search (ID / name / location / status):", mSearch, searchBtn, showAllBtn),
                new JScrollPane(mTable));
    }

    private void clearMachineForm() {
        mId.setText(""); mName.setText(""); mLocation.setText("");
        mStatus.setSelectedIndex(0);
        mTable.clearSelection();
    }

    private void fillMachineTable(List<Machine> list) {
        mModel.setRowCount(0);
        for (Machine m : list) {
            mModel.addRow(new Object[]{m.getId(), m.getName(), m.getLocation(), m.getStatus()});
        }
    }

    /* ------------------------------------------------------------------
     *  TAB 2: TECHNICIANS  (create, update, search)
     * ------------------------------------------------------------------ */
    private JPanel buildTechniciansTab() {
        tId = new JTextField(10);
        tName = new JTextField(15);
        tPhone = new JTextField(15);
        tSpec = new JTextField(15);
        tSearch = new JTextField(20);

        JPanel form = formPanel("Technician Details");
        addField(form, 0, 0, "Technician ID:", tId);
        addField(form, 1, 0, "Name:", tName);
        addField(form, 0, 1, "Phone (10 digits):", tPhone);
        addField(form, 1, 1, "Specialization:", tSpec);

        JButton addBtn = button("Add Technician", GREEN);
        JButton updateBtn = button("Update Technician", BLUE);
        JButton clearBtn = button("Clear", GREY);

        addBtn.addActionListener(e -> safely(() -> {
            service.technicians.add(new Technician(tId.getText(), tName.getText(), tPhone.getText(), tSpec.getText()));
            clearTechForm();
            refreshAll();
            info("Technician added successfully.");
        }));

        updateBtn.addActionListener(e -> safely(() -> {
            service.technicians.update(new Technician(tId.getText(), tName.getText(), tPhone.getText(), tSpec.getText()));
            clearTechForm();
            refreshAll();
            info("Technician updated successfully.");
        }));

        clearBtn.addActionListener(e -> clearTechForm());

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(buttonRow(addBtn, updateBtn, clearBtn), BorderLayout.SOUTH);

        tModel = newModel("Technician ID", "Name", "Phone", "Specialization");
        tTable = newTable(tModel);

        tTable.getSelectionModel().addListSelectionListener(e -> {
            int row = tTable.getSelectedRow();
            if (refreshing || e.getValueIsAdjusting() || row < 0) return;
            tId.setText(str(tModel.getValueAt(row, 0)));
            tName.setText(str(tModel.getValueAt(row, 1)));
            tPhone.setText(str(tModel.getValueAt(row, 2)));
            tSpec.setText(str(tModel.getValueAt(row, 3)));
        });

        JButton searchBtn = button("Search", BLUE);
        JButton showAllBtn = button("Show All", GREY);
        searchBtn.addActionListener(e -> fillTechTable(service.technicians.search(tSearch.getText())));
        showAllBtn.addActionListener(e -> { tSearch.setText(""); refreshAll(); });

        return tabLayout(top, searchBar("Search (ID / name / phone / specialization):", tSearch, searchBtn, showAllBtn),
                new JScrollPane(tTable));
    }

    private void clearTechForm() {
        tId.setText(""); tName.setText(""); tPhone.setText(""); tSpec.setText("");
        tTable.clearSelection();
    }

    private void fillTechTable(List<Technician> list) {
        tModel.setRowCount(0);
        for (Technician t : list) {
            tModel.addRow(new Object[]{t.getId(), t.getName(), t.getPhone(), t.getSpecialization()});
        }
    }

    /* ------------------------------------------------------------------
     *  TAB 3: MAINTENANCE TASKS  (schedule tasks, assign technician, track due dates)
     * ------------------------------------------------------------------ */
    private JPanel buildTasksTab() {
        kMachine = new JComboBox<>();
        kTech = new JComboBox<>();
        kDesc = new JTextField(25);
        kDue = new JTextField(LocalDate.now().plusDays(7).toString(), 10);   // default: 7 days from today
        kFilter = new JComboBox<>(new String[]{"All", "Scheduled", "Overdue", "Completed"});

        JPanel form = formPanel("Schedule Maintenance");
        addField(form, 0, 0, "Machine:", kMachine);
        addField(form, 1, 0, "Assign Technician:", kTech);
        addField(form, 0, 1, "Task Description:", kDesc);
        addField(form, 1, 1, "Due Date (yyyy-MM-dd):", kDue);

        JButton scheduleBtn = button("Schedule Task", GREEN);
        JButton completeBtn = button("Mark Selected as Completed", BLUE);

        scheduleBtn.addActionListener(e -> safely(() -> {
            Machine m = (Machine) kMachine.getSelectedItem();
            Technician t = (Technician) kTech.getSelectedItem();
            service.scheduleTask(m == null ? null : m.getId(), t == null ? null : t.getId(),
                    kDesc.getText(), parseDate(kDue.getText()));
            kDesc.setText("");
            refreshAll();
            info("Maintenance task scheduled.");
        }));

        completeBtn.addActionListener(e -> safely(() -> {
            int row = kTable.getSelectedRow();
            if (row < 0) throw new MaintenanceException("Please select a task from the table first.");
            service.completeTask(str(kModel.getValueAt(row, 0)));
            refreshAll();
            info("Task marked as completed.");
        }));

        kFilter.addActionListener(e -> { if (!refreshing) fillTaskTable(); });

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(buttonRow(scheduleBtn, completeBtn), BorderLayout.SOUTH);

        kModel = newModel("Task ID", "Machine", "Technician", "Description", "Due Date", "Days Left", "Status");
        kTable = newTable(kModel);
        colorStatusColumn(kTable, 6);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterBar.setBackground(LIGHT);
        filterBar.add(new JLabel("Show tasks:"));
        filterBar.add(kFilter);

        return tabLayout(top, filterBar, new JScrollPane(kTable));
    }

    private void fillTaskTable() {
        kModel.setRowCount(0);

        // Sort by due date so the nearest deadline appears first
        List<MaintenanceTask> sorted = new ArrayList<>(service.tasks);
        sorted.sort(Comparator.comparing(MaintenanceTask::getDueDate));

        String filter = (String) kFilter.getSelectedItem();
        for (MaintenanceTask t : sorted) {
            if (!filter.equals("All") && !t.getStatus().equals(filter)) continue;
            String daysLeft = t.isCompleted() ? "-" : String.valueOf(t.getDaysLeft());
            kModel.addRow(new Object[]{t.getId(), service.machines.find(t.getMachineId()),
                    service.technicians.find(t.getTechnicianId()), t.getDescription(),
                    t.getDueDate(), daysLeft, t.getStatus()});
        }
    }

    /* ------------------------------------------------------------------
     *  TAB 4: REPAIRS  (record breakdowns and repairs)
     * ------------------------------------------------------------------ */
    private JPanel buildRepairsTab() {
        rMachine = new JComboBox<>();
        rTech = new JComboBox<>();
        rIssue = new JTextField(20);
        rBreakDate = new JTextField(LocalDate.now().toString(), 10);
        rDetails = new JTextField(20);
        rRepairDate = new JTextField(LocalDate.now().toString(), 10);

        // Left box: report a breakdown
        JPanel breakForm = formPanel("1) Report a Breakdown");
        addField(breakForm, 0, 0, "Machine:", rMachine);
        addField(breakForm, 0, 1, "Issue:", rIssue);
        addField(breakForm, 0, 2, "Breakdown Date:", rBreakDate);
        JButton reportBtn = button("Report Breakdown", RED);
        breakForm.add(reportBtn, gbc(1, 3));

        // Right box: record a repair for the selected breakdown
        JPanel repairForm = formPanel("2) Record Repair (select a breakdown in the table)");
        addField(repairForm, 0, 0, "Technician:", rTech);
        addField(repairForm, 0, 1, "Repair Details:", rDetails);
        addField(repairForm, 0, 2, "Repair Date:", rRepairDate);
        JButton repairBtn = button("Mark as Repaired", GREEN);
        repairForm.add(repairBtn, gbc(1, 3));

        reportBtn.addActionListener(e -> safely(() -> {
            Machine m = (Machine) rMachine.getSelectedItem();
            service.reportBreakdown(m == null ? null : m.getId(), rIssue.getText(), parseDate(rBreakDate.getText()));
            rIssue.setText("");
            refreshAll();
            info("Breakdown recorded. Machine status changed to 'Under Repair'.");
        }));

        repairBtn.addActionListener(e -> safely(() -> {
            int row = rTable.getSelectedRow();
            if (row < 0) throw new MaintenanceException("Please select a breakdown from the table first.");
            Technician t = (Technician) rTech.getSelectedItem();
            service.completeRepair(str(rModel.getValueAt(row, 0)), t == null ? null : t.getId(),
                    rDetails.getText(), parseDate(rRepairDate.getText()));
            rDetails.setText("");
            refreshAll();
            info("Repair recorded.");
        }));

        JPanel top = new JPanel(new GridLayout(1, 2, 10, 0));
        top.add(breakForm);
        top.add(repairForm);

        rModel = newModel("Repair ID", "Machine", "Issue", "Breakdown Date", "Technician",
                "Repair Details", "Repair Date", "Status");
        rTable = newTable(rModel);
        colorStatusColumn(rTable, 7);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setBackground(LIGHT);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(rTable), BorderLayout.CENTER);
        return panel;
    }

    private void fillRepairTable() {
        rModel.setRowCount(0);
        for (RepairRecord r : service.repairs) {
            rModel.addRow(new Object[]{r.getId(), service.machines.find(r.getMachineId()), r.getIssue(),
                    r.getBreakdownDate(),
                    r.isRepaired() ? service.technicians.find(r.getTechnicianId()) : "-",
                    r.isRepaired() ? r.getRepairDetails() : "-",
                    r.isRepaired() ? r.getRepairDate() : "-",
                    r.getStatus()});
        }
    }

    /* ------------------------------------------------------------------
     *  TAB 5: HISTORY  (maintenance history per machine)
     * ------------------------------------------------------------------ */
    private JPanel buildHistoryTab() {
        hMachine = new JComboBox<>();
        hMachine.addActionListener(e -> { if (!refreshing) fillHistoryTable(); });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBackground(LIGHT);
        JLabel label = new JLabel("Select machine:");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        bar.add(label);
        bar.add(hMachine);

        hModel = newModel("Date", "Machine", "Type", "Details", "Technician", "Status");
        JTable table = newTable(hModel);
        colorStatusColumn(table, 5);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setBackground(LIGHT);
        panel.add(bar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void fillHistoryTable() {
        hModel.setRowCount(0);
        Object selected = hMachine.getSelectedItem();
        String machineId = (selected instanceof Machine) ? ((Machine) selected).getId() : null;  // null = all machines
        for (Object[] row : service.getHistory(machineId)) {
            hModel.addRow(row);
        }
    }

    /* ------------------------------------------------------------------
     *  TAB 6: SUMMARY  (useful application summaries)
     * ------------------------------------------------------------------ */
    private JPanel buildSummaryTab() {
        JPanel grid = new JPanel(new GridLayout(3, 3, 18, 18));
        grid.setBorder(new EmptyBorder(25, 25, 25, 25));
        grid.setBackground(LIGHT);

        // Create one "card" for each summary number
        for (String key : service.getSummary().keySet()) {
            JLabel value = new JLabel("0", SwingConstants.CENTER);
            value.setFont(new Font("SansSerif", Font.BOLD, 46));
            value.setForeground(cardColor(key));

            JLabel title = new JLabel(key, SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.PLAIN, 16));
            title.setForeground(GREY);

            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(Color.WHITE);
            card.setBorder(new CompoundBorder(new LineBorder(new Color(220, 224, 228), 1, true),
                    new EmptyBorder(15, 10, 15, 10)));
            card.add(value, BorderLayout.CENTER);
            card.add(title, BorderLayout.SOUTH);
            grid.add(card);

            summaryLabels.put(key, value);
        }
        return grid;
    }

    private Color cardColor(String key) {
        if (key.equals("Under Repair") || key.equals("Overdue Tasks") || key.equals("Open Breakdowns")) return RED;
        if (key.equals("Active Machines") || key.equals("Completed Tasks")) return GREEN;
        if (key.equals("Out of Service")) return GREY;
        return BLUE;
    }

    private void fillSummary() {
        Map<String, Integer> data = service.getSummary();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            summaryLabels.get(entry.getKey()).setText(String.valueOf(entry.getValue()));
        }
    }

    /* ------------------------------------------------------------------
     *  REFRESH - reloads every table, drop-down and summary from the data
     * ------------------------------------------------------------------ */
    private void refreshAll() {
        refreshing = true;   // stop listeners while we reload

        fillMachineTable(service.machines.getAll());
        fillTechTable(service.technicians.getAll());

        reloadCombo(kMachine, service.machines.getAll());
        reloadCombo(kTech, service.technicians.getAll());
        reloadCombo(rMachine, service.machines.getAll());
        reloadCombo(rTech, service.technicians.getAll());

        // History drop-down has an extra "All Machines" option at the top
        Object oldSelection = hMachine.getSelectedItem();
        hMachine.removeAllItems();
        hMachine.addItem("All Machines");
        for (Machine m : service.machines.getAll()) hMachine.addItem(m);
        if (oldSelection != null) hMachine.setSelectedItem(oldSelection);

        fillTaskTable();
        fillRepairTable();
        fillHistoryTable();
        fillSummary();

        refreshing = false;
    }

    // Reload a combo box but keep the previous selection if possible
    private <T> void reloadCombo(JComboBox<T> box, List<T> items) {
        Object old = box.getSelectedItem();
        box.removeAllItems();
        for (T item : items) box.addItem(item);
        if (old != null) box.setSelectedItem(old);
    }

    /* ------------------------------------------------------------------
     *  SMALL HELPER METHODS (to keep the code above short and readable)
     * ------------------------------------------------------------------ */

    // An action that may throw our custom exception
    private interface Action {
        void run() throws MaintenanceException;
    }

    // Runs an action and shows a friendly error message if something is invalid
    // After every successful action the data is saved to the text file
    private void safely(Action action) {
        try {
            action.run();
            service.saveToFile(DATA_FILE);
        } catch (MaintenanceException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Input", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save data: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void info(String message) {
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // Converts text like "2026-10-15" into a date (shows error if wrong format)
    private LocalDate parseDate(String text) throws MaintenanceException {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new MaintenanceException("Invalid date. Please use the format yyyy-MM-dd (example: 2026-10-15).");
        }
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    // Table model that the user cannot edit directly
    private DefaultTableModel newModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    // Styled table
    private JTable newTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setSelectionBackground(new Color(174, 214, 241));
        table.setSelectionForeground(Color.BLACK);
        table.setGridColor(new Color(225, 228, 232));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Dark header with white bold text
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                setOpaque(true);
                setBackground(DARK);
                setForeground(Color.WHITE);
                setFont(new Font("SansSerif", Font.BOLD, 13));
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return this;
            }
        });
        table.getTableHeader().setPreferredSize(new Dimension(100, 32));
        return table;
    }

    // Shows a status column in colour (red = problem, green = good, blue = pending)
    private void colorStatusColumn(JTable table, int column) {
        table.getColumnModel().getColumn(column).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                setFont(getFont().deriveFont(Font.BOLD));
                if (!selected) {
                    String s = String.valueOf(value);
                    if (s.equals("Overdue") || s.equals("Under Repair") || s.equals("Open")) setForeground(RED);
                    else if (s.equals("Completed") || s.equals("Repaired") || s.equals("Active")) setForeground(GREEN);
                    else if (s.equals("Out of Service")) setForeground(GREY);
                    else setForeground(BLUE);
                }
                return this;
            }
        });
    }

    // Coloured button
    private JButton button(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(b.getPreferredSize().width + 20, 34));
        return b;
    }

    private JPanel buttonRow(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        p.setBackground(LIGHT);
        for (JButton b : buttons) p.add(b);
        return p;
    }

    private JPanel searchBar(String label, JTextField field, JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setBackground(LIGHT);
        p.add(new JLabel(label));
        p.add(field);
        for (JButton b : buttons) p.add(b);
        return p;
    }

    // Form panel with a titled border
    private JPanel formPanel(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        TitledBorder border = BorderFactory.createTitledBorder(new LineBorder(new Color(200, 205, 210)), title);
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 14));
        p.setBorder(new CompoundBorder(border, new EmptyBorder(6, 8, 6, 8)));
        return p;
    }

    private GridBagConstraints gbc(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = (x % 2 == 1) ? 1.0 : 0;
        return c;
    }

    // Puts "label + field" into the form grid. col = 0 (left pair) or 1 (right pair)
    private void addField(JPanel form, int col, int row, String label, JComponent field) {
        form.add(new JLabel(label), gbc(col * 2, row));
        form.add(field, gbc(col * 2 + 1, row));
    }

    // Standard layout of a tab: form on top, search bar, then table
    private JPanel tabLayout(JPanel top, JPanel middleBar, JScrollPane table) {
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(LIGHT);
        center.add(middleBar, BorderLayout.NORTH);
        center.add(table, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setBackground(LIGHT);
        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }
}