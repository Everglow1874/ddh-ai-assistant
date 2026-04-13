## ADDED Requirements

### Requirement: User can export job as ZIP
The system SHALL export job as a ZIP file containing SQL files.

#### Scenario: Export job
- **WHEN** user clicks export button
- **THEN** system downloads a ZIP file with all SQL files

### Requirement: Exported ZIP contains correct structure
The exported ZIP SHALL contain properly organized SQL files.

#### Scenario: ZIP contains DDL and DML files
- **WHEN** job is exported
- **THEN** ZIP contains separate DDL and DML files for each step

#### Scenario: ZIP contains README
- **WHEN** job is exported
- **THEN** ZIP contains a README.txt with job description
