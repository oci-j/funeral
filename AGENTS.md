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

## Documentation

When a code change alters behavior, commands, options, configuration, or CI workflows that are described in the documentation, update the corresponding docs in the same commit (or explicitly state why no doc update is needed):

- `README.md` for user-facing quick reference and usage.
- `docs/cli-usage.md` for CLI subcommand/option/config changes.
- Other `docs/*.md` for their respective topics.
- For `docs/*-plan.md` historical plan documents, do not silently rewrite them; append a dated execution/update note instead.
