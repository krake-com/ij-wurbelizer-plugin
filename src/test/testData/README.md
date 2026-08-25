# Test data

The files under `wurblets/` are real wurblet sources taken from the
[Tentackle](https://tentackle.org) framework, paired with the Java that the Tentackle
build actually generated from them (`*.java.expected`).

They are used by `WurbletGoldenTest`, which extracts the region between the
`// ----------------- begin wurblet code -----------------` and
`end wurblet code` markers and compares it against the document the plugin injects.
Testing against real generator output rather than hand-written fixtures is what makes
the golden test meaningful.

Tentackle (`krake-oss/tentackle`) and this plugin (`krake-oss/ij-wurbelizer-plugin`)
come from the same project family and are both published under the LGPL 2.1. These
fixtures are test-only and are not part of the distributed plugin.
