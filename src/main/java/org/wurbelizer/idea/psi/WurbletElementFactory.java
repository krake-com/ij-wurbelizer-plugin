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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.lang.WurbletFileType;

/**
 * Creates wurblet elements from text.
 */
public final class WurbletElementFactory {

  private WurbletElementFactory() {
  }

  /**
   * Parses the given text as a wurblet source.
   *
   * @param project the project
   * @param text the source text
   * @return the parsed file, never null
   */
  public static @NotNull WurbletFile createFile(@NotNull Project project, @NotNull String text) {
    return (WurbletFile) PsiFileFactory.getInstance(project)
        .createFileFromText("dummy." + WurbletFileType.EXTENSION, WurbletFileType.INSTANCE, text);
  }

  /**
   * Parses the given text and returns the first element of the requested type.
   *
   * @param project the project
   * @param type the requested element type
   * @param text the source text, including the level markers
   * @param <T> the element type
   * @return the element, null if the text does not parse to such an element
   */
  public static <T extends PsiElement> @Nullable T createHostElement(@NotNull Project project,
                                                                    @NotNull Class<T> type,
                                                                    @NotNull String text) {
    WurbletFile file = createFile(project, text);
    return PsiTreeUtil.findChildOfType(file, type);
  }
}
