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

package org.wurbelizer.idea.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/** An element type of the wurblet language. */
public class WurbletElementType extends IElementType {

  /**
   * Creates an element type.
   *
   * @param debugName the name for debugging purposes
   */
  public WurbletElementType(@NotNull @NonNls String debugName) {
    super(debugName, WurbletLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return "Wurblet:" + super.toString();
  }
}
