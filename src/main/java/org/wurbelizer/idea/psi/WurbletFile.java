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

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.lang.WurbletFileType;
import org.wurbelizer.idea.lang.WurbletLanguage;

import java.util.Collection;

/**
 * A wurblet source file.
 */
public class WurbletFile extends PsiFileBase {

  /**
   * Creates the file.
   *
   * @param viewProvider the view provider
   */
  public WurbletFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, WurbletLanguage.INSTANCE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return WurbletFileType.INSTANCE;
  }

  /**
   * Returns the name of the wurblet, i.e. the filename without the extension.
   * <p>
   * This is the class name the wurbiler generates.
   *
   * @return the wurblet name, never null
   */
  public @NotNull String getWurbletName() {
    String name = getName();
    int ndx = name.lastIndexOf('.');
    return ndx > 0 ? name.substring(0, ndx) : name;
  }

  /**
   * Returns the content element, i.e. the single child holding everything.
   *
   * @return the content, null only for an empty file
   */
  public @Nullable WurbletContent getContent() {
    return PsiTreeUtil.findChildOfType(this, WurbletContent.class);
  }

  /**
   * Returns all directives of this file, in source order.
   *
   * @return the directives, never null
   */
  public @NotNull Collection<WurbletDirective> getDirectives() {
    return PsiTreeUtil.findChildrenOfType(this, WurbletDirective.class);
  }

  @Override
  public String toString() {
    return "Wurblet source " + getName();
  }
}
