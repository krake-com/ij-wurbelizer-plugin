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

package org.wurbelizer.idea.inject;

import com.intellij.codeInsight.ImportFilter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.psi.WurbletHostElement;

/**
 * Makes Java use fully qualified names within the injected code of a wurblet source.
 * <p>
 * An injected document is assembled from ranges of the {@code .wrbl} file plus synthetic
 * prefixes. Its import section belongs to such a prefix, so an import statement added by a
 * completion or an intention would be written to text that does not exist in the file and is
 * lost. A wurblet declares its imports with the {@code @{import}@} directive instead, and a
 * fully qualified name is valid at both code levels, so that is what gets inserted. Once the
 * class is covered by an {@code @{import}@} directive, Java uses the simple name again on its
 * own.
 */
public class WurbletImportFilter extends ImportFilter {

  @Override
  public boolean shouldUseFullyQualifiedName(@NotNull PsiFile targetFile, @NotNull String classQualifiedName) {
    PsiElement host = InjectedLanguageManager.getInstance(targetFile.getProject()).getInjectionHost(targetFile);
    return host instanceof WurbletHostElement;
  }
}
