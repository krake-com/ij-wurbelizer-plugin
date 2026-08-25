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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.psi.WurbletOutputSegment;

/**
 * Drops the diagnostics of the output level injection.
 * <p>
 * The output level is a fragment of code with holes: a value extraction may stand for a
 * modifier, a type, a name or a whole statement, so the reconstructed Java is a best effort
 * parse and its errors say nothing about the wurblet. Errors of the <em>wurblet level</em>
 * injection are real compile errors and are kept.
 */
public class WurbletOutputHighlightFilter implements HighlightInfoFilter {

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (file == null || highlightInfo.getSeverity().compareTo(HighlightSeverity.WEAK_WARNING) < 0) {
      return true;
    }
    return !isOutputLevelInjection(file);
  }

  /**
   * Returns whether the given file is the injected output level of a wurblet source.
   *
   * @param file the file
   * @return true if it is the output level injection
   */
  public static boolean isOutputLevelInjection(@NotNull PsiFile file) {
    PsiElement host = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file);
    return host instanceof WurbletOutputSegment;
  }
}
