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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.inject.WurbletJavaSupport;
import org.wurbelizer.idea.model.WurbletHeader;
import org.wurbelizer.idea.psi.WurbletDirective;
import org.wurbelizer.idea.psi.WurbletFile;

/**
 * The reference from {@code @{extends}@}, {@code @{implements}@} and {@code @{import}@} to the
 * Java class or package.
 * <p>
 * A simple name is resolved through the imports and the package of the wurblet, exactly as
 * javac would when compiling the generated wurblet source.
 */
public class WurbletClassReference extends PsiReferenceBase<WurbletDirective> {

  private final boolean allowPackage;

  /**
   * Creates the reference.
   *
   * @param directive the directive
   * @param rangeInElement the range of the class name within the directive
   * @param allowPackage true if the reference may denote a package, as for {@code @{import}@}
   */
  public WurbletClassReference(@NotNull WurbletDirective directive, @NotNull TextRange rangeInElement,
                               boolean allowPackage) {
    super(directive, rangeInElement, true);
    this.allowPackage = allowPackage;
  }

  @Override
  public @Nullable PsiElement resolve() {
    if (!(getElement().getContainingFile() instanceof WurbletFile file)) {
      return null;
    }
    String name = getValue();
    if (name.isEmpty()) {
      return null;
    }
    GlobalSearchScope scope = WurbletJavaSupport.scopeOf(file);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    if (allowPackage) {
      String stripped = name.endsWith(".*") ? name.substring(0, name.length() - 2) : name;
      PsiClass psiClass = facade.findClass(stripped, scope);
      if (psiClass != null) {
        return psiClass;
      }
      PsiPackage psiPackage = facade.findPackage(stripped);
      if (psiPackage != null) {
        return psiPackage;
      }
      return null;
    }
    WurbletHeader header = WurbletHeader.of(file);
    if (name.indexOf('.') >= 0) {
      return facade.findClass(name, scope);
    }
    for (String imp : header.getImports()) {
      String candidate = imp.endsWith(".*") ? imp.substring(0, imp.length() - 1) + name
                                            : imp.endsWith("." + name) ? imp : null;
      if (candidate != null) {
        PsiClass psiClass = facade.findClass(candidate, scope);
        if (psiClass != null) {
          return psiClass;
        }
      }
    }
    if (header.getPackageName() != null) {
      PsiClass psiClass = facade.findClass(header.getPackageName() + '.' + name, scope);
      if (psiClass != null) {
        return psiClass;
      }
    }
    return facade.findClass("org.wurbelizer.wurblet." + name, scope);
  }
}
