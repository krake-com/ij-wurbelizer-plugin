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

package org.wurbelizer.idea.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.model.WurbletDirectiveType;
import org.wurbelizer.idea.psi.WurbletDirective;

import java.util.ArrayList;
import java.util.List;

/**
 * Contributes the references of the directive arguments: the included file, the parent class,
 * the implemented interfaces and the imports.
 */
public class WurbletReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(WurbletDirective.class), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!(element instanceof WurbletDirective directive)) {
          return PsiReference.EMPTY_ARRAY;
        }
        WurbletDirectiveType type = WurbletDirectiveType.forName(directive.getDirectiveName());
        if (type == null) {
          return PsiReference.EMPTY_ARRAY;
        }
        List<TextRange> ranges = directive.getArgumentRanges();
        if (ranges.isEmpty()) {
          return PsiReference.EMPTY_ARRAY;
        }
        List<PsiReference> references = new ArrayList<>();
        switch (type) {
          case INCLUDE -> references.add(new WurbletIncludeReference(directive, ranges.get(0)));
          case EXTENDS -> references.add(new WurbletClassReference(directive, ranges.get(0), false));
          case IMPLEMENTS -> {
            for (TextRange range : ranges) {
              references.add(new WurbletClassReference(directive, range, false));
            }
          }
          case IMPORT -> references.add(new WurbletClassReference(directive, ranges.get(0), true));
          default -> { }
        }
        return references.toArray(PsiReference.EMPTY_ARRAY);
      }
    });
  }
}
