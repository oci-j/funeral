# bash completion for the funeral OCI registry CLI.
#
# Install (bash):
#   sudo cp funeral-completion.bash /etc/bash_completion.d/funeral
#   # or
#   mkdir -p ~/.local/share/bash-completion/completions
#   cp funeral-completion.bash ~/.local/share/bash-completion/completions/funeral
#
# zsh: run `autoload -U +X bashcompinit && bashcompinit` first, then source this file.
#
# All completion logic lives in the CLI itself (`funeral __complete`);
# this script only forwards the current command line words.

_funeral_complete() {
    local line cur
    line="${COMP_LINE:0:COMP_POINT}"
    cur="${line##*[[:space:]]}"
    COMPREPLY=()

    # completing the command name itself: nothing to do
    if [[ "$line" =~ ^[[:space:]]*[^[:space:]]+$ ]]; then
        return 0
    fi

    # rebuild words from the raw line so that ':' in host:port / repo:tag
    # (part of COMP_WORDBREAKS) does not corrupt word parsing
    local -a words
    read -ra words <<< "$line"
    # a trailing space means a fresh empty word is being completed
    if [[ "$line" =~ [[:space:]]$ ]]; then
        words+=("")
    fi

    local out
    # the '--' delimiter makes picocli treat all words as positional,
    # so option-like words (--format, -t, ...) are passed through as-is;
    # the env vars keep quarkus banner/logs out of stdout (candidates only)
    out="$(QUARKUS_BANNER_ENABLED=false QUARKUS_LOG_CONSOLE_ENABLE=false \
        "${words[0]}" __complete -- "${words[@]:1}" 2>/dev/null)" || return 0

    if [[ "$cur" == *:* ]]; then
        # bash replaces only the part after the last ':'; strip the common
        # prefix up to it from the candidates so insertion stays correct
        local base="${cur%"${cur##*:}"}"
        COMPREPLY=($(compgen -W "$out" -- "$cur"))
        COMPREPLY=("${COMPREPLY[@]#"$base"}")
    else
        COMPREPLY=($(compgen -W "$out" -- "$cur"))
    fi
    return 0
}

# -o default: fall back to filename completion when the CLI returns nothing
# (e.g. path-valued options like --oci-dir / --storage)
complete -o default -F _funeral_complete funeral
