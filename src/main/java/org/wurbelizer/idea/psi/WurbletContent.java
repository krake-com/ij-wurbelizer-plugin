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
import org.jetbrains.annotations.NotNull;

/**
 * The content of a wurblet source, i.e. everything below the file element.
 * <p>
 * This wrapper exists for the language injection. The platform probes for injections by
 * walking up from the element under the caret and stops one level below the containing file,
 * so an injector that needs the whole file as its context needs an element that is not the
 * file itself.
 */
public class WurbletContent extends ASTWrapperPsiElement {

  /**
   * Creates the element.
   *
   * @param node the AST node
   */
  public WurbletContent(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "WurbletContent";
  }
}
