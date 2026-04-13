## ADDED Requirements

### Requirement: User can create job from conversation
After AI conversation generates SQL, the system SHALL allow users to save the SQL as an ETL job.

#### Scenario: Save job after SQL generation
- **WHEN** user clicks "Save as Job" button in workbench
- **THEN** system creates a new job with all SQL steps

### Requirement: User can view job list
The system SHALL display all jobs for a project in a list view.

#### Scenario: View job list
- **WHEN** user navigates to job list page
- **THEN** system shows paginated list of jobs with name, status, step count

#### Scenario: Search jobs by keyword
- **WHEN** user enters search keyword
- **THEN** system filters jobs by job name

### Requirement: User can view job details
The system SHALL display complete job information including all steps.

#### Scenario: View job detail
- **WHEN** user clicks on a job in list
- **THEN** system shows job name, description, all steps with SQL

### Requirement: User can edit job step SQL
The system SHALL allow users to modify SQL for any job step.

#### Scenario: Edit step SQL
- **WHEN** user clicks edit button on a step
- **THEN** Monaco editor opens with the SQL content

### Requirement: User can delete job
The system SHALL allow users to delete unwanted jobs.

#### Scenario: Delete job
- **WHEN** user clicks delete and confirms
- **THEN** system removes the job and all its steps
