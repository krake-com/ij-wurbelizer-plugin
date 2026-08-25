# How the plugin works

Developer documentation of the internals. The [README](../../README.md) describes what the
plugin does from a user's point of view; this document describes how it does it and why it is
built the way it is.

- [The problem](#the-problem)
- [The pipeline](#the-pipeline)
- [Package map](#package-map)
- [Lexing](#lexing)
- [The PSI tree](#the-psi-tree)
- [The header model](#the-header-model)
- [The Java injection](#the-java-injection)
- [Highlighting and colors](#highlighting-and-colors)
- [Diagnostics](#diagnostics)
- [Completion and references](#completion-and-references)
- [Settings](#settings)
- [Tests](#tests)
- [Where it stops](#where-it-stops)

## The problem

A wurblet source (`*.wrbl`) is a code generator written in Java that emits code, and both live
in the same file:

| Construct | Level | Meaning |
|---|---|---|
| `@[ … ]@` | wurblet level | the generator's own Java, copied verbatim into the wurblet class |
| `@( expr )@` | wurblet level | a value spliced into the output, compiled to `out.print(expr);` |
| `@{ … }@` | — | a wurbiler directive |
| everything else | output level | the code being generated |

An editor that treats such a file as plain text is of no use for either level. Writing a Java
front end for both levels ourselves would be worse: it would be a second, inevitably diverging
implementation of Java.

The way out is an observation about the wurbiler. Its translation
(`org.wurbelizer.wurbile.AbstractWurbiler#compile()` driving
`org.wurbelizer.wurbile.SourceWurbiler`) is a **pure, order-preserving concatenation** — every
character of the source ends up at a predictable place in the generated Java, and nothing is
reordered. So the whole `.wrbl` file can be reconstructed as Java and handed to IntelliJ's own
Java support through language injection. The plugin never analyses Java. It reconstructs, and
IntelliJ does the rest.

Everything else in the plugin exists either to make that reconstruction possible (lexer, PSI,
header model) or to cover what the reconstruction cannot reach (directives, colors,
diagnostics).

## The pipeline

```
  ColumnLengths.wrbl
         │
         ▼
  WurbletLexer            character level state machine, a port of AbstractWurbiler#compile()
         │                tokens: OUTPUT_TEXT, WURBLET_CODE, EXPR_CODE, DIRECTIVE_*, markers, escapes
         ▼
  WurbletParser           deliberately flat tree, no Java understanding whatsoever
         │
         ▼
  WurbletFile
    └─ WurbletContent     one wrapper element below the file, see "The PSI tree"
         ├─ WurbletDirective      @{ … }@
         ├─ WurbletBlock          @[ … ]@      ─┐
         ├─ WurbletExpr           @( … )@      ─┤ injection hosts
         └─ WurbletOutputSegment  plain text   ─┘
         │
         ▼
  WurbletJavaInjector     builds TWO Java documents per file
         │
         ├──► wurblet level   class header + blocks verbatim + out.print(expr); per @( )@
         │                    → completion, resolution, parameter info, real compile errors
         │
         └──► output level    generated text with a synthetic identifier per @( )@ hole
                              → completion and resolution in the code being generated
```

Once the two documents exist, the platform's Java plugin supplies completion, resolution,
parameter info, navigation and error highlighting inside them, and the wurblet plugin only has
to keep out of the way — see [Highlighting and colors](#highlighting-and-colors) and
[Diagnostics](#diagnostics).

## Package map

All classes live under `org.wurbelizer.idea`.

| Package | Role |
|---|---|
| `lang` | language, file type, lexer, parser, parser definition, brace matcher, icons |
| `psi` | PSI elements, the injection hosts, escape encoding and the text escaper |
| `model` | directive types, the directive scanner, the header model, `$`-variable substitution |
| `inject` | the `MultiHostInjector`, the output level model, Java helpers, the import filter |
| `highlight` | syntax highlighter, level backgrounds, color settings page, the two info filters |
| `inspection` | the annotator mirroring the wurbiler's diagnostics and its quick fix |
| `completion` | completion of directives and their arguments, plain fallback for non-Java output |
| `reference` | references from `@{include}@`, `@{extends}@`, `@{implements}@`, `@{import}@` |
| `settings` | the project level settings and their configurable |

Every class is wired up in [`META-INF/plugin.xml`](../main/resources/META-INF/plugin.xml);
there is no other registration mechanism in play.

## Lexing

`lang/WurbletLexer` is a hand written port of the character level state machine in
`AbstractWurbiler#compile()`. It is a port, not an interpretation: where the wurbiler's
documentation and its implementation disagree, the implementation wins, because that is what
users' files were written against. The rules that are easy to get wrong:

- **The file starts at output level.** There is no preamble mode; a directive is just the first
  thing that happens to appear.
- **Closing brackets are not matched against the opener.** Each of `]@`, `)@` and `}@` closes
  whatever level is currently open. `@[ code )@` is legal and closes the block.
- **A further opener within a level only increments a nesting counter** and stays part of the
  enclosed text, so `@[ String s = "@(x)@"; ]@` does not end the block early.
- **The backslash is tested before anything else, on every level.** `\@` is a literal `@`,
  backslash-blank is dropped entirely, and a backslash before anything else keeps both
  characters — so `\\` stays `\\` and there is no line continuation in a `.wrbl` file.
- **An `@` not followed by `{`, `(` or `[` is an unknown @-sequence.** The wurbiler warns and
  keeps both characters, so the lexer emits `BAD_AT` and the annotator warns.
- **`)@(` at output level is a legal special case**, an explicit exception in the wurbiler: a
  closing round brace directly followed by a new value extraction.

Because escapes are recognised on every level, an `ESCAPED_CHAR` token says nothing about which
level it sits on — a `\n` inside a Java string literal at wurblet level and a `\@` in output
text are the same token type. That is the reason the level colors are not painted from the
syntax highlighter; see [Highlighting and colors](#highlighting-and-colors).

### Lexer state

The platform relexes incrementally and therefore needs the lexer state at every token boundary
as a single `int`. `WurbletLexer` encodes it as

```
bits 0-1   the code level   (LEVEL_OUTPUT, LEVEL_BLOCK, LEVEL_EXPR, LEVEL_DIRECTIVE)
bit  2     nameRead         the directive command word has already been lexed
bits 3-    the nesting counter
```

via `encodeState()` / `levelOf()`. `getState()` reports the state at the *start* of the current
token, which is what the platform expects.

## The PSI tree

`lang/WurbletParser` builds a deliberately flat tree: a file is a sequence of output segments,
blocks, value extractions and directives, all siblings. No nesting, no expression structure —
all Java understanding comes from the injection, and a richer tree would only be a second place
to keep it consistent.

```
WurbletFile
└── WurbletContent
    ├── WurbletDirective       @{include $currentDir/header.incl}@
    ├── WurbletOutputSegment   "\n"
    ├── WurbletBlock           @[ … ]@
    ├── WurbletOutputSegment   "\n  /** … for '"
    ├── WurbletExpr            @(attr)@
    └── …
```

Two structural decisions are worth knowing about:

- **`WurbletContent` exists for the injection.** The platform probes for injections by walking
  up from the element under the caret and stops one level *below* the containing file, so an
  injector that needs the whole file as its context needs an element that is not the file
  itself. `WurbletContent` is that element and has no other purpose.
- **A stray level terminator stays a top level token.** When `]@` appears with nothing open the
  parser does not wrap it, so the annotator can flag it and the injector can ignore it.

### Injection hosts

`psi/WurbletHostElement` is the base of `WurbletBlock`, `WurbletExpr` and
`WurbletOutputSegment` — the three element kinds Java is injected into. It implements
`PsiLanguageInjectionHost` and adds:

- `getRangeInsideHost()` — the element's text **without** its level markers. The host carries
  `@[ … ]@`, the injected text is what lies between.
- `createLiteralTextEscaper()` → `psi/WurbletTextEscaper`, which decodes the wurblet escapes
  into the injected document (`\@` → `@`, backslash-blank dropped) and keeps an offset table
  mapping every decoded character back to its offset in the host, so that an edit made in the
  injected document lands in the right place in the `.wrbl` file.
- `updateText()` → `psi/WurbletHostManipulator`, which re-encodes on the way back
  (`psi/WurbletEscaping#encode`): an `@` in injected text has to be written as `\@` to survive
  the wurbiler.
- `InjectionBackgroundSuppressor`, a marker interface — see
  [Highlighting and colors](#highlighting-and-colors).

## The header model

Several parts of the plugin need to know what class the wurbiler would generate: its package,
imports, parent class and interfaces. `model/WurbletHeader#of(WurbletFile)` answers that, cached
on `PsiModificationTracker.MODIFICATION_COUNT`.

Collecting it means following `@{include}@` recursively, exactly as the wurbiler does, because
in practice the directives that matter live in a shared `header.incl`:

```
@{include $currentDir/header.incl}@   →   @{package org.tentackle.wurblet}@
                                          @{import java.util.*}@   …
                                          @{extends ModelWurblet}@
```

Details that matter:

- **Included files have no wurblet PSI.** As far as the IDE is concerned a `.incl` is a plain
  text file, so `model/WurbletDirectiveScanner` runs `WurbletLexer` over its raw text and
  returns the directives. The same lexer, so the same rules.
- **The document beats the file on disk.** An unsaved edit of a shared `header.incl` takes
  effect immediately.
- **Includes are visited once.** A `visited` set of paths makes a circular include terminate;
  the missing or circular case is reported by the annotator, not here.
- **First wins for the singletons.** `@{package}@` and `@{extends}@` keep the first value, which
  is what the wurbiler does; the annotator flags the conflicting second one.
- **`$`-variables** are substituted by `model/WurbletVariables`, mirroring
  `org.wurbelizer.wurbel.WurbelHelper#translateVariables`: `$name` or `${name}`, an unknown
  variable is left alone, `\$` suppresses the substitution. `$currentDir` and `$currentFile` are
  always available, more can be added in the settings.

`model/WurbletDirectiveType` is the enum of the twelve directives with their parameter syntax
and description, used by the completion, the annotator and the references. The wurbiler
lower-cases the command word before dispatching, so directive names are case insensitive.

## The Java injection

`inject/WurbletJavaInjector` is a `MultiHostInjector` registered on `WurbletContent`. It builds
two disjoint Java documents per file.

### The wurblet level document

This is what `SourceWurbiler` writes: the class header derived from the directives, then the
`@[ … ]@` blocks verbatim and every `@( expr )@` as `stream.print(expr);`.

```
@[  private void wurbel() throws ModelException {          →  private void wurbel() throws ModelException {
      for (Attribute attr: ...) {                          →    for (Attribute attr: ...) {
]@                                                         →      out.print(source[0]);
  /** … for '@(attr)@'. */                                 →      out.print(attr);
  @(lead)@int CL_@(attr.getName()…)@ = @(attr.getSize())@; →      out.print(lead); … out.print(attr.getSize());
@[    }                                                    →    }
  }                                                        →  }
]@
```

Each `@[ ]@` block becomes one injected place with no prefix; each `@( )@` becomes a place with
the prefix `<stream>.print(` and the suffix `);`. The stream is `out` unless a `@{to}@`
directive changed it — the directives are walked in source order, so a `@{to}@` in the middle of
a file affects only the value extractions after it.

Because this is the same concatenation the wurbiler produces, the whole wurblet level is **one
continuous Java fragment**: a loop opened in one block and closed in another parses, locals are
visible across blocks, and the members of the `@{extends}@` parent class resolve. Its errors are
the errors javac would report, which is why they are kept while the output level's are dropped.

`buildClassHeader()` writes the prefix of the first place: the `package`, the `import`s, the
class declaration with `extends`/`implements`, and — when the code contains `wurbel(` — the
`run()` method the wurbiler generates, so that `wurbel()` is not reported as unused.

### The output level document

The output level is the code being generated, and it has holes. Without a placeholder the
concatenation would not even tokenize: `int CL_@(name)@ = @(size)@;` would collapse into
`int CL_ = ;`. `inject/WurbletOutputModel` therefore substitutes a synthetic identifier `_wvN_`
for every value extraction, with two gluing rules taken from what wurblets actually do:

| Case | Source | Fragment |
|---|---|---|
| name building | `CL_@(x)@` | `CL__wv1_` — glued left, one identifier |
| modifier | `@(lead)@int` | `_wv0_ int` — a blank before an identifier on the right |

The fragment is then wrapped, because a run of generated code is not a compilation unit:

| Wrapper | Prefix |
|---|---|
| `CLASS_BODY` | `class __WurbletOutput__ {` |
| `STATEMENTS` | `class __WurbletOutput__ { void __body__() {` |

In `AUTO` mode (the default) `inject/WurbletJavaSupport#wrapperOf` parses the fragment both ways
and takes the one with fewer `PsiErrorElement`s, class body winning ties because it is by far
the common case. The result is cached per file.

The output level is injected **only when the wurblet generates Java**, i.e. when its parent
class is `org.wurbelizer.wurblet.AbstractJavaWurblet` or a subclass of it
(`WurbletJavaSupport#isJavaOutput`). The parent class is resolved the way javac would resolve it
in the generated source — through the `@{import}@` directives, then the `@{package}@`, then
`org.wurbelizer.wurblet`, then `java.lang`. When it cannot be resolved at all, a setting
decides, defaulting to Java.

### Consequences of injecting into a reconstructed document

- **Class names are inserted fully qualified.** `inject/WurbletImportFilter` forces this. An
  injected document's import section belongs to a synthetic prefix, so an import statement added
  by a completion would be written to text that does not exist in the file and would be lost. A
  qualified name is valid at both levels. Once an `@{import}@` directive covers the class, Java
  uses the simple name again on its own.
- **The output level is a best effort parse.** A value extraction may stand for a modifier, a
  type, a name or a whole statement, so the reconstructed Java is not always well formed. Its
  diagnostics are therefore dropped — see below.

## Highlighting and colors

Two independent layers.

**Token colors** come from `highlight/WurbletSyntaxHighlighter`, which maps token types to
attribute keys: the level markers and directive braces, the directive command word and
arguments, the escapes and the unknown `@`-sequence.

**Level backgrounds** are what separates the two code levels visually, and they need a little
more machinery. Both levels are Java injected into the same host file, and the platform paints
**one** background over every injected fragment: `InjectedGeneralHighlightingPass` builds a
single key from the *host file's* language, `Wurblet:INJECTED_LANGUAGE_FRAGMENT`, falling back to
`INJECTED_LANGUAGE_FRAGMENT` — the familiar green. Uniform, so both levels come out identical.

The lever is `InjectedLanguageUtil#isHighlightInjectionBackground(host)`, which the pass
consults **per host element**. `WurbletHostElement` implements `InjectionBackgroundSuppressor`,
so the platform paints no background in a `.wrbl` file at all, and
`highlight/WurbletLevelBackgroundAnnotator` paints its own instead:

| Element | Key |
|---|---|
| `WurbletBlock`, `WurbletExpr`, `WurbletDirective` | `WURBLET_LEVEL_BACKGROUND` |
| `WurbletOutputSegment` | `OUTPUT_LEVEL_BACKGROUND` |

A directive counts as wurblet level: it configures the generator and is nothing the wurblet
emits.

The tint is painted over **whole elements**, markers included, by an annotator rather than per
token by the syntax highlighter, for the reason given under [Lexing](#lexing): the escapes of
every level share their token types, so a token keyed background would stripe the wurblet level
wherever a Java string literal contains a `\n`. The attributes carry a background only, so they
compose with the foregrounds of the injected Java instead of replacing them, and the keys
deliberately have no fallback key, so no foreground can bleed in from a platform default.

Defaults ship in [`colorSchemes/`](../main/resources/colorSchemes) for the light and the dark
schemes and are registered as `additionalTextAttributes`. Cool blue against warm sand: the pair
is separated on the blue–yellow axis and in luminance, the axis that survives a red–green color
vision deficiency. Both are editable under *Settings | Editor | Color Scheme | Wurblet*, which
`highlight/WurbletColorSettingsPage` provides. Its preview marks the two levels with `<wl>` and
`<ol>` tags, because the preview is built from the syntax highlighter alone and would otherwise
not show them — note that once that tag map is non-null the platform parses *every* `<tag>` in
the demo text.

### The two info filters

Diagnostics of the output level injection are dropped, those of the wurblet level are kept:

- `highlight/WurbletOutputHighlightFilter` drops every `HighlightInfo` of severity
  `WEAK_WARNING` or above whose file is the output level injection, identified by its injection
  host being a `WurbletOutputSegment`.
- `highlight/WurbletOutputErrorFilter` does the same for `PsiErrorElement`s.

The asymmetry is deliberate. The wurblet level is exactly what javac will compile, so its errors
are real. The output level is a fragment with holes and its errors say nothing about the
wurblet.

## Diagnostics

`inspection/WurbletAnnotator` reports what the wurbiler would report — deliberately including
its quirks, because the point is to predict the wurbiler's behavior, not to improve on it:

| Check | Severity | Note |
|---|---|---|
| unknown directive | error | the wurbiler aborts compilation |
| missing directive name, missing argument | error | |
| unclosed level at end of file | error | |
| stray `]@` with nothing open | error | |
| conflicting `@{package}@` / `@{extends}@` | error | the second one is ignored |
| duplicate `@{implements}@` | error | |
| non-numeric `@{phase}@` / `@{indent}@` | error | `@{indent}@` also accepts `auto` |
| unresolvable `@{include}@` | error | after `$`-variable substitution |
| unknown `@`-sequence | warning | quick fix escapes it as `\@` |
| more than one `@{args}@` argument | weak warning | the wurbiler's `handleArgs()` does not loop |
| no `wurbel()` method | weak warning | the generated `run()` calls it unconditionally |

The quick fix is `inspection/WurbletEscapeAtFix`.

## Completion and references

Inside the injected documents, completion is the Java plugin's. What the plugin adds is
everything the injection cannot reach.

`completion/WurbletCompletionContributor` completes:

- the twelve directive command words, with their parameter syntax and description;
- their arguments — classes for `@{extends}@` and `@{implements}@`, classes and packages for
  `@{import}@`, files for `@{include}@` (offering `$currentDir/`), streams for `@{to}@` (`out`
  plus every `PrintStream` declared in the file), `auto` for `@{indent}@`;
- a plain keyword and class fallback in the output level of wurblets that do *not* generate
  Java, where there is no injected document to do the job.

Two implementation notes. Class names are collected in two steps — all matching names first,
then the classes per name — because querying classes from within `processAllClassNames` would
nest two stub index operations, which the platform forbids. And the insert handler writes the
qualified name unless the wurblet's `@{import}@` directives already make the simple name
resolvable, for the reason given under [the injection](#consequences-of-injecting-into-a-reconstructed-document).

`reference/WurbletReferenceContributor` contributes references on the directive arguments:
`WurbletIncludeReference` for `@{include}@` (with `$`-variable substitution) and
`WurbletClassReference` for `@{extends}@`, `@{implements}@` and `@{import}@`, the latter also
resolving packages. A simple class name is resolved through the imports and the package of the
wurblet, exactly as javac would.

## Settings

`settings/WurbletSettings` is a project level `PersistentStateComponent` stored in
`wurblet.xml`, edited through `settings/WurbletConfigurable` under *Settings | Languages &
Frameworks | Wurblet*:

| Setting | Default | Effect |
|---|---|---|
| `injectOutputLevel` | on | whether Java is injected into the output level at all |
| `outputWrapper` | `AUTO` | class body, statements, or parse both ways and take fewer errors |
| `assumeJavaWhenParentUnresolved` | on | what to assume when the parent class does not resolve |
| `variables` | empty | additional `$`-variables for `@{include}@` paths |

## Tests

```bash
./gradlew test
```

| Test | Pins |
|---|---|
| `lang/WurbletLexerTest` | the state machine, including the quirks listed under [Lexing](#lexing) |
| `inject/WurbletGoldenTest` | the reconstruction against the Java the wurbiler really generated |
| `inject/WurbletInjectionTest`, `WurbletSmokeTest` | that both documents are built and resolve |
| `inspection/WurbletAnnotatorTest` | every diagnostic, including the quirks |
| `highlight/WurbletLevelColorTest` | the level backgrounds, the suppression, the shipped schemes |
| `completion/WurbletCompletionTest` | directive and argument completion |
| `reference/WurbletReferenceTest` | include, parent class and import references |

The golden test deserves a note. Its fixtures in [`testData/wurblets`](../test/testData/wurblets)
are wurblet sources of the [Tentackle](https://tentackle.org) framework **together with the
`.java` files its build generated from them**. The test extracts the wurblet code between the
generated `begin wurblet code` / `end wurblet code` comment markers and compares it against the
injected wurblet level document. The reconstruction is therefore pinned to the actual behavior
of `SourceWurbiler`, not to an expectation of ours — which is the only way to be sure the
premise this whole plugin rests on still holds.

## Where it stops

The limitations in the [README](../../README.md#known-limitations) are not oversights; each is a
consequence of the design above.

- **Content-bearing includes are not inlined.** `MultiHostInjector` works on ranges of a single
  host file, so an included fragment that contains wurblet or output level code contributes only
  its directives. Symbols it defines appear unresolved in the including wurblet.
- **Class names are inserted fully qualified** — see
  [above](#consequences-of-injecting-into-a-reconstructed-document).
- **The output level is a best effort parse** and its diagnostics are suppressed.
- **`@{code}@` and `@{comment}@` bodies are not injected** as Java and javadoc.
- **A backslash directly followed by a blank cannot be expressed** in generated output: the
  wurbiler drops both characters and there is no escape for it.

When changing the lexer or the injector, run the golden tests first. They are the check on
whether the reconstruction still matches the wurbiler; everything else in the plugin assumes it
does.
