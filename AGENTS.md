# AGENTS.md

## Commit and Push

By default, after completing a coding task, stage the intended changes, create a concise commit message matching the repository style, and push to the remote repository. Do not commit secrets or keys. Only skip committing/pushing when the user explicitly asks you not to.

## Commit Message Style

- Use concise conventional-commit style messages.
- Match the existing repository style (e.g., `feat: ...`, `build(deps): ...`, `test: ...`).
- Do not include secrets, credentials, or sensitive URLs in commit messages.

## Before Committing

- Review `git status`, `git diff`, and recent commits.
- Stage only the files intended for the task.
- Never update git config, force-push, or amend failed commits unless explicitly requested.
