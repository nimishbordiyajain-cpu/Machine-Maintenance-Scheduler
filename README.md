# Machine Maintenance Scheduler

**Case Study No. 139 | Java Programming**
B.Tech CSE (2025-29), Semester III | School of Future Tech, ITM Skills University

**Author:** Nimish Bordiya (Roll No. 150096725027)

A Java Swing desktop application that helps a factory maintain preventive-maintenance schedules for machines, record service tasks, assign technicians, and track breakdown and repair history.

---

## Problem Statement

A factory wants to maintain preventive-maintenance schedules for machines, record service tasks, assign technicians, and track breakdown and repair history.

## Features

| Module (tab) | What it does |
|---|---|
| **Machines** | Add, update and search machines; manage status (Active, Under Repair, Out of Service) |
| **Technicians** | Add, update and search technicians (phone and specialization) |
| **Maintenance Tasks** | Schedule a task for a machine, assign a technician, set a due date, see days left, filter by status, mark as completed |
| **Repairs** | Report a breakdown, then record the repair |
| **History** | Combined maintenance and repair history for one machine or all machines |
| **Summary** | Nine live counts (machines, technicians, tasks, overdue tasks, open breakdowns, etc.) |

Other behaviour:
- A task becomes **Overdue** automatically when its due date passes.
- Reporting a breakdown sets the machine to **Under Repair**. Recording the repair sets it back to **Active** (if no other breakdown is open).
- All data is **saved automatically** to a text file and loaded again on the next start.

## Java Concepts Used

| Concept | Where |
|---|---|
| **OOP** (abstract class, inheritance, encapsulation) | `BaseRecord` is the parent of `Machine` and `Technician` |
| **Interfaces** | `Identifiable`, `RecordManager` |
| **HashMap** | `MapManager` stores machines and technicians by ID for fast lookup |
| **ArrayList** | Maintenance tasks and repair records |
| **Exception handling** | Custom `MaintenanceException` |
| **File handling** | Save and load `maintenance_data.txt` |
| **Swing GUI** | `MainFrame` with tabs, forms and tables |

## Requirements

- JDK 8 or later (any recent JDK works)
- No external libraries

## How to Run

1. Put `MachineMaintenanceScheduler.java` in a folder.
2. Open a terminal in that folder and run:

```bash
javac MachineMaintenanceScheduler.java
java MachineMaintenanceScheduler
```

3. Add at least one **machine** and one **technician** first, then schedule tasks and record breakdowns and repairs.

> The file `maintenance_data.txt` is created in the folder you run the program from. To start fresh, close the app and delete that file.

## Validation Rules

- Machine and technician IDs must be unique
- ID, name, location, task description and issue cannot be empty
- Technician phone must be exactly 10 digits
- Dates must use the format `yyyy-MM-dd` (example: `2026-10-15`)
- A task due date cannot be in the past
- A task cannot be scheduled for a machine that is Out of Service
- A breakdown date cannot be in the future
- A repair date cannot be before the breakdown date or in the future
- A technician must be selected for a task or repair
- A completed task or repaired breakdown cannot be completed again

Invalid input shows a popup message instead of crashing the program.

## Project Structure

Everything is in a single file for easy submission and running:

```
MachineMaintenanceScheduler.java
 |-- MachineMaintenanceScheduler   main class (starts the app)
 |-- MaintenanceException          custom exception
 |-- Identifiable, RecordManager   interfaces
 |-- BaseRecord, Machine, Technician, MaintenanceTask, RepairRecord   model classes
 |-- MapManager, MachineManager, TechnicianManager   HashMap-based managers
 |-- MaintenanceService            business rules, history, summary, file save/load
 `-- MainFrame                     Swing GUI (all tabs)
```

## Data File Format

One record per line, fields separated by `|`:

```
MACHINE|id|name|location|status
TECH|id|name|phone|specialization
TASK|id|machineId|techId|description|dueDate|completed|completedDate
REPAIR|id|machineId|issue|breakdownDate|repaired|techId|details|repairDate
```

A `|` typed in a text field is saved as `/`. A damaged line is skipped and the rest of the file still loads.

## Limitations

- A task's due date cannot be edited after it is scheduled
- Tasks and repairs can be filtered by status but have no text search (machines and technicians do)
- Data is stored in a text file, not a database

## Possible Future Improvements

- Reschedule tasks and search tasks and repairs
- Email or SMS reminders for due dates
- Export reports to PDF
- Database storage (for example MySQL)
