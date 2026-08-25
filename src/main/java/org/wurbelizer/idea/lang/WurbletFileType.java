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

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** The file type of wurblet sources. */
public final class WurbletFileType extends LanguageFileType {

  /** The singleton instance. */
  public static final WurbletFileType INSTANCE = new WurbletFileType();

  /** The filename extension of wurblet sources, without the dot. */
  public static final String EXTENSION = "wrbl";

  private WurbletFileType() {
    super(WurbletLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "Wurblet";
  }

  @Override
  public @NotNull String getDescription() {
    return "Wurbelizer wurblet source";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return WurbletIcons.FILE;
  }
}
