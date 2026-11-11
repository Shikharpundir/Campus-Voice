# Campus Voice – Java Swing app

## Prerequisites
* JDK 11+ (compiled and syntax-checked with the JDK available in the build sandbox)
* MySQL 8.0.16+ with `database/schema.sql` and `sample_data.sql` loaded
  (for an existing database, run `database/migrate_global_admin_staff_departments.sql`
  and `database/migrate_complaint_attachments.sql` once each instead of recreating it)
* MySQL Connector/J jar (download from the MySQL website), e.g. `lib/mysql-connector-j-8.x.x.jar`
* The C executable built in `../os-module` (see root README)

## Configuration (environment variables – no secrets in source)
| Variable | Required | Default |
|---|---|---|
| `CV_DB_PASSWORD` | **yes** | – |
| `CV_DB_USER` | no | `root` |
| `CV_DB_URL` | no | `jdbc:mysql://localhost:3306/campus_voice?...` |
| `CV_OS_EXE` | no | `../os-module/campusvoice_os` (`.exe` added on Windows) |

If `CV_DB_PASSWORD` is not set, the app securely prompts for the MySQL password
once per launch (input is hidden) and reuses it for database connections during
that run. To avoid the prompt, set the variable in the CMD window before launching:

```
set CV_DB_PASSWORD=your_mysql_password
```

## Build and run – Windows (cmd)
```
cd java-app
set CV_DB_PASSWORD=your_password
set CV_OS_EXE=..\os-module\campusvoice_os.exe
mkdir out
javac -cp "lib\*" -d out src\*.java
java -cp "out;lib\*" Main
```
## Build and run – Linux / macOS / WSL
```
cd java-app
export CV_DB_PASSWORD=your_password
mkdir -p out && javac -cp "lib/*" -d out src/*.java
java -cp "out:lib/*" Main
```
Run from inside `java-app/` so the default relative path to the C executable works (or set `CV_OS_EXE`). To launch the older console menu instead, run `java -cp "out:lib/*" Main --console`.

## Authentication and complaint access
The Swing screen is the presentation/controller layer, `ComplaintService` enforces roles
and access rules, and `CampusVoiceDAO` performs the core authentication and complaint SQL
with `PreparedStatement`. `UserSession` holds the signed-in account until logout.
`validateUser(email, password)` returns the role and staff department after password-hash
verification. Students are routed to their own-complaints view and may submit to a selected
department. The single global admin sees all departments and can assign a complaint only to
staff belonging to that complaint's department. The admin creates pending staff records
with a name and email. On first-time setup, the staff member enters the same name/email,
creates a password, and selects a department; both values are saved to the account. Future
sign-ins use email/password and go directly to the staff portal. Staff see and update only
complaints assigned to their account. These access rules are checked in service queries and mutations, not only
in the UI. Until staff is assigned, student complaint views display the global admin as the
routed recipient.

The DAO exposes `getComplaintsByDepartment(String dept)` for explicit department queries,
`getComplaintsForAdmin(...)` for the global admin queue, and
`createComplaint(Complaint c)` for complaint creation with its initial history row in one
transaction. Staff selection derives the department from the complaint record, and the
assignment transaction verifies the staff member's department again before saving.

## Menu
* Students, the global admin, and staff use the same sign-in form; the app routes each account by its database role.
* Students can submit complaints from the Swing dashboard, optionally adding a photo from the system camera or image chooser. Images up to 10 MB are stored in the `complaint_attachments` table as image data, not in the complaint text.
* Students can view only their own complaint history/status.
* Double-click a complaint or select it and use **Complaint details / image** to see its complete details and attachment preview.
* The global administrator can assign open complaints across all departments, but only to staff belonging to the complaint's department. Staff can sign in to view and update only complaints assigned to their own account. Status changes and history entries are committed atomically.
* The global admin can create multiple pending staff logins with **Add Department Staff** by entering each staff member's name and email. Staff complete first-time setup by confirming that name/email, creating a password, and selecting a department.
* The scheduler panel runs priority scheduling with aging. Every three waiting days, effective priority improves one rank, up to URGENT. Age and effective priority are recalculated from `created_at`; a daily Swing refresh updates the dashboard automatically. The C scheduler also applies aging to accumulated real and simulated wait time.
* **Run Mutex / Race Demo** launches the C synchronization module from the Swing dashboard. Choose the mutex-protected or unprotected shared-counter run, then compare expected and actual updates in **OS Module Output**. This is an educational concurrency demo; it does not modify complaint or database records.
* Students can register from the login screen using their name, institutional email, and a password. The global administrator can use **View Staff Accounts** to see staff names, emails, departments, and whether first-time setup is pending or complete. The global administrator has a `NULL` `department_id`; pending staff have `NULL` password and department values until first-time setup.
* Existing sample-data accounts have placeholder passwords and cannot sign in. Register a real account with a new email address.

### Provision administrator and staff accounts
Build the app, then generate a password hash in CMD (type the password when prompted):

```
cd java-app
javac -cp "lib\*" -d out src\*.java
java -cp "out;lib\*" PasswordHashTool
```

Copy the printed hash and run this in MySQL Workbench, replacing the sample values:

```sql
USE campus_voice;
INSERT INTO users (name, email, password_hash, role, department_id)
VALUES ('Campus Administrator', 'campus-admin@campus.edu',
        'PASTE_GENERATED_HASH_HERE', 'ADMIN', NULL);
```

The global admin creates staff accounts in the dashboard by entering each person's name and
email. The staff member selects **First-Time Staff Setup** at the login screen, confirms the
same name/email, creates a password, and chooses a department. The department and password
are saved together. Future sign-ins use the email/password and go straight to the
assigned-complaints portal. The admin can assign a complaint only to a staff member in its
department.

For an existing database, run `database/migrate_global_admin_staff_departments.sql` once.
It clears the old admin department scope and incompatible existing complaint assignments.
Existing staff with no department become pending accounts and must complete first-time
setup by confirming their name/email, creating a password, and selecting a department.

### Password checks
Run the dependency-free password hashing checks on Windows from `java-app`:

```
javac -cp "out;lib\*" -d out test\PasswordUtilTest.java test\UserSessionTest.java test\ComplaintAgingTest.java
java -cp "out;lib\*" PasswordUtilTest
java -cp "out;lib\*" UserSessionTest
java -cp "out;lib\*" ComplaintAgingTest
```

## Java ↔ C protocol
Java starts `campusvoice_os sched <fcfs|priority|both>`, writes lines `pid complaint_id arrival burst priority aging_days` to its stdin, closes stdin, reads stdout (stderr merged). The Swing **Run Mutex / Race Demo** button also launches `campusvoice_os sync` or `campusvoice_os race` and displays the output in the OS module panel. Non-zero exit code or 30 s timeout → error shown to the user. The C executable also accepts the prior five-number scheduling format, with initial age defaulting to zero.

## Troubleshooting
* `CV_DB_PASSWORD is not set` → set the variable in the same terminal you run Java from.
* `No suitable driver` / `ClassNotFoundException` → Connector/J jar is not on the `-cp` path.
* `Access denied` → wrong user/password; `Unknown database` → run `schema.sql`.
* `C executable not found` → build `os-module` or set `CV_OS_EXE` to the full path.
* Option 4 prints "no open complaints" → every complaint is RESOLVED/REJECTED; submit a new one.
