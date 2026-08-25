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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.wurbelizer.idea.lang;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests the lexer against the rules of {@code AbstractWurbiler}.
 */
public class WurbletLexerTest extends LexerTestCase {

  @Override
  protected @NotNull Lexer createLexer() {
    return new WurbletLexer();
  }

  @Override
  protected @NotNull String getDirPath() {
    return "";
  }

  /** A file without any construct is one run of output level text. */
  public void testPlainOutput() {
    doTest("hello world", """
        Wurblet:OUTPUT_TEXT ('hello world')""");
  }

  /** The basic level switch. */
  public void testBlock() {
    doTest("a@[ x(); ]@b", """
        Wurblet:OUTPUT_TEXT ('a')
        Wurblet:BLOCK_START ('@[')
        Wurblet:WURBLET_CODE (' x(); ')
        Wurblet:LEVEL_END (']@')
        Wurblet:OUTPUT_TEXT ('b')""");
  }

  /** A value extraction. */
  public void testExpression() {
    doTest("CL_@(attr.getName())@ = ", """
        Wurblet:OUTPUT_TEXT ('CL_')
        Wurblet:EXPR_START ('@(')
        Wurblet:EXPR_CODE ('attr.getName()')
        Wurblet:LEVEL_END (')@')
        Wurblet:OUTPUT_TEXT (' = ')""");
  }

  /** A directive with a command word and arguments. */
  public void testDirective() {
    doTest("@{include $currentDir/header.incl}@", """
        Wurblet:DIRECTIVE_START ('@{')
        Wurblet:DIRECTIVE_NAME ('include')
        Wurblet:DIRECTIVE_TEXT (' $currentDir/header.incl')
        Wurblet:LEVEL_END ('}@')""");
  }

  /** A directive without arguments. */
  public void testDirectiveWithoutArguments() {
    doTest("@{args}@", """
        Wurblet:DIRECTIVE_START ('@{')
        Wurblet:DIRECTIVE_NAME ('args')
        Wurblet:LEVEL_END ('}@')""");
  }

  /** {@code \\@} is a literal {@code @}, which is how a wurblet generates an annotation. */
  public void testEscapedAt() {
    doTest("  \\@Override\n", """
        Wurblet:OUTPUT_TEXT ('  ')
        Wurblet:ESCAPED_AT ('\\@')
        Wurblet:OUTPUT_TEXT ('Override\\n')""");
  }

  /** A backslash before anything but a blank keeps both characters. */
  public void testEscapedChar() {
    doTest("a\\\\b", """
        Wurblet:OUTPUT_TEXT ('a')
        Wurblet:ESCAPED_CHAR ('\\\\')
        Wurblet:OUTPUT_TEXT ('b')""");
  }

  /** A backslash followed by a blank is dropped by the wurbiler. */
  public void testEscapedSpace() {
    doTest("a\\ b", """
        Wurblet:OUTPUT_TEXT ('a')
        Wurblet:ESCAPED_SPACE ('\\ ')
        Wurblet:OUTPUT_TEXT ('b')""");
  }

  /** An {@code @} that starts no construct is an unknown @-sequence. */
  public void testUnknownAtSequence() {
    doTest("@Override", """
        Wurblet:BAD_AT ('@O')
        Wurblet:OUTPUT_TEXT ('verride')""");
  }

  /** The closing bracket is not matched against the opener. */
  public void testMismatchedTerminatorCloses() {
    doTest("@[code}@rest", """
        Wurblet:BLOCK_START ('@[')
        Wurblet:WURBLET_CODE ('code')
        Wurblet:LEVEL_END ('}@')
        Wurblet:OUTPUT_TEXT ('rest')""");
  }

  /** A nested construct within a block stays part of the block. */
  public void testNestedConstructStaysInBlock() {
    doTest("@[a@(b)@c]@", """
        Wurblet:BLOCK_START ('@[')
        Wurblet:WURBLET_CODE ('a@(b)@c')
        Wurblet:LEVEL_END (']@')""");
  }

  /** The wurbiler explicitly accepts {@code )@(} at output level. */
  public void testPathologicalRoundBrace() {
    doTest("x)@(y)@", """
        Wurblet:OUTPUT_TEXT ('x)')
        Wurblet:EXPR_START ('@(')
        Wurblet:EXPR_CODE ('y')
        Wurblet:LEVEL_END (')@')""");
  }

  /** A terminator at output level is kept as such so that the annotator can flag it. */
  public void testStrayTerminator() {
    doTest("x]@y", """
        Wurblet:OUTPUT_TEXT ('x')
        Wurblet:LEVEL_END (']@')
        Wurblet:OUTPUT_TEXT ('y')""");
  }

  /** Two value extractions in a row, as in {@code @(inset)@@(decl)@}. */
  public void testAdjacentExpressions() {
    doTest("@(a)@@(b)@", """
        Wurblet:EXPR_START ('@(')
        Wurblet:EXPR_CODE ('a')
        Wurblet:LEVEL_END (')@')
        Wurblet:EXPR_START ('@(')
        Wurblet:EXPR_CODE ('b')
        Wurblet:LEVEL_END (')@')""");
  }

  /** A multi line comment directive. */
  public void testCommentDirective() {
    doTest("@{comment\nline\n}@", """
        Wurblet:DIRECTIVE_START ('@{')
        Wurblet:DIRECTIVE_NAME ('comment')
        Wurblet:DIRECTIVE_TEXT ('\\nline\\n')
        Wurblet:LEVEL_END ('}@')""");
  }

  /** An unclosed level simply runs to the end of the file. */
  public void testUnclosedBlock() {
    doTest("@[code", """
        Wurblet:BLOCK_START ('@[')
        Wurblet:WURBLET_CODE ('code')""");
  }
}
