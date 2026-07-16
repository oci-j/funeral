# Funeral CLI Usage Reference

[English](cli-usage.md) | [简体中文](cli-usage.zh-CN.md)

The Funeral CLI shares a single binary with the registry server (JVM uber-jar or GraalVM
native executable); the top-level command name is `funeral`. Every subcommand supports
`--help`:

```shell
funeral --help
funeral <subcommand> --help        # e.g. funeral mirror helm --help
```

This document mirrors the picocli annotations in the code; if anything diverges, the
`--help` output wins.

## General Concepts

### Registry argument resolution

Most subcommands take an optional `[host:port]` positional argument, resolved in this order:

1. An explicitly given `host:port` on the command line (or a `--server` URL)
2. An alias in `aliases` of `~/.funeral/config.json` (mapped to `serverUrl`, optionally with
   its own `authDomain`)
3. `defaultRegistry` (written automatically on the first successful `login`)

If none is configured, the command fails with
`Registry not specified and no default registry configured`.

### Config file and credentials

- Config file: `~/.funeral/config.json`; override the directory with the `FUNERAL_CONFIG_DIR`
  environment variable (or the JVM property `funeral.config.dir`)
- `config.json` structure: `defaultRegistry`, `aliases`
  (`serverUrl` / `authDomain` / `protocol`), `auths` (`username` / `password` / `keyring` flag)
- After a successful `login`, credentials are stored in the system keyring (java-keyring),
  falling back to plaintext `auths` when the keyring is unavailable

### HTTP port binding caveat

**Every** CLI subcommand (including `login`, `repo list`) boots a Quarkus instance and binds
the HTTP port (8911 by default). If it conflicts with a locally running server or another
parallel CLI invocation, give the CLI a random port:

```shell
java -Dquarkus.http.port=0 -jar funeral.jar repo list
# same for the native binary:
./funeral -Dquarkus.http.port=0 repo list
```

### Exit codes

- `0` success
- `1` runtime failure (network errors, server errors, partial export failures, etc.)
- `2` command line usage error (picocli parse failure)

### Logs and stdout

The Quarkus banner and logs go to stdout by default. When piping command output into
files or other commands (e.g. `generate-completion` or `__complete`), keep stdout clean
with these environment variables:

```shell
QUARKUS_BANNER_ENABLED=false QUARKUS_LOG_CONSOLE_ENABLE=false funeral repo list
```

## serve — Start the registry server

```shell
funeral serve
```

Blocks and listens on port 8911 (change with `quarkus.http.port`). Running `funeral` with no
subcommand prints the usage text; use `serve` to start the server. Common environment
variables:

| Variable | Default | Description |
|---|---|---|
| `NO_MONGO` | - | Non-empty: skip MongoDB (metadata goes to local file storage) |
| `NO_MINIO` | - | Non-empty: skip MinIO/S3 (blobs go to local file storage) |
| `LOCAL_STORAGE_PATH` | `/tmp/funeral-storage` | Local file storage path |
| `MONGO_URL` | `mongodb://192.168.8.9:27017` | MongoDB connection string |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | see application.yml | MinIO/S3 settings |
| `AUTH_ENABLED` | `true` | When false, everything is anonymous |
| `AUTH_ALLOW_ANONYMOUS_PULL` | `true` | Allow anonymous pull |
| `AUTH_REALM` | `http://192.168.8.9:8911/v2/token` | Token endpoint (external address, used by docker login) |
| `AUTH_AUTO_CREATE_*` | admin/password | Admin user auto-created on first start |

## login / logout — Session management

```shell
funeral login [host:port] [-u|--username <name>] [-p|--password <pass>]
funeral logout [host:port]
```

- Without `-u/-p`, credentials are prompted interactively (`-u`/`-p` may also be given
  without a value to trigger the prompt)
- On success: credentials are stored; if `defaultRegistry` is empty it is set to this registry
- `logout` deletes the stored credentials for the registry

Examples:

```shell
funeral login 192.168.8.9:8911 -u admin -p password
funeral logout 192.168.8.9:8911
```

## repo — Repository management

```shell
funeral repo list [host:port]     # list repositories (name / tag count / updated)
funeral repo rm <repo> [host:port]
```

