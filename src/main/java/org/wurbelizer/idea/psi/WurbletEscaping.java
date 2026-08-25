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

/**
 * Encoding and decoding of the wurblet escape sequences.
 * <p>
 * Mirrors the backslash handling of {@code org.wurbelizer.wurbile.AbstractWurbiler}:
 * {@code \@} stands for a literal {@code @}, a backslash followed by a blank is dropped
 * entirely and a backslash followed by anything else keeps both characters.
 */
public final class WurbletEscaping {

  private WurbletEscaping() {
  }

  /**
   * Encodes text so that the wurbiler reproduces it verbatim.
   * <p>
   * Only the {@code @} needs escaping. A backslash directly followed by a blank cannot be
   * expressed at all and is left alone.
   *
   * @param text the text
   * @return the encoded text
   */
  public static String encode(String text) {
    if (text.indexOf('@') < 0) {
      return text;
    }
    StringBuilder buf = new StringBuilder(text.length() + 8);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '@') {
        buf.append('\\');
      }
      buf.append(c);
    }
    return buf.toString();
  }

  /**
   * Decodes the escape sequences of a wurblet source text.
   * <p>
   * This is what the wurbiler passes on: {@code \@} becomes {@code @} and a backslash
   * followed by a blank disappears.
   *
   * @param text the raw text
   * @return the decoded text
   */
  public static String decode(String text) {
    if (text.indexOf('\\') < 0) {
      return text;
    }
    StringBuilder buf = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < text.length()) {
        char n = text.charAt(i + 1);
        if (n == '@') {
          buf.append('@');
          i += 2;
          continue;
        }
        if (n == ' ') {
          i += 2;
          continue;
        }
        buf.append(c).append(n);
        i += 2;
        continue;
      }
      buf.append(c);
      i++;
    }
    return buf.toString();
  }
}
