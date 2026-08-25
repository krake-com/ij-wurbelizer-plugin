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

package org.wurbelizer.idea.inject;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ide.highlighter.JavaFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.model.WurbletHeader;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.settings.WurbletSettings;

/**
 * Java related helpers for the injection: resolving the wurblet's parent class and deciding
 * how the output level has to be wrapped.
 */
public final class WurbletJavaSupport {

  /** The wrapper of the output level fragment. */
  public enum Wrapper {

    /** The output level holds class members. */
    CLASS_BODY("class __WurbletOutput__ {\n", "\n}\n"),
    /** The output level holds statements. */
    STATEMENTS("class __WurbletOutput__ {\n  void __body__() {\n", "\n  }\n}\n");

    private final String prefix;
    private final String suffix;

    Wrapper(String prefix, String suffix) {
      this.prefix = prefix;
      this.suffix = suffix;
    }

    /**
     * Returns the text preceding the fragment.
     *
     * @return the prefix, never null
     */
    public @NotNull String getPrefix() {
      return prefix;
    }

    /**
     * Returns the text following the fragment.
     *
     * @return the suffix, never null
     */
    public @NotNull String getSuffix() {
      return suffix;
    }
  }

  private WurbletJavaSupport() {
  }

  /**
   * Resolves the parent class of the wurblet.
   * <p>
   * The parent may be written as a simple name, in which case the {@code @{import}@}
   * directives and the {@code @{package}@} of the wurblet are searched, exactly as javac
   * would when compiling the generated wurblet source.
   *
   * @param file the wurblet source
   * @param header the header of the wurblet source
   * @return the class, null if it cannot be resolved
   */
  public static @Nullable PsiClass resolveParentClass(@NotNull WurbletFile file, @NotNull WurbletHeader header) {
    String parent = header.getParentClass();
    Project project = file.getProject();
    GlobalSearchScope scope = scopeOf(file);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    if (parent.indexOf('.') >= 0) {
      return facade.findClass(parent, scope);
    }
    for (String imp : header.getImports()) {
      String candidate = imp.endsWith(".*") ? imp.substring(0, imp.length() - 1) + parent
                                            : imp.endsWith("." + parent) ? imp : null;
      if (candidate != null) {
        PsiClass psiClass = facade.findClass(candidate, scope);
        if (psiClass != null) {
          return psiClass;
        }
      }
    }
    if (header.getPackageName() != null) {
      PsiClass psiClass = facade.findClass(header.getPackageName() + '.' + parent, scope);
      if (psiClass != null) {
        return psiClass;
      }
    }
    PsiClass psiClass = facade.findClass("org.wurbelizer.wurblet." + parent, scope);
    return psiClass != null ? psiClass : facade.findClass("java.lang." + parent, scope);
  }

  /**
   * Returns whether the output level of the wurblet is Java.
   * <p>
   * A wurblet generates Java exactly if its parent class is
   * {@code org.wurbelizer.wurblet.AbstractJavaWurblet} or a subclass of it. When the parent
   * cannot be resolved the setting decides, defaulting to Java since that is what the vast
   * majority of wurblets generate.
   *
   * @param file the wurblet source
   * @param header the header of the wurblet source
   * @return true if the output level is Java
   */
  public static boolean isJavaOutput(@NotNull WurbletFile file, @NotNull WurbletHeader header) {
    WurbletSettings settings = WurbletSettings.getInstance(file.getProject());
    if (!settings.injectOutputLevel) {
      return false;
    }
    PsiClass parent = resolveParentClass(file, header);
    if (parent == null) {
      return settings.assumeJavaWhenParentUnresolved;
    }
    return InheritanceUtil.isInheritor(parent, true, WurbletHeader.JAVA_WURBLET_CLASS)
           || WurbletHeader.JAVA_WURBLET_CLASS.equals(parent.getQualifiedName());
  }

  /**
   * Returns the wrapper for the output level of a wurblet source.
   * <p>
   * In automatic mode the fragment is parsed both ways and the wrapper producing fewer syntax
   * errors wins, class body being the tie breaker because that is by far the common case.
   *
   * @param file the wurblet source
   * @return the wrapper, never null
   */
  public static @NotNull Wrapper wrapperOf(@NotNull WurbletFile file) {
    WurbletSettings settings = WurbletSettings.getInstance(file.getProject());
    return switch (settings.outputWrapper) {
      case CLASS_BODY -> Wrapper.CLASS_BODY;
      case STATEMENTS -> Wrapper.STATEMENTS;
      case AUTO -> CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
          chooseWrapper(file), PsiModificationTracker.MODIFICATION_COUNT));
    };
  }

  private static Wrapper chooseWrapper(WurbletFile file) {
    String fragment = WurbletOutputModel.of(file).getText();
    if (fragment.isBlank()) {
      return Wrapper.CLASS_BODY;
    }
    int classBodyErrors = countErrors(file.getProject(), Wrapper.CLASS_BODY, fragment);
    int statementErrors = countErrors(file.getProject(), Wrapper.STATEMENTS, fragment);
    return statementErrors < classBodyErrors ? Wrapper.STATEMENTS : Wrapper.CLASS_BODY;
  }

  private static int countErrors(Project project, Wrapper wrapper, String fragment) {
    try {
      PsiFile parsed = PsiFileFactory.getInstance(project).createFileFromText(
          "__wurblet_output__.java", JavaFileType.INSTANCE, wrapper.getPrefix() + fragment + wrapper.getSuffix());
      return PsiTreeUtil.findChildrenOfType(parsed, PsiErrorElement.class).size();
    }
    catch (RuntimeException ex) {
      return Integer.MAX_VALUE;
    }
  }

  /**
   * Returns the search scope for resolving Java references of a wurblet source.
   *
   * @param file the wurblet source
   * @return the scope, never null
   */
  public static @NotNull GlobalSearchScope scopeOf(@NotNull WurbletFile file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file.getOriginalFile());
    return module == null ? GlobalSearchScope.allScope(file.getProject())
                          : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
  }
}