## tag — Tag management

```shell
funeral tag list <repo> [host:port]              # list all tags of a repository
funeral tag rm <repo> <tag|digest> [host:port]   # delete the given manifest
```

## mirror — Mirror images / Helm charts

Pull images or Helm charts from an external registry and store them into the Funeral
registry (performed server-side).

```shell
funeral mirror image <source-image-ref> [--to <target-repo>] [--target-tag <tag>] \
    [--username <u>] [--password <p>] [host:port]

funeral mirror helm <source-repo-url-or-name> <chart-name> [--version <version>] \
    [--to <target-repo>] [--target-version <version>] [--username <u>] [--password <p>] \
    [--format oci|chartmuseum] [host:port]
```

- `--format` defaults to `oci`
- `--password` prompts interactively when the value is omitted
- Source names are kept when `--to` / `--target-tag` / `--target-version` are not given

## import — Import from a remote registry to local

```shell
funeral import <image-ref> [-t|--to local|docker|oci] \
    [--oci-dir <dir>] [--storage <path>] [--server <url>] [--platform <os/arch>]
```

- `-t local` (default): write into local file storage (`--storage`, default
  `/tmp/funeral-storage`)
- `-t docker`: package and `docker load` into the local Docker daemon
- `-t oci`: write an OCI image layout directory (requires `--oci-dir`)
- `--server`: override the registry address from the image reference (e.g. direct IP)
- `--platform`: pick a platform for multi-arch images, e.g. `linux/amd64`

## export — Export from local to multiple registries

```shell
funeral export <source-image-ref> --to <target-ref> [--to <target-ref>...] \
    [--from local|docker|oci] [--oci-dir <dir>] [--storage <path>] \
    [--server <url>] [--platform <os/arch>] [--use-docker] [--continue-on-error]
```

- `--to` is **required and repeatable** — export to several targets in one run
- `--from` (default `local`): source is local file storage / the local Docker daemon /
  an OCI layout directory
- When the target is a Funeral registry with a configured alias (or `--server` is given),
  the upload goes through the server's direct-upload endpoint; otherwise (or with
  `--use-docker`) it goes through `docker push`
- `--continue-on-error`: keep exporting to the remaining targets after a failure; the
  final exit code is 1 if any target failed

## admin — User and permission management

```shell
funeral admin user list [host:port]
funeral admin user create <username> [--password <p>] [--email <e>] \
    [--role <role>...] [--repo <allowed-repo>...] [--enabled] [host:port]
funeral admin user update <username> [--password <p>] [--email <e>] \
    [--role <role>...] [--repo <allowed-repo>...] [--enabled|--no-enabled] [host:port]
funeral admin user delete <username> [host:port]

funeral admin permission list [username] [host:port]
funeral admin permission set <username> <repo> [--pull] [--push] [host:port]
funeral admin permission delete <username> <repo> [host:port]
```

- `create`'s `--password` prompts interactively when the value is omitted; `--enabled`
  defaults to true
- `update`'s `--enabled` is negatable (`--no-enabled` disables the user)
- `permission set` without `--pull/--push` grants neither

## health / version — Miscellaneous

```shell
funeral health [host:port]    # call /funeral_addition/health, prints OK/UNHEALTHY
funeral version               # print version (same as funeral --version)
```

## generate-completion — Generate a static completion script

```shell
QUARKUS_BANNER_ENABLED=false QUARKUS_LOG_CONSOLE_ENABLE=false \
  funeral generate-completion > ~/.local/share/bash-completion/completions/funeral
```

Purely static bash completion generated by picocli (subcommands/options/enum values).
The repository ships a dynamic completion script (with live repo/tag/host candidates)
which is recommended instead; installation is described in the *Shell Completion* section
of the [README](../README.md).

`__complete` is a hidden command used internally by that dynamic completion script and is
not meant for direct use.

## Related documents

- [README](../README.md): startup, shell completion installation
- [cli-tab-completion-plan.md](cli-tab-completion-plan.md): completion design (Chinese)
- [cli-import-export-plan.md](cli-import-export-plan.md): import/export design (Chinese)
- [DOCKER_DEPLOYMENT.md](../DOCKER_DEPLOYMENT.md): Docker deployment
