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

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.psi.WurbletBlock;
import org.wurbelizer.idea.psi.WurbletContent;
import org.wurbelizer.idea.psi.WurbletDirective;
import org.wurbelizer.idea.psi.WurbletExpr;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.psi.WurbletOutputSegment;

/** Parser definition of the wurblet language. */
public class WurbletParserDefinition implements ParserDefinition {

  private static final TokenSet WHITESPACE = TokenSet.create(TokenType.WHITE_SPACE);

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new WurbletLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new WurbletParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return WurbletTokenTypes.FILE;
  }

  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return WHITESPACE;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    IElementType type = node.getElementType();
    if (type == WurbletTokenTypes.WURBLET_CONTENT) {
      return new WurbletContent(node);
    }
    if (type == WurbletTokenTypes.WURBLET_BLOCK) {
      return new WurbletBlock(node);
    }
    if (type == WurbletTokenTypes.WURBLET_EXPR) {
      return new WurbletExpr(node);
    }
    if (type == WurbletTokenTypes.OUTPUT_SEGMENT) {
      return new WurbletOutputSegment(node);
    }
    if (type == WurbletTokenTypes.WURBLET_DIRECTIVE) {
      return new WurbletDirective(node);
    }
    throw new IllegalArgumentException("unknown element type " + type);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new WurbletFile(viewProvider);
  }
}
