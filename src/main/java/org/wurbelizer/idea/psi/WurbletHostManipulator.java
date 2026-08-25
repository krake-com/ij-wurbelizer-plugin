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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Writes text back into an injection host.
 * <p>
 * The replacement is escaped, because an {@code @} in the injected text has to be written as
 * {@code \@} to survive the wurbiler.
 */
public class WurbletHostManipulator extends AbstractElementManipulator<WurbletHostElement> {

  @Override
  public @Nullable WurbletHostElement handleContentChange(@NotNull WurbletHostElement element,
                                                          @NotNull TextRange range,
                                                          String newContent) throws IncorrectOperationException {
    String text = element.getText();
    String replacement = text.substring(0, range.getStartOffset())
                         + WurbletEscaping.encode(newContent == null ? "" : newContent)
                         + text.substring(range.getEndOffset());
    WurbletHostElement created = WurbletElementFactory.createHostElement(
        element.getProject(), element.getClass(), replacement);
    if (created == null) {
      return element;
    }
    return element.replace(created) instanceof WurbletHostElement host ? host : element;
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull WurbletHostElement element) {
    return element.getRangeInsideHost();
  }
}
