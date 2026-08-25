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

package org.wurbelizer.idea.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The wurbiler directives.
 * <p>
 * The wurbiler lower-cases the command word before dispatching, so directive names are case
 * insensitive, and it aborts compilation on an unknown one.
 */
public enum WurbletDirectiveType {

  /** Includes another wurblet source. */
  INCLUDE("include", "<filename>", "Includes the given source file. May be nested; $-variables are replaced."),
  /** Sets the package of the generated wurblet class. */
  PACKAGE("package", "<packagename>", "Sets the package name of the wurblet."),
  /** Adds an import to the generated wurblet class. */
  IMPORT("import", "<importpath>", "Adds an import statement to the wurblet source."),
  /** Sets the parent class of the generated wurblet class. */
  EXTENDS("extends", "<parentclass>", "Sets the parent class the wurblet extends. Defaults to AbstractWurblet."),
  /** Adds interfaces to the generated wurblet class. */
  IMPLEMENTS("implements", "<interface>...", "Adds interfaces the wurblet implements. Multiple directives are concatenated."),
  /** Presets the wurblet arguments. */
  ARGS("args", "[<arg>]", "Presets the wurblet arguments. Without an argument the wurbler's arguments are used."),
  /** Switches the output stream. */
  TO("to", "[<outputstream>]", "Selects the stream the generated code is written to. Without an argument: out."),
  /** Sets the execution phase. */
  PHASE("phase", "<n>", "Sets the execution phase of the wurblet. Default is 1."),
  /** Sets the indentation of the generated Java code. */
  INDENT("indent", "<columns>|auto", "Sets the indentation of the generated Java source."),
  /** Adds text to the javadoc of the generated wurblet class. */
  COMMENT("comment", "<text>", "Adds the text to the wurblet javadoc comment. HTML; @ must be escaped as \\@."),
  /** Adds class level code to the generated wurblet class. */
  CODE("code", "<code>", "Adds code to the class level of the generated wurblet."),
  /** Sets the wurblet specific configuration. */
  CONFIG("config", "<text>", "Sets the wurblet configuration, available at runtime via getConfiguration().");

  private final String directiveName;
  private final String parameters;
  private final String description;

  WurbletDirectiveType(String directiveName, String parameters, String description) {
    this.directiveName = directiveName;
    this.parameters = parameters;
    this.description = description;
  }

  /**
   * Returns the directive name as written in the source.
   *
   * @return the name, never null
   */
  public @NotNull String getDirectiveName() {
    return directiveName;
  }

  /**
   * Returns the parameter description.
   *
   * @return the parameters, never null
   */
  public @NotNull String getParameters() {
    return parameters;
  }

  /**
   * Returns the description.
   *
   * @return the description, never null
   */
  public @NotNull String getDescription() {
    return description;
  }

  /**
   * Returns the directive for a command word.
   *
   * @param name the command word, case insensitive
   * @return the directive, null if unknown
   */
  public static @Nullable WurbletDirectiveType forName(@Nullable String name) {
    if (name == null) {
      return null;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    for (WurbletDirectiveType type : values()) {
      if (type.directiveName.equals(lower)) {
        return type;
      }
    }
    return null;
  }
}
