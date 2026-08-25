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

package org.wurbelizer.idea.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.psi.ContributedReferenceHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.lang.WurbletTokenTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A <code>@{ ... }@</code> wurbiler directive.
 */
public class WurbletDirective extends ASTWrapperPsiElement implements ContributedReferenceHost {

  /**
   * Creates the element.
   *
   * @param node the AST node
   */
  public WurbletDirective(@NotNull ASTNode node) {
    super(node);
  }

  /**
   * Returns the node holding the command word.
   *
   * @return the node, null if the directive has no command word
   */
  public @Nullable ASTNode getNameNode() {
    return getNode().findChildByType(WurbletTokenTypes.DIRECTIVE_NAME);
  }

  /**
   * Returns the command word.
   *
   * @return the command word in lower case, null if the directive has no command word
   */
  public @Nullable String getDirectiveName() {
    ASTNode name = getNameNode();
    return name == null ? null : name.getText().toLowerCase(Locale.ROOT);
  }

  /**
   * Returns the range of the arguments within this element.
   * <p>
   * The range starts after the command word, or after the opening <code>@{</code> if there is
   * none, and ends before the level terminator.
   *
   * @return the range, never null, possibly empty
   */
  public @NotNull TextRange getArgumentsRange() {
    int base = getTextRange().getStartOffset();
    int start = 0;
    ASTNode name = getNameNode();
    if (name != null) {
      start = name.getTextRange().getEndOffset() - base;
    }
    else {
      ASTNode first = getNode().getFirstChildNode();
      if (first != null && first.getElementType() == WurbletTokenTypes.DIRECTIVE_START) {
        start = first.getTextLength();
      }
    }
    int end = getTextLength();
    ASTNode last = getNode().getLastChildNode();
    if (last != null && last.getElementType() == WurbletTokenTypes.LEVEL_END) {
      end -= last.getTextLength();
    }
    if (end < start) {
      end = start;
    }
    return new TextRange(start, end);
  }

  /**
   * Returns the raw argument text.
   *
   * @return the text, never null
   */
  public @NotNull String getArgumentsText() {
    return getArgumentsRange().substring(getText());
  }

  /**
   * Returns the ranges of the whitespace separated argument tokens, relative to this element.
   *
   * @return the ranges, never null
   */
  public @NotNull List<TextRange> getArgumentRanges() {
    List<TextRange> ranges = new ArrayList<>();
    TextRange args = getArgumentsRange();
    String text = getText();
    int i = args.getStartOffset();
    while (i < args.getEndOffset()) {
      while (i < args.getEndOffset() && Character.isWhitespace(text.charAt(i))) {
        i++;
      }
      int start = i;
      while (i < args.getEndOffset() && !Character.isWhitespace(text.charAt(i))) {
        i++;
      }
      if (i > start) {
        ranges.add(new TextRange(start, i));
      }
    }
    return ranges;
  }

  /**
   * Returns the whitespace separated arguments.
   *
   * @return the arguments, never null
   */
  public @NotNull List<String> getArguments() {
    List<String> args = new ArrayList<>();
    String text = getText();
    for (TextRange range : getArgumentRanges()) {
      args.add(range.substring(text));
    }
    return args;
  }

  /**
   * Returns the first argument.
   *
   * @return the first argument, null if there is none
   */
  public @Nullable String getFirstArgument() {
    List<String> args = getArguments();
    return args.isEmpty() ? null : args.get(0);
  }

  /**
   * Returns whether this directive is closed by a level terminator.
   *
   * @return true if closed
   */
  public boolean isClosed() {
    ASTNode last = getNode().getLastChildNode();
    return last != null && last.getElementType() == WurbletTokenTypes.LEVEL_END;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public String toString() {
    return "WurbletDirective(" + getDirectiveName() + ")";
  }
}
