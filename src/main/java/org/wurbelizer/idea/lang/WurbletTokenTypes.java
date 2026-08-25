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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Token- and element types of the wurblet language.
 * <p>
 * The token set mirrors the character level state machine of
 * {@code org.wurbelizer.wurbile.AbstractWurbiler#compile()}.
 */
public interface WurbletTokenTypes {

  /** The file element. */
  IFileElementType FILE = new IFileElementType(WurbletLanguage.INSTANCE);

  // --- tokens ---

  /** Plain text at output level. */
  IElementType OUTPUT_TEXT = new WurbletElementType("OUTPUT_TEXT");

  /** Java code at wurblet level, i.e. within {@code @[ ]@}. */
  IElementType WURBLET_CODE = new WurbletElementType("WURBLET_CODE");

  /** A Java expression within {@code @( )@}. */
  IElementType EXPR_CODE = new WurbletElementType("EXPR_CODE");

  /** The command word of a directive. */
  IElementType DIRECTIVE_NAME = new WurbletElementType("DIRECTIVE_NAME");

  /** The arguments of a directive. */
  IElementType DIRECTIVE_TEXT = new WurbletElementType("DIRECTIVE_TEXT");

  /** The opening {@code @[}. */
  IElementType BLOCK_START = new WurbletElementType("BLOCK_START");

  /** The opening {@code @(}. */
  IElementType EXPR_START = new WurbletElementType("EXPR_START");

  /** The opening <code>@{</code>. */
  IElementType DIRECTIVE_START = new WurbletElementType("DIRECTIVE_START");

  /**
   * A closing {@code ]@}, {@code )@} or <code>}@</code>.
   * The wurbiler does not match the bracket type against the opener, so neither do we.
   */
  IElementType LEVEL_END = new WurbletElementType("LEVEL_END");

  /** The escape {@code \@}, standing for a literal {@code @}. */
  IElementType ESCAPED_AT = new WurbletElementType("ESCAPED_AT");

  /** A backslash followed by a blank. The wurbiler drops both characters. */
  IElementType ESCAPED_SPACE = new WurbletElementType("ESCAPED_SPACE");

  /** A backslash followed by anything else. The wurbiler keeps both characters. */
  IElementType ESCAPED_CHAR = new WurbletElementType("ESCAPED_CHAR");

  /** An {@code @} not followed by <code>{</code>, {@code (} or {@code [}. */
  IElementType BAD_AT = new WurbletElementType("BAD_AT");

  // --- composite elements ---

  /**
   * The whole content of a wurblet source.
   * <p>
   * Everything lives below this single element so that the language injection has a context
   * element that is not the file itself: the platform stops probing for injections one level
   * below the containing file.
   */
  IElementType WURBLET_CONTENT = new WurbletElementType("WURBLET_CONTENT");

  /** A run of output level text. */
  IElementType OUTPUT_SEGMENT = new WurbletElementType("OUTPUT_SEGMENT");

  /** A {@code @[ ]@} block. */
  IElementType WURBLET_BLOCK = new WurbletElementType("WURBLET_BLOCK");

  /** A {@code @( )@} value extraction. */
  IElementType WURBLET_EXPR = new WurbletElementType("WURBLET_EXPR");

  /** A <code>@{ }@</code> directive. */
  IElementType WURBLET_DIRECTIVE = new WurbletElementType("WURBLET_DIRECTIVE");

  // --- token sets ---

  /** All escape sequences. */
  TokenSet ESCAPES = TokenSet.create(ESCAPED_AT, ESCAPED_SPACE, ESCAPED_CHAR);

  /** Everything that may appear within an output segment. */
  TokenSet OUTPUT_CONTENT = TokenSet.create(OUTPUT_TEXT, ESCAPED_AT, ESCAPED_SPACE, ESCAPED_CHAR, BAD_AT);

  /** All level switching markers. */
  TokenSet MARKERS = TokenSet.create(BLOCK_START, EXPR_START, DIRECTIVE_START, LEVEL_END);
}
