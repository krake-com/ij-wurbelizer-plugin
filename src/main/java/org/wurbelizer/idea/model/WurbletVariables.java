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

package org.wurbelizer.idea.model;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitution of {@code $}-variables in directive arguments.
 * <p>
 * Mirrors {@code org.wurbelizer.wurbel.WurbelHelper#translateVariables}: a variable is
 * written {@code $name} or <code>${name}</code>, an unknown variable is left alone and
 * {@code \$} suppresses the substitution.
 */
public final class WurbletVariables {

  /** The variable holding the directory of the wurblet source being compiled. */
  public static final String CURRENT_DIR = "currentDir";

  /** The variable holding the filename of the wurblet source being compiled. */
  public static final String CURRENT_FILE = "currentFile";

  private static final Pattern VARIABLE = Pattern.compile("(?<!\\\\)\\$(?:\\{([^}]+)}|([A-Za-z_][A-Za-z0-9_.-]*))");

  private WurbletVariables() {
  }

  /**
   * Substitutes the variables of a directive argument.
   *
   * @param text the raw argument
   * @param variables the known variables
   * @return the substituted text, never null
   */
  public static @NotNull String substitute(@NotNull String text, @NotNull Map<String, String> variables) {
    if (text.indexOf('$') < 0) {
      return text.replace("\\$", "$");
    }
    Matcher matcher = VARIABLE.matcher(text);
    StringBuilder buf = new StringBuilder();
    while (matcher.find()) {
      String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      String value = variables.get(key);
      matcher.appendReplacement(buf, Matcher.quoteReplacement(value != null ? value : matcher.group()));
    }
    matcher.appendTail(buf);
    return buf.toString().replace("\\$", "$");
  }

  /**
   * Resolves an include path.
   *
   * @param path the path, variables already substituted
   * @param baseDir the directory to resolve a relative path against
   * @return the file, null if it does not exist
   */
  public static @Nullable VirtualFile resolve(@NotNull String path, @Nullable VirtualFile baseDir) {
    String normalized = path.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    if (baseDir != null) {
      // resolve within the file system the wurblet source itself lives in
      VirtualFile file = baseDir.getFileSystem().findFileByPath(normalized);
      if (file != null) {
        return file;
      }
      file = baseDir.findFileByRelativePath(normalized);
      if (file != null) {
        return file;
      }
    }
    return LocalFileSystem.getInstance().findFileByPath(normalized);
  }
}
