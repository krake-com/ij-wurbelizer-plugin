# Changelog

All notable changes to the Wurbelizer plugin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.1]

### Fixed

- A backslash followed by a blank (`\ `) now stands for the blank alone, matching the
  fixed wurbiler. The plugin used to drop both characters, as older wurbilers did, which
  could glue two tokens of the injected Java together.

## [1.0.0]

First public release.

### Added

- Syntax highlighting, per-level background tint and brace matching for the level
  markers `@[ ]@`, `@( )@` and `@{ }@`, their escapes and the wurbiler directives.
- Java code insight on both code levels. The wurblet level and the output level are
  each reconstructed as a Java document and injected, so completion, resolution,
  parameter info and navigation work on the generator code *and* on the code being
  generated.
- Completion and validation of the wurbiler directives.
- Navigation to `@{include}@` files, the `@{extends}@` parent class and `@{import}@`
  targets.
- Quick fix for unescaped `@` sequences.
- Per-project settings under *Settings | Languages & Frameworks | Wurblet*.
- Colour scheme contributions for the Default and Darcula schemes.

[Unreleased]: https://bitbucket.org/krake-oss/ij-wurbelizer-plugin/branches/compare/HEAD..v1.0.1
[1.0.1]: https://bitbucket.org/krake-oss/ij-wurbelizer-plugin/branches/compare/v1.0.1..v1.0.0
[1.0.0]: https://bitbucket.org/krake-oss/ij-wurbelizer-plugin/src/v1.0.0/
