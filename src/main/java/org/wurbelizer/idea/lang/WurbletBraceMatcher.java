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

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches the level switching markers.
 * <p>
 * The wurbiler accepts any of {@code ]@}, {@code )@} and <code>}@</code> as the terminator of
 * any level, so all three openers pair with the same terminator token.
 */
public class WurbletBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = {
      new BracePair(WurbletTokenTypes.BLOCK_START, WurbletTokenTypes.LEVEL_END, true),
      new BracePair(WurbletTokenTypes.EXPR_START, WurbletTokenTypes.LEVEL_END, false),
      new BracePair(WurbletTokenTypes.DIRECTIVE_START, WurbletTokenTypes.LEVEL_END, false),
  };

  @Override
  public BracePair @NotNull [] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType tokenType) {
    return true;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
