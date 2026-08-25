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

package org.wurbelizer.idea.lang;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lexer for wurblet sources.
 * <p>
 * This is a hand written port of the character level state machine in
 * {@code org.wurbelizer.wurbile.AbstractWurbiler#compile()}. The rules that are easy to get
 * wrong, and that the documentation states differently from the implementation:
 * <ul>
 *   <li>the file starts at output level</li>
 *   <li>closing brackets are <em>not</em> matched against the opener: each of {@code ]@},
 *       {@code )@} and <code>}@</code> closes whatever level is currently open</li>
 *   <li>within a level, a further {@code @[}, {@code @(} or <code>@{</code> only increments a
 *       nesting counter and stays part of the enclosed text</li>
 *   <li>{@code \@} is a literal {@code @}, backslash-blank is dropped entirely, and a
 *       backslash before anything else keeps both characters (so {@code \\} stays
 *       {@code \\}); there is no line continuation in a {@code .wrbl} file</li>
 *   <li>an {@code @} that is not followed by <code>{</code>, {@code (} or {@code [} is an
 *       unknown @-sequence: the wurbiler warns and keeps both characters</li>
 *   <li>{@code )@(} at output level is a legal special case</li>
 * </ul>
 */
public class WurbletLexer extends LexerBase {

  /** Output level, i.e. the generated text. */
  public static final int LEVEL_OUTPUT = 0;
  /** Wurblet level, i.e. within {@code @[ ]@}. */
  public static final int LEVEL_BLOCK = 1;
  /** Value extraction, i.e. within {@code @( )@}. */
  public static final int LEVEL_EXPR = 2;
  /** Directive, i.e. within <code>@{ }@</code>. */
  public static final int LEVEL_DIRECTIVE = 3;

  private static final int LEVEL_MASK = 0x03;
  private static final int NAME_READ = 0x04;
  private static final int NESTING_SHIFT = 3;

  private CharSequence buffer;
  private int bufferEnd;
  private int tokenStart;
  private int tokenEnd;
  private IElementType tokenType;
  private int tokenState;     // state at tokenStart, this is what getState() reports

  private int level;
  private int nesting;
  private boolean nameRead;   // directive command word already lexed

  /**
   * Encodes the lexer state.
   *
   * @param level the code level
   * @param nesting the nesting counter
   * @param nameRead true if the directive command word has been lexed
   * @return the encoded state
   */
  public static int encodeState(int level, int nesting, boolean nameRead) {
    return (level & LEVEL_MASK) | (nameRead ? NAME_READ : 0) | (nesting << NESTING_SHIFT);
  }

  /**
   * Extracts the code level from an encoded state.
   *
   * @param state the encoded state
   * @return the code level
   */
  public static int levelOf(int state) {
    return state & LEVEL_MASK;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.buffer = buffer;
    this.bufferEnd = endOffset;
    this.tokenEnd = startOffset;
    this.level = levelOf(initialState);
    this.nesting = initialState >> NESTING_SHIFT;
    this.nameRead = (initialState & NAME_READ) != 0;
    advance();
  }

  @Override
  public void advance() {
    tokenState = encodeState(level, nesting, nameRead);
    tokenStart = tokenEnd;
    if (tokenStart >= bufferEnd) {
      tokenType = null;
      return;
    }
    locate();
  }

  @Override
  public int getState() {
    return tokenState;
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return tokenType;
  }

  @Override
  public int getTokenStart() {
    return tokenStart;
  }

  @Override
  public int getTokenEnd() {
    return tokenEnd;
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return bufferEnd;
  }

  /**
   * Locates the token starting at {@link #tokenStart} and updates the level state.
   */
  private void locate() {
    char c = buffer.charAt(tokenStart);

    // the wurbiler tests for the backslash before anything else, on every level
    if (c == '\\') {
      if (tokenStart + 1 < bufferEnd) {
        char n = buffer.charAt(tokenStart + 1);
        tokenEnd = tokenStart + 2;
        tokenType = n == '@' ? WurbletTokenTypes.ESCAPED_AT
                             : n == ' ' ? WurbletTokenTypes.ESCAPED_SPACE
                                        : WurbletTokenTypes.ESCAPED_CHAR;
      }
      else {
        tokenEnd = bufferEnd;
        tokenType = WurbletTokenTypes.ESCAPED_CHAR;
      }
      return;
    }

    if (level == LEVEL_OUTPUT) {
      locateOutput(c);
    }
    else {
      locateWurblet(c);
    }
  }

  /**
   * Locates a token at output level.
   *
   * @param c the character at {@link #tokenStart}
   */
  private void locateOutput(char c) {
    if (c == '@') {
      if (tokenStart + 1 < bufferEnd && isOpen(buffer.charAt(tokenStart + 1))) {
        char open = buffer.charAt(tokenStart + 1);
        tokenEnd = tokenStart + 2;
        nesting = 0;
        nameRead = false;
        switch (open) {
          case '[' -> {
            level = LEVEL_BLOCK;
            tokenType = WurbletTokenTypes.BLOCK_START;
          }
          case '(' -> {
            level = LEVEL_EXPR;
            tokenType = WurbletTokenTypes.EXPR_START;
          }
          default -> {
            level = LEVEL_DIRECTIVE;
            tokenType = WurbletTokenTypes.DIRECTIVE_START;
          }
        }
      }
      else {
        // unknown @-sequence: the wurbiler warns and keeps both characters
        tokenEnd = Math.min(tokenStart + 2, bufferEnd);
        tokenType = WurbletTokenTypes.BAD_AT;
      }
      return;
    }

    if (isLevelEnd(tokenStart)) {
      // closed although nothing is open: an error, flagged by the annotator
      tokenEnd = tokenStart + 2;
      tokenType = WurbletTokenTypes.LEVEL_END;
      return;
    }

    int q = tokenStart;
    while (q < bufferEnd && !stopsOutputText(q)) {
      q++;
    }
    tokenEnd = q;
    tokenType = WurbletTokenTypes.OUTPUT_TEXT;
  }

  /**
   * Returns whether a run of output level text must end at the given offset.
   *
   * @param offset the offset
   * @return true if the text run ends here
   */
  private boolean stopsOutputText(int offset) {
    char ch = buffer.charAt(offset);
    return ch == '\\' || ch == '@' || isLevelEnd(offset);
  }

  /**
   * Returns whether the given offset holds a level terminator.
   * <p>
   * The sequence {@code )@(} is not a terminator: the wurbiler explicitly accepts it as a
   * closing round brace at output level directly followed by a new value extraction.
   *
   * @param offset the offset
   * @return true if a {@code ]@}, {@code )@} or <code>}@</code> starts here
   */
  private boolean isLevelEnd(int offset) {
    char ch = buffer.charAt(offset);
    if (!isClose(ch) || offset + 1 >= bufferEnd || buffer.charAt(offset + 1) != '@') {
      return false;
    }
    return !(ch == ')' && offset + 2 < bufferEnd && buffer.charAt(offset + 2) == '(');
  }

  /**
   * Locates a token within a wurblet level, a value extraction or a directive.
   *
   * @param c the character at {@link #tokenStart}
   */
  private void locateWurblet(char c) {
    if (nesting == 0 && isClose(c) && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '@') {
      tokenEnd = tokenStart + 2;
      tokenType = WurbletTokenTypes.LEVEL_END;
      level = LEVEL_OUTPUT;
      nameRead = false;
      return;
    }

    if (level == LEVEL_DIRECTIVE && !nameRead) {
      if (Character.isWhitespace(c)) {
        int q = tokenStart;
        while (q < bufferEnd && Character.isWhitespace(buffer.charAt(q))) {
          q++;
        }
        tokenEnd = q;
        tokenType = TokenType.WHITE_SPACE;
        return;
      }
      int q = tokenStart;
      while (q < bufferEnd
             && !Character.isWhitespace(buffer.charAt(q))
             && buffer.charAt(q) != '\\'
             && buffer.charAt(q) != '@'
             && !isLevelEndRaw(q)) {
        q++;
      }
      if (q > tokenStart) {
        tokenEnd = q;
        tokenType = WurbletTokenTypes.DIRECTIVE_NAME;
        nameRead = true;
        return;
      }
      // cannot form a name here, fall through to the generic scan
    }

    int nest = nesting;
    int q = tokenStart;
    while (q < bufferEnd) {
      char ch = buffer.charAt(q);
      if (ch == '\\') {
        break;
      }
      if (ch == '@') {
        if (q + 1 < bufferEnd && isOpen(buffer.charAt(q + 1))) {
          nest++;
          q += 2;
        }
        else {
          q = Math.min(q + 2, bufferEnd);
        }
        continue;
      }
      if (isLevelEndRaw(q)) {
        if (nest > 0) {
          nest--;
          q += 2;
          continue;
        }
        break;
      }
      q++;
    }
    tokenEnd = q;
    nesting = nest;
    tokenType = switch (level) {
      case LEVEL_BLOCK -> WurbletTokenTypes.WURBLET_CODE;
      case LEVEL_EXPR -> WurbletTokenTypes.EXPR_CODE;
      default -> WurbletTokenTypes.DIRECTIVE_TEXT;
    };
  }

  /**
   * Returns whether a level terminator starts at the given offset, without the
   * {@code )@(} exception, which only applies at output level.
   *
   * @param offset the offset
   * @return true if a {@code ]@}, {@code )@} or <code>}@</code> starts here
   */
  private boolean isLevelEndRaw(int offset) {
    return isClose(buffer.charAt(offset))
           && offset + 1 < bufferEnd
           && buffer.charAt(offset + 1) == '@';
  }

  /**
   * Returns whether the character opens a level.
   *
   * @param c the character
   * @return true if <code>{</code>, {@code (} or {@code [}
   */
  private static boolean isOpen(char c) {
    return c == '{' || c == '(' || c == '[';
  }

  /**
   * Returns whether the character closes a level.
   *
   * @param c the character
   * @return true if <code>}</code>, {@code )} or {@code ]}
   */
  private static boolean isClose(char c) {
    return c == '}' || c == ')' || c == ']';
  }
}
