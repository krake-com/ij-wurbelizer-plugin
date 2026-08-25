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

package org.wurbelizer.idea.lang;

import com.intellij.lang.Language;

/**
 * The wurblet template language.
 * <p>
 * A wurblet source ({@code *.wrbl}) mixes two code levels: the <em>wurblet level</em>
 * ({@code @[ ... ]@}, the generator's own Java) and the <em>output level</em> (everything
 * else, the text being generated). {@code @( ... )@} splices a wurblet level value into the
 * output, {@code @{ ... }@} carries a wurbiler directive.
 */
public final class WurbletLanguage extends Language {

  /** The singleton instance. */
  public static final WurbletLanguage INSTANCE = new WurbletLanguage();

  private WurbletLanguage() {
    super("Wurblet");
  }

  @Override
  public String getDisplayName() {
    return "Wurblet";
  }
}
