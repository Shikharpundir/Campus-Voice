
# Campus Voice

### Speak Up. Track Progress. Find What Matters.

**Campus Voice** is a unified university platform for managing **campus complaints and Lost & Found services**.

It provides students with a structured way to report problems, track their resolution, and search for lost or found belongings, while giving university administrators a centralized system to manage and resolve campus issues.

The project is built around a multi-language architecture combining **Java, SQL, and C**, where each technology has a specific role in the system.

---

## The Idea

A university is a large environment where hundreds of problems can arise every day.

A broken classroom facility, hostel maintenance issue, network problem, sanitation complaint, or lost personal belonging can easily get buried in messages, emails, or informal communication.

**Campus Voice creates one place for all of them.**

Students can raise an issue and follow its journey from:

**Report → Priority → Assignment → Processing → Resolution**

At the same time, the Lost & Found system provides a centralized way to report and discover belongings lost across campus.

---

## What Campus Voice Offers

### 🗣️ Campus Complaints

Students can submit complaints with relevant information such as category, location, description, priority, and date.

Administrators can:

* Review incoming complaints
* Prioritize urgent issues
* Assign complaints to departments
* Update progress
* Mark issues as resolved
* Maintain complete complaint history

This creates a transparent connection between the person reporting a problem and the department responsible for solving it.

---

### 🧳 Lost & Found

Campus Voice also addresses a simple but common campus problem — **lost belongings**.

Students can report both:

**Lost Items**
Items that a student has misplaced.

**Found Items**
Items discovered somewhere on campus.

Users can search records using relevant information such as item name, category, location, date, and description to identify possible matches.

The objective is simple:

> **Make something lost easier to find.**

---

# ⚙️ Technology Architecture

Campus Voice intentionally uses different technologies for different responsibilities.

### Java — Core Application

Java acts as the primary application layer and handles:

* Application logic
* User operations
* Complaint management
* Lost & Found operations
* Authentication
* Database communication
* Communication with OS modules

### SQL — Data Management

The relational database manages the persistent information of the platform.

It stores:

* Students
* Administrators
* Departments
* Complaints
* Complaint history
* Lost items
* Found items
* Status and priority information

Java communicates with the database using **JDBC**.

```text
Java Application
       ↓
      JDBC
       ↓
   SQL Database
```

### C — Operating System Layer

C is used to implement Operating System concepts rather than duplicating the Java application.

OS-related modules can include:

* Process management
* CPU scheduling
* Priority scheduling
* FCFS
* Round Robin
* Synchronization
* Resource management
* Deadlock-related simulations

For example, complaints can be treated as tasks and processed according to their priority.

```text
Incoming Complaints
        ↓
   C Scheduler
        ↓
Priority / FCFS / RR
        ↓
Processing Order
        ↓
Java Application
```

This allows the project to demonstrate how **Operating System concepts can be applied to a real-world software system**.

---

# 🗄️ DBMS Concepts

The database component demonstrates practical relational database concepts including:

* Relational schema design
* Primary and foreign keys
* Relationships
* Normalization
* Constraints
* CRUD operations
* SQL queries
* Joins
* Aggregate functions
* Transactions
* Data integrity
* Search and filtering

The database acts as the central source of truth for the application.

---

# 🖥️ Operating System Concepts

The OS component focuses on how tasks and resources are handled efficiently.

### Process Management

Different application operations can be represented as independent tasks.

### CPU Scheduling

Complaint processing can use priority-based scheduling so urgent complaints are handled before routine issues.

### Multithreading

Multiple operations can execute concurrently where appropriate.

### Synchronization

Shared resources can be protected from race conditions when multiple operations access them simultaneously.

### Resource Management

The system can demonstrate controlled allocation and management of shared resources.

---

# 🔄 Complaint Lifecycle

```text
Student
   ↓
Submit Complaint
   ↓
Database
   ↓
Priority Assignment
   ↓
OS Scheduler
   ↓
Department Processing
   ↓
Status Updates
   ↓
Resolution
   ↓
Student
```

Every complaint therefore has a **trackable lifecycle rather than disappearing after submission**.

---

# 🔍 Lost & Found Lifecycle

```text
Lost / Found Report
        ↓
      Database
        ↓
      Search
        ↓
Possible Match
        ↓
Verification
        ↓
Item Returned
```

---

# 🎯 Project Goals

* Create a centralized campus complaint platform
* Improve transparency between students and administration
* Reduce dependency on manual complaint handling
* Prioritize important campus issues
* Maintain searchable complaint history
* Provide a centralized Lost & Found service
* Demonstrate real-world DBMS implementation
* Demonstrate Operating System concepts
* Integrate multiple programming languages through a meaningful architecture

---

# 🔮 Future Scope

Campus Voice can evolve into a larger **Smart Campus Platform** with:

* AI-based complaint categorization
* Automatic department assignment
* Intelligent priority prediction
* Real-time notifications
* Complaint analytics
* Campus issue heatmaps
* Image-based Lost & Found matching
* Mobile application
* Advanced search
* Role-based access control

---

## The Vision

Campus Voice is more than a complaint portal.

It is an attempt to create a **structured communication layer between students, campus services, and administration**.

Every complaint gets a place.
Every issue gets a status.
Every department gets accountability.
And every lost item gets a chance to be found.

### **Campus Voice**

**Speak Up. Track Progress. Find What Matters.**

