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

package org.wurbelizer.idea.highlight;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.lang.WurbletLexer;
import org.wurbelizer.idea.lang.WurbletTokenTypes;

import java.util.HashMap;
import java.util.Map;

/** Syntax highlighter of the wurblet language. */
public class WurbletSyntaxHighlighter extends SyntaxHighlighterBase {

  /** The level switching markers and the directive braces. */
  public static final TextAttributesKey MARKER =
      TextAttributesKey.createTextAttributesKey("WURBLET_MARKER", DefaultLanguageHighlighterColors.KEYWORD);

  /** The command word of a directive. */
  public static final TextAttributesKey DIRECTIVE_NAME =
      TextAttributesKey.createTextAttributesKey("WURBLET_DIRECTIVE_NAME", DefaultLanguageHighlighterColors.METADATA);

  /** The arguments of a directive. */
  public static final TextAttributesKey DIRECTIVE_ARGUMENT =
      TextAttributesKey.createTextAttributesKey("WURBLET_DIRECTIVE_ARGUMENT", DefaultLanguageHighlighterColors.STRING);

  /** Code at wurblet level, i.e. the generator's own code. */
  public static final TextAttributesKey WURBLET_LEVEL =
      TextAttributesKey.createTextAttributesKey("WURBLET_LEVEL", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);

  /** Text at output level, i.e. the code being generated. */
  public static final TextAttributesKey OUTPUT_LEVEL =
      TextAttributesKey.createTextAttributesKey("WURBLET_OUTPUT_LEVEL", HighlighterColors.TEXT);

  /**
   * The background of the wurblet level, i.e. of everything that runs at generation time.
   * <p>
   * Deliberately without a fallback key: the level backgrounds are painted over whole regions
   * by {@link WurbletLevelBackgroundAnnotator}, so a foreground inherited from a platform
   * default would flatten the coloring of the injected Java.
   */
  public static final TextAttributesKey WURBLET_LEVEL_BACKGROUND =
      TextAttributesKey.createTextAttributesKey("WURBLET_LEVEL_BACKGROUND");

  /** The background of the output level, i.e. of the code being generated. */
  public static final TextAttributesKey OUTPUT_LEVEL_BACKGROUND =
      TextAttributesKey.createTextAttributesKey("WURBLET_OUTPUT_LEVEL_BACKGROUND");

  /** An escape sequence. */
  public static final TextAttributesKey ESCAPE =
      TextAttributesKey.createTextAttributesKey("WURBLET_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);

  /** An {@code @} that starts no construct. */
  public static final TextAttributesKey BAD_AT =
      TextAttributesKey.createTextAttributesKey("WURBLET_BAD_AT", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);

  private static final Map<IElementType, TextAttributesKey[]> KEYS = new HashMap<>();

  static {
    for (IElementType type : WurbletTokenTypes.MARKERS.getTypes()) {
      KEYS.put(type, pack(MARKER));
    }
    KEYS.put(WurbletTokenTypes.DIRECTIVE_NAME, pack(DIRECTIVE_NAME));
    KEYS.put(WurbletTokenTypes.DIRECTIVE_TEXT, pack(DIRECTIVE_ARGUMENT));
    KEYS.put(WurbletTokenTypes.WURBLET_CODE, pack(WURBLET_LEVEL));
    KEYS.put(WurbletTokenTypes.EXPR_CODE, pack(WURBLET_LEVEL));
    KEYS.put(WurbletTokenTypes.OUTPUT_TEXT, pack(OUTPUT_LEVEL));
    KEYS.put(WurbletTokenTypes.ESCAPED_AT, pack(ESCAPE));
    KEYS.put(WurbletTokenTypes.ESCAPED_CHAR, pack(ESCAPE));
    KEYS.put(WurbletTokenTypes.ESCAPED_SPACE, pack(ESCAPE));
    KEYS.put(WurbletTokenTypes.BAD_AT, pack(BAD_AT));
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new WurbletLexer();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    TextAttributesKey[] keys = KEYS.get(tokenType);
    return keys == null ? TextAttributesKey.EMPTY_ARRAY : keys;
  }
}
