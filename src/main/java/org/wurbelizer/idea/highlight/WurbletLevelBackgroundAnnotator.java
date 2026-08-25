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

package org.wurbelizer.idea.highlight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.psi.WurbletBlock;
import org.wurbelizer.idea.psi.WurbletDirective;
import org.wurbelizer.idea.psi.WurbletExpr;
import org.wurbelizer.idea.psi.WurbletOutputSegment;

/**
 * Gives the two code levels their background.
 * <p>
 * The tint is painted over whole elements, markers included, rather than per token: the lexer
 * emits the escape sequences of <em>every</em> level as the same token types, so a {@code \n}
 * within a Java string literal at wurblet level cannot be told from a {@code \@} in output text
 * by its token type alone. A {@link com.intellij.openapi.fileTypes.SyntaxHighlighter} sees
 * nothing but the token type and would stripe the wurblet level wherever a string escape
 * occurs.
 * <p>
 * The attributes carry a background only, so they compose with the foregrounds of the injected
 * Java instead of replacing them. What makes them visible in the first place is that
 * {@link org.wurbelizer.idea.psi.WurbletHostElement} suppresses the uniform background the
 * platform paints over every injected fragment.
 */
public class WurbletLevelBackgroundAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    TextAttributesKey key = levelOf(element);
    if (key != null) {
      holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
          .range(element)
          .textAttributes(key)
          .create();
    }
  }

  /**
   * Returns the background of the level the given element belongs to.
   * <p>
   * A {@code @{ }@} directive counts as wurblet level: it configures the generator and is
   * nothing the wurblet emits.
   *
   * @param element the element
   * @return the attributes key, null if the element is not a level of its own
   */
  private static @Nullable TextAttributesKey levelOf(@NotNull PsiElement element) {
    if (element instanceof WurbletBlock || element instanceof WurbletExpr
        || element instanceof WurbletDirective) {
      return WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND;
    }
    if (element instanceof WurbletOutputSegment) {
      return WurbletSyntaxHighlighter.OUTPUT_LEVEL_BACKGROUND;
    }
    return null;
  }
}
