# CLAUDE.md

This file captures only what cannot be inferred from the codebase itself.

## Rules for editing this file

Both developers and AI agents are expected to add entries as they encounter surprises.

- **Add an entry** when you encounter something unexpected: a build quirk, a non-obvious constraint, a dependency gotcha, or any behavior that would surprise the next agent or developer.
- **Add an entry** when a developer flags an anti-pattern produced by AI — describe the anti-pattern and the preferred alternative.
- **Do not** add codebase overviews, directory listings, or anything discoverable by reading the source.
- Keep entries concise: one line per lesson, grouped under a heading if a theme emerges.

## Known gotchas

- Copyright year range (e.g. 2025-2026) is applied on autosave — new files should use only the current year (e.g. 2026).
- Kotlin context-sensitive resolution (`-Xcontext-sensitive-resolution`, preview in 2.2 / refined in 2.3) is enabled in the convention plugin. Inside a `when` whose subject has a known sealed type (or for `is`/`as` against that type), drop the type prefix on subclass references — write `is Heading` / `Paragraph`, not `is BlockMode.Heading` / `BlockMode.Paragraph`. CSR also applies to explicit return types, declared variable types, and parameter types when an outer expected type drives resolution. It does NOT apply to functions, properties with parameters, extension properties with receivers, type-annotation positions for variables, supertype lists, or generic constraints — keep the prefix in those positions.
- In Claude Code "auto mode", never commit on your own — leave changes in the working tree so the user can review the diff first. Only commit when the user explicitly asks for it.
- When generating backtick-quoted Kotlin identifiers (e.g. test names) from arbitrary input, strip CR, LF, and ``` ` \ < > [ ] / . : ; * ? " | ``` before wrapping in backticks.

## Test conventions

- Tests must retain `// given`, `// when`, `// then` comment structure — AI agents tend to omit these.
- For semantic event flow testing, use the overloaded `sameAs` infix on `Flow<SemanticEvent>` (defined in `markanywhere-test`) against a `semanticEvents { ... }` builder — this is event-stream comparison, unrelated to HTML.
- For asserting rendered HTML output (e.g. in `markanywhere-render` tests), use `sameAsHtml` (not the generic string `sameAs`) — provides syntax highlighting in the IDE.

## Anti-patterns to avoid

- Do not add content to this file that is already discoverable by reading the source or build scripts — that inflates context without adding signal, reducing AI agent task success rates (see [arxiv 2602.11988](https://arxiv.org/abs/2602.11988)).