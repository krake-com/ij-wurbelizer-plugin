/*
 * Wurbelizer - https://wurbelizer.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.wurbelizer.idea.inspection;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.wurbelizer.idea.lang.WurbletFileType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests the diagnostics, which mirror those of the wurbiler.
 */
public class WurbletAnnotatorTest extends LightJavaCodeInsightFixtureTestCase {

  /** An unknown directive makes the wurbiler abort, so it is an error. */
  public void testUnknownDirective() {
    assertMessage("@{extendz Foo}@\n", HighlightSeverity.ERROR, "Unknown wurblet directive 'extendz'");
  }

  /** All twelve known directives are accepted. */
  public void testKnownDirectivesAreAccepted() {
    assertNoError("""
        @{package a.b}@
        @{import java.util.*}@
        @{extends AbstractWurblet}@
        @{implements Runnable}@
        @{args}@
        @{to}@
        @{phase 2}@
        @{indent auto}@
        @{comment text}@
        @{code x}@
        @{config y}@
        """);
  }

  /** {@code @{phase}@} takes a number. */
  public void testPhaseNeedsANumber() {
    assertMessage("@{phase two}@\n", HighlightSeverity.ERROR, "Expected a number");
  }

  /** {@code @{indent}@} takes a number or 'auto'. */
  public void testIndentAcceptsAuto() {
    assertNoError("@{indent auto}@\n@{indent 4}@\n");
  }

  /** A level that is never closed. */
  public void testUnclosedLevel() {
    assertMessage("@[ code\n", HighlightSeverity.ERROR, "Unclosed wurblet level at end of file");
  }

  /** A terminator without an open level. */
  public void testStrayTerminator() {
    assertMessage("text ]@ more\n", HighlightSeverity.ERROR, "No wurblet level is open here");
  }

  /** Two {@code @{extends}@} directives with different values. */
  public void testConflictingExtends() {
    assertMessage("@{extends A}@\n@{extends B}@\n", HighlightSeverity.ERROR, "More than one 'extends' directive");
  }

  /** The same interface twice. */
  public void testDuplicateInterface() {
    assertMessage("@{implements A}@\n@{implements A}@\n", HighlightSeverity.ERROR, "Interface 'A' already added");
  }

  /** An {@code @} that starts no construct only warns, exactly as the wurbiler does. */
  public void testUnknownAtSequenceWarns() {
    assertMessage("  @Override\n", HighlightSeverity.WARNING, "Unknown @-sequence");
  }

  /** An escaped {@code @} is what a wurblet writes to generate an annotation. */
  public void testEscapedAtIsClean() {
    assertNoError("  \\@Override\n");
  }

  /** {@code @{args}@} keeps only its first argument. */
  public void testMultipleArgsWarn() {
    assertMessage("@{args a b}@\n", HighlightSeverity.WEAK_WARNING, "Only the first argument is used");
  }

  /** An include that does not exist. */
  public void testMissingInclude() {
    assertMessage("@{include $currentDir/nowhere.incl}@\n", HighlightSeverity.ERROR, "Cannot resolve include");
  }

  /** A wurblet whose code defines no wurbel() method. */
  public void testMissingWurbelMethod() {
    assertMessage("""
        @[
          private void other() {
          }
        ]@
        """, HighlightSeverity.WEAK_WARNING, "No wurbel() method");
  }

  /** A wurblet that defines wurbel() is clean. */
  public void testWurbelMethodPresent() {
    assertNoError("""
        @[
          private void wurbel() {
          }
        ]@
        """);
  }

  private void assertMessage(String text, HighlightSeverity severity, String fragment) {
    List<HighlightInfo> infos = highlight(text);
    boolean found = infos.stream()
        .anyMatch(info -> info.getSeverity().equals(severity)
                          && info.getDescription() != null && info.getDescription().contains(fragment));
    assertTrue("expected " + severity + " '" + fragment + "', got: "
               + infos.stream().map(i -> i.getSeverity() + " " + i.getDescription()).collect(Collectors.joining("; ")),
               found);
  }

  private void assertNoError(String text) {
    List<HighlightInfo> infos = highlight(text);
    List<String> errors = infos.stream()
        .filter(info -> info.getSeverity().equals(HighlightSeverity.ERROR))
        .map(HighlightInfo::getDescription)
        .toList();
    assertTrue("unexpected errors: " + errors, errors.isEmpty());
  }

  private List<HighlightInfo> highlight(String text) {
    myFixture.configureByText(WurbletFileType.INSTANCE, text);
    return myFixture.doHighlighting();
  }
}
