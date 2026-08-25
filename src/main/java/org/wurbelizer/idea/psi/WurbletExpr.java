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

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * A {@code @( ... )@} value extraction.
 * <p>
 * The wurbiler translates it into {@code <stream>.print(<expression>);}.
 */
public class WurbletExpr extends WurbletHostElement {

  /**
   * Creates the element.
   *
   * @param node the AST node
   */
  public WurbletExpr(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "WurbletExpr";
  }
}
