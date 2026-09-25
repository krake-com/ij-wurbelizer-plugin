# Wurbelizer plugin for IntelliJ IDEA

Editor support for [Wurbelizer](https://wurbelizer.org) wurblet sources (`*.wrbl`), with
**code completion on both code levels**.

A wurblet mixes two code levels in one file:

| Construct | Level | Meaning |
|---|---|---|
| `@[ … ]@` | wurblet level | the generator's own Java, copied verbatim into the wurblet class |
| `@( expr )@` | wurblet level | a value spliced into the output, compiled to `out.print(expr);` |
| `@{ … }@` | — | a wurbiler directive |
| everything else | output level | the code being generated |

## Installing

*Settings | Plugins | Marketplace*, search for **Wurbelizer**.

Alternatively, download the plugin zip and use *Settings | Plugins | ⚙ | Install Plugin
from Disk*.

Requires a Java-capable IDE (IntelliJ IDEA Community or Ultimate, Android Studio) —
the plugin depends on the bundled Java plugin. IDEA 2025.1 or newer.

## How it works

The wurbiler's translation is a pure, order-preserving concatenation, so the whole `.wrbl`
file can be reconstructed as Java and handed to IntelliJ's Java support through language
injection. Two documents are built per file.

**The wurblet level document** is what `org.wurbelizer.wurbile.SourceWurbiler` writes: the
class header from the directives, then the `@[ … ]@` blocks verbatim and every `@( expr )@` as
`out.print(expr);`.

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

Because this is the same concatenation the wurbiler produces, a loop opened in one block and
closed in another forms one continuous Java fragment: locals are visible across blocks, the
members of the `@{extends}@` parent class resolve, and errors are the errors javac would
report. `@{include}@` is followed recursively, so the `package`, `import`s and `extends` of a
shared `header.incl` apply.

**The output level document** holds the generated code, with a synthetic identifier standing
in for each `@( )@` hole — without one, `int CL_@(name)@ = @(size)@;` would collapse into
`int CL_ = ;` and not even tokenize. It is injected when the wurblet's parent class is
`AbstractJavaWurblet` or a subclass, i.e. exactly when the wurblet generates Java.

The internals — lexer, PSI, the two injected documents, the colors — are documented in
[`src/docs/how-it-works.md`](src/docs/how-it-works.md).

## Features

- Syntax highlighting and brace matching for the level markers, directives and escapes
- A background tint per code level, so the generator's own code and the code being generated
  read as two bands; both colors are under *Settings | Editor | Color Scheme | Wurblet*
- Code completion, resolution and parameter info on both levels
- Completion of the twelve wurbiler directives and their arguments — classes for `@{extends}@`,
  `@{implements}@` and `@{import}@`, files for `@{include}@`, streams for `@{to}@`
- Validation mirroring the wurbiler's own diagnostics: unknown directive, unclosed level,
  stray terminator, conflicting `@{package}@`/`@{extends}@`, duplicate `@{implements}@`,
  non-numeric `@{phase}@`/`@{indent}@`, unresolvable `@{include}@`, unknown `@`-sequence
  (with a quick fix that escapes it as `\@`)
- Navigation to the included file, the parent class and the imports

## Building and running

Requires a JDK 25 toolchain. The IntelliJ Platform to build against is downloaded
automatically (`platformType`/`platformVersion` in `gradle.properties`, currently IDEA
Community 2025.1 — the oldest supported IDE).

```bash
./gradlew test          # unit tests, including the golden tests against Tentackle
./gradlew buildPlugin   # build/distributions/ij-wurbelizer-plugin-*.zip
./gradlew runIde        # a sandbox IDE with the plugin installed
./gradlew verifyPlugin  # binary compatibility across the supported IDE range
```

To compile against a locally installed IDE instead and skip the download, set
`platformLocalPath` in `~/.gradle/gradle.properties` or `IDEA_HOME` in the environment.
This is a development convenience only — pass `-PplatformLocalPath=` to override it, and
never cut a release build that way, since the artifact would be tied to whichever IDE
happens to be installed.

The tests use the wurblet sources of the [Tentackle](https://tentackle.org) framework as
fixtures, together with the `.java` files its build generated from them, so the
reconstruction is pinned to the actual behavior of the wurbiler rather than to an expectation
of ours.

## Settings

*Settings | Languages & Frameworks | Wurblet* (per project):

- whether Java is injected into the output level at all
- how the output level is wrapped: automatically (parse both ways, take the one with fewer
  errors), always as a class body, or always as a list of statements
- additional `$`-variables for `@{include}@` paths; `$currentDir` and `$currentFile` are
  always available

## Known limitations

- **Content-bearing includes are not inlined.** `@{include}@` of a fragment that contains
  wurblet or output level code (`genwhere.incl`, `gensetpar.incl`) contributes only its
  directives, so symbols it defines appear unresolved in the including wurblet. Cross-file
  inclusion cannot be expressed with `MultiHostInjector`, which works on ranges of a single
  host file.
- **Class names are inserted fully qualified.** An injected document's import section belongs
  to a synthetic prefix, so an import statement added by a completion would be written to text
  that does not exist in the file. Completing a class that no `@{import}@` directive covers
  therefore inserts its qualified name, which is valid at both levels. Add the `@{import}@`
  directive and the simple name is used again.
- **The output level is a best effort parse.** A value extraction may stand for a modifier, a
  type, a name or a whole statement, so the reconstructed Java is not always well formed. Its
  errors are suppressed for that reason; the errors of the wurblet level are kept, because
  those are real.
- `@{code}@` and `@{comment}@` bodies are not injected as Java and javadoc.

## License

LGPL 2.1 or later — see [LICENSE](LICENSE).
