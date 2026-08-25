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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.model.WurbletVariables;
import org.wurbelizer.idea.psi.WurbletDirective;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.settings.WurbletSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The reference from {@code @{include <filename>}@} to the included file.
 * <p>
 * The filename may contain {@code $}-variables. {@code $currentDir} is the directory of the
 * wurblet source being compiled, which is what makes the Tentackle idiom
 * {@code @{include $currentDir/header.incl}@} work.
 */
public class WurbletIncludeReference extends PsiReferenceBase<WurbletDirective> {

  /**
   * Creates the reference.
   *
   * @param directive the include directive
   * @param rangeInElement the range of the filename within the directive
   */
  public WurbletIncludeReference(@NotNull WurbletDirective directive, @NotNull TextRange rangeInElement) {
    super(directive, rangeInElement, true);
  }

  @Override
  public @Nullable PsiElement resolve() {
    WurbletDirective directive = getElement();
    if (!(directive.getContainingFile() instanceof WurbletFile file)) {
      return null;
    }
    VirtualFile self = file.getOriginalFile().getVirtualFile();
    VirtualFile baseDir = self == null ? null : self.getParent();
    VirtualFile target = WurbletVariables.resolve(
        WurbletVariables.substitute(getValue(), variablesOf(file, self, baseDir)), baseDir);
    return target == null || target.isDirectory() ? null : PsiManager.getInstance(file.getProject()).findFile(target);
  }

  /**
   * Returns the variables available for include paths.
   *
   * @param file the wurblet source
   * @param self the wurblet source file
   * @param baseDir the directory of the wurblet source
   * @return the variables, never null
   */
  public static @NotNull Map<String, String> variablesOf(@NotNull WurbletFile file,
                                                         @Nullable VirtualFile self,
                                                         @Nullable VirtualFile baseDir) {
    Map<String, String> variables = new LinkedHashMap<>();
    if (baseDir != null) {
      variables.put(WurbletVariables.CURRENT_DIR, baseDir.getPath());
    }
    if (self != null) {
      variables.put(WurbletVariables.CURRENT_FILE, self.getName());
    }
    variables.putAll(WurbletSettings.getInstance(file.getProject()).variables);
    return variables;
  }
}
