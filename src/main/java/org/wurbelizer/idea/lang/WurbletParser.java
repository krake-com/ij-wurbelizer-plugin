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

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the wurblet PSI tree.
 * <p>
 * The tree is deliberately flat: a file is a sequence of output segments, {@code @[ ]@}
 * blocks, {@code @( )@} value extractions and <code>@{ }@</code> directives. All Java
 * understanding comes from the language injection, not from this parser.
 */
public class WurbletParser implements PsiParser {

  @Override
  public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    PsiBuilder.Marker file = builder.mark();
    PsiBuilder.Marker content = builder.mark();
    while (!builder.eof()) {
      IElementType type = builder.getTokenType();
      if (type == WurbletTokenTypes.BLOCK_START) {
        parseEnclosed(builder, WurbletTokenTypes.WURBLET_BLOCK, WurbletTokenTypes.WURBLET_CODE);
      }
      else if (type == WurbletTokenTypes.EXPR_START) {
        parseEnclosed(builder, WurbletTokenTypes.WURBLET_EXPR, WurbletTokenTypes.EXPR_CODE);
      }
      else if (type == WurbletTokenTypes.DIRECTIVE_START) {
        parseDirective(builder);
      }
      else if (WurbletTokenTypes.OUTPUT_CONTENT.contains(type)) {
        parseOutputSegment(builder);
      }
      else {
        // a stray level terminator at output level, kept as a top level token so that the
        // annotator can flag it and the injector can ignore it
        builder.advanceLexer();
      }
    }
    content.done(WurbletTokenTypes.WURBLET_CONTENT);
    file.done(root);
    return builder.getTreeBuilt();
  }

  /**
   * Parses a {@code @[ ]@} block or a {@code @( )@} value extraction.
   *
   * @param builder the builder
   * @param composite the element type to create
   * @param content the token type of the enclosed code
   */
  private void parseEnclosed(@NotNull PsiBuilder builder, @NotNull IElementType composite, @NotNull IElementType content) {
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();   // the opening marker
    while (!builder.eof()) {
      IElementType type = builder.getTokenType();
      if (type == WurbletTokenTypes.LEVEL_END) {
        builder.advanceLexer();
        break;
      }
      if (type != content && !WurbletTokenTypes.ESCAPES.contains(type)) {
        break;
      }
      builder.advanceLexer();
    }
    marker.done(composite);
  }

  /**
   * Parses a <code>@{ }@</code> directive.
   *
   * @param builder the builder
   */
  private void parseDirective(@NotNull PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();   // the opening marker
    while (!builder.eof()) {
      IElementType type = builder.getTokenType();
      if (type == WurbletTokenTypes.LEVEL_END) {
        builder.advanceLexer();
        break;
      }
      if (type != WurbletTokenTypes.DIRECTIVE_NAME
          && type != WurbletTokenTypes.DIRECTIVE_TEXT
          && !WurbletTokenTypes.ESCAPES.contains(type)) {
        break;
      }
      builder.advanceLexer();
    }
    marker.done(WurbletTokenTypes.WURBLET_DIRECTIVE);
  }

  /**
   * Parses a run of output level text.
   *
   * @param builder the builder
   */
  private void parseOutputSegment(@NotNull PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    while (!builder.eof() && WurbletTokenTypes.OUTPUT_CONTENT.contains(builder.getTokenType())) {
      builder.advanceLexer();
    }
    marker.done(WurbletTokenTypes.OUTPUT_SEGMENT);
  }
}
