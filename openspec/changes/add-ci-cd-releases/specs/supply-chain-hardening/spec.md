## ADDED Requirements

### Requirement: All third-party GitHub Actions are pinned by commit SHA
Every third-party GitHub Action referenced in any workflow SHALL be pinned to a full 40-character
commit SHA (a human-readable version tag MAY follow as a trailing comment). Mutable references such as
floating tags or branch names SHALL NOT be used to invoke third-party actions.

#### Scenario: A workflow uses a SHA-pinned action
- **WHEN** any workflow file under `.github/workflows/` is inspected
- **THEN** every `uses:` of a third-party action references a full commit SHA
- **AND** no `uses:` references a third-party action by a floating tag or branch name

#### Scenario: An unpinned action is rejected
- **WHEN** a workflow change introduces a third-party action pinned by tag or branch instead of a SHA
- **THEN** review/linting flags it as a supply-chain violation before merge

### Requirement: Dependabot keeps actions and Gradle dependencies updated
The repository SHALL include a `.github/dependabot.yml` configuring Dependabot for both the
`github-actions` and `gradle` package ecosystems, so action SHAs and build/app dependencies receive
automated update pull requests.

#### Scenario: Dependabot config covers both ecosystems
- **WHEN** `.github/dependabot.yml` is inspected
- **THEN** it declares an update entry for the `github-actions` ecosystem
- **AND** it declares an update entry for the `gradle` ecosystem
- **AND** the file is valid YAML accepted by Dependabot

#### Scenario: A pinned action becomes outdated
- **WHEN** a newer release exists for a SHA-pinned action
- **THEN** Dependabot opens a pull request updating that action's SHA

### Requirement: Workflows and shell scripts are linted by actionlint and shellcheck
CI SHALL run `actionlint` to lint all GitHub Actions workflow YAML and `shellcheck` to lint all shell
(both standalone helper scripts and inline `run:` shell in workflows). A lint failure SHALL fail the
relevant CI job.

#### Scenario: actionlint validates workflows
- **WHEN** CI runs on a workflow change
- **THEN** `actionlint` runs against the workflow files
- **AND** an invalid workflow causes the lint job to fail

#### Scenario: shellcheck validates shell
- **WHEN** CI runs and the repository contains shell scripts or inline shell in workflows
- **THEN** `shellcheck` runs against that shell
- **AND** a shellcheck error causes the lint job to fail

### Requirement: Every workflow uses least-privilege permissions
Each workflow SHALL declare an explicit `permissions` block, defaulting to read-only
(`contents: read`) and elevating only the specific jobs that require more (the publish job to
`contents: write`; the release-please job to `contents: write` and `pull-requests: write`). No
workflow SHALL rely on the default broad token permissions.

#### Scenario: Default token is read-only
- **WHEN** a workflow that neither publishes releases nor runs release-please is inspected
- **THEN** its effective permissions are `contents: read` with no write scopes

#### Scenario: Write scope is limited to jobs that need it
- **WHEN** the release/publish or release-please workflow is inspected
- **THEN** only the job that creates the Release or the release PR holds write permissions
- **AND** other jobs in the same workflow remain read-only
