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

package org.wurbelizer.idea.model;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.lang.WurbletLexer;
import org.wurbelizer.idea.lang.WurbletTokenTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the directives of a wurblet source from plain text.
 * <p>
 * Used for included files, which are ordinary text files as far as the IDE is concerned and
 * thus have no wurblet PSI of their own.
 */
public final class WurbletDirectiveScanner {

  /**
   * A directive found in the text.
   *
   * @param name the command word, in lower case
   * @param arguments the raw argument text, trimmed
   */
  public record Directive(@NotNull String name, @NotNull String arguments) {
  }

  private WurbletDirectiveScanner() {
  }

  /**
   * Scans a wurblet source for directives.
   *
   * @param text the source text
   * @return the directives in source order, never null
   */
  public static @NotNull List<Directive> scan(@NotNull CharSequence text) {
    List<Directive> directives = new ArrayList<>();
    Lexer lexer = new WurbletLexer();
    lexer.start(text, 0, text.length(), 0);
    String name = null;
    StringBuilder args = new StringBuilder();
    boolean inDirective = false;
    while (true) {
      IElementType type = lexer.getTokenType();
      if (type == null) {
        break;
      }
      if (type == WurbletTokenTypes.DIRECTIVE_START) {
        inDirective = true;
        name = null;
        args.setLength(0);
      }
      else if (inDirective) {
        if (type == WurbletTokenTypes.DIRECTIVE_NAME) {
          name = text.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString().toLowerCase(java.util.Locale.ROOT);
        }
        else if (type == WurbletTokenTypes.DIRECTIVE_TEXT) {
          args.append(text, lexer.getTokenStart(), lexer.getTokenEnd());
        }
        else if (type == WurbletTokenTypes.ESCAPED_AT) {
          args.append('@');
        }
        else if (type == WurbletTokenTypes.ESCAPED_CHAR) {
          args.append(text, lexer.getTokenStart(), lexer.getTokenEnd());
        }
        else if (type == WurbletTokenTypes.LEVEL_END) {
          if (name != null) {
            directives.add(new Directive(name, args.toString().trim()));
          }
          inDirective = false;
        }
      }
      lexer.advance();
    }
    if (inDirective && name != null) {
      directives.add(new Directive(name, args.toString().trim()));
    }
    return directives;
  }
}
