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

package org.wurbelizer.idea.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.lang.WurbletTokenTypes;

/**
 * Base class of all elements Java is injected into.
 * <p>
 * The injected range excludes the level markers, so the host of a {@code @[ ... ]@} block
 * carries the block including its markers while the injected text is what lies between them.
 * <p>
 * Every host is an {@link InjectionBackgroundSuppressor}, so the platform paints none of its
 * uniform injected fragment background here. Both levels are Java injected into the same host
 * file and would therefore come out in the same color; the two levels are told apart by
 * {@code WurbletLevelBackgroundAnnotator} instead.
 */
public abstract class WurbletHostElement extends ASTWrapperPsiElement
    implements PsiLanguageInjectionHost, InjectionBackgroundSuppressor {

  /**
   * Creates the element.
   *
   * @param node the AST node
   */
  protected WurbletHostElement(@NotNull ASTNode node) {
    super(node);
  }

  /**
   * Returns the range of the injected text within this element.
   * <p>
   * This is the element's text without the leading {@code @[}, {@code @(} and the trailing
   * level terminator, if present.
   *
   * @return the range, never null
   */
  public @NotNull TextRange getRangeInsideHost() {
    int start = 0;
    int end = getTextLength();
    ASTNode first = getNode().getFirstChildNode();
    if (first != null && WurbletTokenTypes.MARKERS.contains(first.getElementType())) {
      start = first.getTextLength();
    }
    ASTNode last = getNode().getLastChildNode();
    if (last != null && last != first && last.getElementType() == WurbletTokenTypes.LEVEL_END) {
      end -= last.getTextLength();
    }
    if (end < start) {
      end = start;
    }
    return new TextRange(start, end);
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public @NotNull PsiLanguageInjectionHost updateText(@NotNull String text) {
    WurbletHostElement replacement = WurbletElementFactory.createHostElement(getProject(), getClass(), text);
    if (replacement == null) {
      return this;
    }
    PsiElement result = replace(replacement);
    return result instanceof WurbletHostElement host ? host : this;
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new WurbletTextEscaper(this);
  }
}
