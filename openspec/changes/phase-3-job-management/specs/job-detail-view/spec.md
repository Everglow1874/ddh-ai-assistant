## ADDED Requirements

### Requirement: Job detail shows step flow
The job detail page SHALL display steps in a visual flow.

#### Scenario: View step flow
- **WHEN** user opens job detail
- **THEN** steps are displayed in order with connecting lines

### Requirement: Each step shows SQL content
Each step SHALL display its DDL and DML SQL.

#### Scenario: View step SQL
- **WHEN** user expands a step
- **THEN** DDL and DML SQL are displayed

### Requirement: Steps are collapsible
Users SHALL be able to collapse/expand individual steps.

#### Scenario: Toggle step collapse
- **WHEN** user clicks step header
- **THEN** step content collapses or expands
