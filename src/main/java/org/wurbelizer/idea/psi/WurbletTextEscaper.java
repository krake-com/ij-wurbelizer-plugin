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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import org.jetbrains.annotations.NotNull;

/**
 * Decodes the wurblet escape sequences of an injection host.
 * <p>
 * The injected document sees the text the wurbiler would produce: {@code \@} becomes
 * {@code @} and {@code \ } becomes a blank. The offset table maps every
 * decoded character back to its offset within the host element, so that edits made in the
 * injected document land in the right place.
 */
public class WurbletTextEscaper extends LiteralTextEscaper<WurbletHostElement> {

  private int[] offsets;    // decoded offset -> offset within the host element

  /**
   * Creates the escaper.
   *
   * @param host the injection host
   */
  public WurbletTextEscaper(@NotNull WurbletHostElement host) {
    super(host);
  }

  @Override
  public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    String text = myHost.getText();
    int start = rangeInsideHost.getStartOffset();
    int end = Math.min(rangeInsideHost.getEndOffset(), text.length());
    int[] map = new int[end - start + 1];
    int decoded = 0;
    int i = start;
    while (i < end) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < end) {
        char n = text.charAt(i + 1);
        if (n == '@' || n == ' ') {
          // the wurbiler emits the char without the backslash
          map[decoded++] = i;
          outChars.append(n);
          i += 2;
          continue;
        }
        map[decoded++] = i;
        outChars.append(c);
        map[decoded++] = i + 1;
        outChars.append(n);
        i += 2;
        continue;
      }
      map[decoded++] = i;
      outChars.append(c);
      i++;
    }
    map[decoded] = end;
    offsets = new int[decoded + 1];
    System.arraycopy(map, 0, offsets, 0, decoded + 1);
    return true;
  }

  @Override
  public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
    if (offsets == null) {
      return rangeInsideHost.getStartOffset() + offsetInDecoded;
    }
    if (offsetInDecoded < 0) {
      return -1;
    }
    if (offsetInDecoded >= offsets.length) {
      return rangeInsideHost.getEndOffset();
    }
    return offsets[offsetInDecoded];
  }

  @Override
  public boolean isOneLine() {
    return false;
  }

  @Override
  public @NotNull TextRange getRelevantTextRange() {
    return myHost.getRangeInsideHost();
  }
}
